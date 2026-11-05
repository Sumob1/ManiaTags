package com.tiertagger.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tiertagger.Config;
import com.tiertagger.TierTaggerMod;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking, cached lookups. {@link #get} returns immediately (state LOADING the first time)
 * and fetches in the background; {@link #version()} changes whenever new data arrives so that
 * the nametag cache knows to rebuild.
 */
public final class TierService {
    private TierService() {}

    public enum State { LOADING, READY, NONE, ERROR }

    public static final class Result {
        public final State state;
        public final Profile profile;
        final long time;

        Result(State state, Profile profile) {
            this.state = state;
            this.profile = profile;
            this.time = System.currentTimeMillis();
        }
    }

    private static final Result LOADING = new Result(State.LOADING, Profile.EMPTY);

    private static final AtomicInteger VERSION = new AtomicInteger();
    private static final ConcurrentHashMap<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> INFLIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> NAME_FAILED = new ConcurrentHashMap<>();

    public static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "TierTagger-IO");
        t.setDaemon(true);
        return t;
    });

    /** Forget one player so the next lookup hits the websites again. */
    public static void forget(UUID uuid) {
        CACHE.keySet().removeIf(k -> k.endsWith(":" + uuid));
        bump();
    }

    public static int version() {
        return VERSION.get();
    }

    /** Call after a settings change so cached tags rebuild. */
    public static void bump() {
        VERSION.incrementAndGet();
    }

    public static void clearCache() {
        CACHE.clear();
        NAME_TO_UUID.clear();
        NAME_FAILED.clear();
        bump();
    }

    public static Result get(TierProvider provider, UUID uuid, String name) {
        UUID real = uuid;
        if (uuid.version() != 4) {
            // Offline-mode / cracked UUID: resolve the real one from the name.
            real = NAME_TO_UUID.get(name.toLowerCase(Locale.ROOT));
            if (real == null) {
                resolveName(name);
                return LOADING;
            }
        }

        String key = provider.id() + ":" + real;
        Result r = CACHE.get(key);
        long now = System.currentTimeMillis();
        long ttl = r == null ? 0
            : r.state == State.ERROR ? 60_000L
            : Math.max(1, Config.get().cacheMinutes) * 60_000L;

        if (r == null || now - r.time > ttl) {
            if (INFLIGHT.putIfAbsent(key, Boolean.TRUE) == null) {
                final UUID target = real;
                POOL.execute(() -> fetch(provider, target, name, key));
            }
        }
        return r == null ? LOADING : r;
    }

    private static void fetch(TierProvider provider, UUID uuid, String name, String key) {
        Result result;
        try {
            Profile p = provider.fetch(HTTP, uuid, name);
            result = new Result(p.modes().isEmpty() ? State.NONE : State.READY, p);
        } catch (Exception e) {
            TierTaggerMod.LOGGER.debug("{} lookup failed for {}: {}", provider.name(), name, e.toString());
            result = new Result(State.ERROR, Profile.EMPTY);
        }
        CACHE.put(key, result);
        INFLIGHT.remove(key);
        bump();
    }

    // ------------------------------------------------------------------ name -> uuid

    /** Resolves a player name to its real UUID (cached). Completes with null if there is no such player. */
    public static CompletableFuture<UUID> uuidOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        UUID cached = NAME_TO_UUID.get(lower);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID id = lookupMojang(name);
                if (id == null) id = lookupPlayerDb(name);
                if (id != null) NAME_TO_UUID.put(lower, id);
                return id;
            } catch (Exception e) {
                return null;
            }
        }, POOL);
    }

    private static void resolveName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        Long failedAt = NAME_FAILED.get(lower);
        if (failedAt != null && System.currentTimeMillis() - failedAt < 120_000L) return;
        if (INFLIGHT.putIfAbsent("name:" + lower, Boolean.TRUE) != null) return;
        uuidOf(name).whenComplete((id, err) -> {
            if (id == null) NAME_FAILED.put(lower, System.currentTimeMillis());
            INFLIGHT.remove("name:" + lower);
            bump();
        });
    }

    private static UUID lookupMojang(String name) throws Exception {
        JsonElement el = Http.getJson(HTTP, "https://api.mojang.com/users/profiles/minecraft/" + name);
        if (el == null || !el.isJsonObject() || !el.getAsJsonObject().has("id")) return null;
        return fromUndashed(el.getAsJsonObject().get("id").getAsString());
    }

    private static UUID lookupPlayerDb(String name) throws Exception {
        JsonElement el = Http.getJson(HTTP, "https://playerdb.co/api/player/minecraft/" + name);
        if (el == null || !el.isJsonObject()) return null;
        JsonElement data = el.getAsJsonObject().get("data");
        if (data == null || !data.isJsonObject()) return null;
        JsonElement player = data.getAsJsonObject().get("player");
        if (player == null || !player.isJsonObject()) return null;
        JsonObject p = player.getAsJsonObject();
        if (p.has("raw_id")) return fromUndashed(p.get("raw_id").getAsString());
        if (p.has("id")) return UUID.fromString(p.get("id").getAsString());
        return null;
    }

    private static UUID fromUndashed(String s) {
        if (s == null || s.length() != 32) return null;
        return UUID.fromString(s.replaceFirst(
            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }
}
