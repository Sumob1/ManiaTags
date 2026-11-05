package com.tiertagger.gui;

import com.tiertagger.TierTaggerMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads / loads images in the background and turns them into GUI textures. */
final class ImageCache {
    private ImageCache() {}

    record Img(Identifier id, int w, int h) {}

    @FunctionalInterface
    interface Loader {
        /** Return the raw image bytes (PNG/JPG/GIF), or null if there is none. */
        byte[] load() throws Exception;
    }

    private static final Map<String, Img> DONE = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "TierTagger-Images");
        t.setDaemon(true);
        return t;
    });

    /** Returns the texture if it is ready, otherwise null (and starts loading it). */
    static Img get(String key, Loader loader) {
        Img img = DONE.get(key);
        if (img != null) return img;
        if (FAILED.contains(key) || !BUSY.add(key)) return null;
        POOL.execute(() -> {
            try {
                byte[] data = loader.load();
                if (data == null) throw new IOException("no image");
                NativeImage image = decode(data);
                int w = image.getWidth();
                int h = image.getHeight();
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    Identifier id = Identifier.of("tiertagger", "dyn/" + sanitize(key));
                    mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "tiertagger:" + key, image));
                    DONE.put(key, new Img(id, w, h));
                    BUSY.remove(key);
                });
            } catch (Exception e) {
                TierTaggerMod.LOGGER.debug("Image {} failed: {}", key, e.toString());
                FAILED.add(key);
                BUSY.remove(key);
            }
        });
        return null;
    }

    static void forgetFailures() {
        FAILED.clear();
    }

    private static NativeImage decode(byte[] data) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(data));
        } catch (Exception pngFailed) {
            // JPG / GIF etc: convert to PNG first
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) throw new IOException("unsupported image format");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            return NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
        }
    }

    private static String sanitize(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
