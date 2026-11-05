package com.tiertagger.gui;

import com.tiertagger.Config;
import com.tiertagger.TierTaggerMod;
import com.tiertagger.api.Http;
import com.tiertagger.api.TierService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Custom window background: png/jpg picture or animated gif, from a file or a link. */
final class Backgrounds {
    private Backgrounds() {}

    private record Frame(Identifier id, int delay) {}

    private static final class Anim {
        final List<Frame> frames = new ArrayList<>();
        int w, h, total;
    }

    private static volatile Anim current;
    private static String currentKey = "";
    private static volatile String loadingKey = "";
    private static String failedKey = "";
    private static int counter;
    private static final int MAX_W = 640, MAX_FRAMES = 90;

    static boolean active() {
        Config c = Config.get();
        return !"OFF".equals(c.bgMode) && c.bgSource != null && !c.bgSource.isBlank();
    }

    private static String key() {
        Config c = Config.get();
        return c.bgMode + "|" + c.bgSource.trim();
    }

    /** Draws the background (cover-fit) and the dimming layer. Call before drawing the UI on top. */
    static void draw(DrawContext c, int x, int y, int w, int h) {
        if (!active()) return;
        String k = key();
        if (!k.equals(currentKey)) {
            if (!k.equals(loadingKey) && !k.equals(failedKey)) startLoad(k);
        }
        Anim a = current;
        if (a == null || a.frames.isEmpty()) return;
        Frame f = a.frames.get(0);
        if (a.frames.size() > 1 && a.total > 0) {
            long t = System.currentTimeMillis() % a.total;
            for (Frame fr : a.frames) {
                if (t < fr.delay()) { f = fr; break; }
                t -= fr.delay();
            }
        }
        float s = Math.max(w / (float) a.w, h / (float) a.h);
        int r = Math.min(Ui.bubble() ? 16 : 7, Math.min(w, h) / 2);
        float u0 = (a.w - w / s) / 2f, v0 = (a.h - h / s) / 2f;
        int mid = h - 2 * r;
        if (mid > 0) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, f.id(), x, y + r, u0, v0 + r / s, w, mid,
                Math.max(1, Math.round(w / s)), Math.max(1, Math.round(mid / s)), a.w, a.h);
        }
        int regH = Math.max(1, Math.round(1f / s));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            int sw = w - 2 * inset;
            if (sw <= 0) continue;
            int regW = Math.max(1, Math.round(sw / s));
            c.drawTexture(RenderPipelines.GUI_TEXTURED, f.id(), x + inset, y + i, u0 + inset / s, v0 + i / s, sw, 1, regW, regH, a.w, a.h);
            c.drawTexture(RenderPipelines.GUI_TEXTURED, f.id(), x + inset, y + h - 1 - i, u0 + inset / s, v0 + (h - 1 - i) / s, sw, 1, regW, regH, a.w, a.h);
        }
        int dim = Math.round(255 * Math.max(0, Math.min(90, Config.get().bgDim)) / 100f * Ui.alpha);
        Ui.rrectRaw(c, x, y, w, h, r, dim << 24);
    }

    private static void startLoad(String k) {
        loadingKey = k;
        Config cfg = Config.get();
        String mode = cfg.bgMode, src = cfg.bgSource.trim();
        Thread t = new Thread(() -> {
            try {
                byte[] data;
                if ("URL".equals(mode)) {
                    data = Http.getBytes(TierService.HTTP, src);
                } else {
                    Path p = Path.of(src);
                    if (!p.isAbsolute()) p = Config.dir().resolve("backgrounds").resolve(src);
                    data = Files.readAllBytes(p);
                }
                List<BufferedImage> imgs = new ArrayList<>();
                List<Integer> delays = new ArrayList<>();
                decode(data, imgs, delays);
                if (imgs.isEmpty()) throw new Exception("no frames");
                List<NativeImage> natives = new ArrayList<>();
                for (BufferedImage b : imgs) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(b, "png", out);
                    natives.add(NativeImage.read(new ByteArrayInputStream(out.toByteArray())));
                }
                int w = natives.get(0).getWidth(), h = natives.get(0).getHeight();
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    Anim old = current;
                    Anim a = new Anim();
                    a.w = w;
                    a.h = h;
                    int gen = ++counter;
                    for (int i = 0; i < natives.size(); i++) {
                        Identifier id = Identifier.of("tiertagger", "dyn/bg/" + gen + "_" + i);
                        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "tiertagger:bg", natives.get(i)));
                        a.frames.add(new Frame(id, delays.get(i)));
                        a.total += delays.get(i);
                    }
                    current = a;
                    currentKey = k;
                    loadingKey = "";
                    if (old != null) for (Frame fr : old.frames) mc.getTextureManager().destroyTexture(fr.id());
                });
            } catch (Throwable e) {
                TierTaggerMod.LOGGER.warn("Background failed: {}", e.toString());
                failedKey = k;
                loadingKey = "";
            }
        }, "TierTagger-Background");
        t.setDaemon(true);
        t.start();
    }

    private static BufferedImage scale(BufferedImage src) {
        if (src.getWidth() <= MAX_W) return src;
        int w = MAX_W, h = Math.max(1, src.getHeight() * MAX_W / src.getWidth());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void decode(byte[] data, List<BufferedImage> imgs, List<Integer> delays) throws Exception {
        boolean gif = data.length > 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
        if (!gif) {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) throw new Exception("unsupported image");
            imgs.add(scale(bi));
            delays.add(1000);
            return;
        }
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
        ImageReader reader = it.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, false);
            int n = Math.min(reader.getNumImages(true), MAX_FRAMES);
            BufferedImage canvas = null;
            for (int i = 0; i < n; i++) {
                BufferedImage fr = reader.read(i);
                if (canvas == null) canvas = new BufferedImage(fr.getWidth(), fr.getHeight(), BufferedImage.TYPE_INT_ARGB);
                int left = 0, top = 0, delay = 10;
                String disposal = "none";
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                for (int k = 0; k < root.getLength(); k++) {
                    IIOMetadataNode node = (IIOMetadataNode) root.item(k);
                    if (node.getNodeName().equals("ImageDescriptor")) {
                        left = Integer.parseInt(node.getAttribute("imageLeftPosition"));
                        top = Integer.parseInt(node.getAttribute("imageTopPosition"));
                    } else if (node.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(node.getAttribute("delayTime"));
                        disposal = node.getAttribute("disposalMethod");
                    }
                }
                Graphics2D g = canvas.createGraphics();
                g.drawImage(fr, left, top, null);
                g.dispose();
                BufferedImage snap = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D sg = snap.createGraphics();
                sg.drawImage(canvas, 0, 0, null);
                sg.dispose();
                imgs.add(scale(snap));
                delays.add(Math.max(40, delay * 10));
                if (disposal.equals("restoreToBackgroundColor")) {
                    Graphics2D cg = canvas.createGraphics();
                    cg.setComposite(java.awt.AlphaComposite.Clear);
                    cg.fillRect(left, top, fr.getWidth(), fr.getHeight());
                    cg.dispose();
                }
            }
        } finally {
            reader.dispose();
        }
    }
}
