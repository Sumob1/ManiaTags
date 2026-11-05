package com.tiertagger.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Http {
    private Http() {}

    public static final String USER_AGENT = "TierTagger/1.1 (Minecraft client mod)";

    /** GET a URL and parse JSON. Returns null on HTTP 404 (= "no such player"). */
    static JsonElement getJson(HttpClient http, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code == 404) return null;
        if (code != 200) throw new IOException("HTTP " + code + " from " + url);
        String body = resp.body();
        if (body == null || body.isBlank()) return null;
        return JsonParser.parseString(body);
    }

    /** GET raw bytes (images). Returns null on 404. */
    public static byte[] getBytes(HttpClient http, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == 404) return null;
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " from " + url);
        return resp.body();
    }

    static String str(JsonObject o, String field) {
        if (field == null || field.isBlank()) return "";
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    static int num(JsonObject o, String field) {
        if (field == null || field.isBlank()) return 0;
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive()) return 0;
        try {
            return (int) Math.round(e.getAsDouble());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
