package com.example.triplesweb.service;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 아카이브 카드용 축소 JPEG를 원본 옆에 캐시한다.
 * 그리드가 수 MB 원본을 그대로 받지 않게 한다.
 */
@Service
public class UploadImageResizeService {

    public static final int CARD_WIDTH = 480;
    private static final Set<Integer> ALLOWED_WIDTHS = Set.of(360, CARD_WIDTH, 720);
    private static final float JPEG_QUALITY = 0.82f;
    private static final Color CARD_BG = new Color(0x0d0e12);

    public static boolean isAllowedWidth(int width) {
        return ALLOWED_WIDTHS.contains(width);
    }

    /**
     * @return 캐시된 카드 JPEG 경로. 만들 수 없으면 null.
     */
    public Path ensureJpegVariant(Path original, int maxWidth) throws IOException {
        if (original == null || !Files.isRegularFile(original) || !isAllowedWidth(maxWidth)) {
            return null;
        }
        String name = original.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jfif")
                || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp")
                || lower.endsWith(".webp"))) {
            return null;
        }
        Path parent = original.getParent();
        if (parent == null) return null;
        String stem = stem(name);
        Path variant = parent.resolve(stem + ".card-" + maxWidth + ".jpg");
        if (Files.isRegularFile(variant)
                && Files.getLastModifiedTime(variant).compareTo(Files.getLastModifiedTime(original)) >= 0
                && Files.size(variant) > 0) {
            return variant;
        }

        BufferedImage source = ImageIO.read(original.toFile());
        if (source == null) return null;
        BufferedImage scaled = scaleToWidth(source, maxWidth);
        Path temp = parent.resolve(stem + ".card-" + maxWidth + ".jpg.tmp");
        try {
            writeJpeg(scaled, temp);
            try {
                Files.move(temp, variant, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveEx) {
                Files.move(temp, variant, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw ex;
        } finally {
            source.flush();
            scaled.flush();
        }
        return Files.isRegularFile(variant) ? variant : null;
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static BufferedImage scaleToWidth(BufferedImage source, int maxWidth) {
        int srcW = Math.max(1, source.getWidth());
        int srcH = Math.max(1, source.getHeight());
        int width = Math.min(maxWidth, srcW);
        int height = Math.max(1, (int) Math.round(srcH * (width / (double) srcW)));
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(CARD_BG);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer unavailable");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target);
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
