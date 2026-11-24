package com.example.triplesweb.service;

import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 아카이브 카드와 상세 보기용 축소 JPEG를 원본 옆에 캐시한다.
 * 그리드가 수 MB 원본을 그대로 받지 않게 한다.
 */
@Service
public class UploadImageResizeService {

    public static final int CARD_WIDTH = 480;
    private static final Set<Integer> ALLOWED_WIDTHS = Set.of(360, CARD_WIDTH, 720, 1600);
    private static final String VARIANT_SUFFIX = ".card2-";
    private static final String LEGACY_VARIANT_SUFFIX = ".card-";
    private static final Pattern VARIANT_FILENAME = Pattern.compile(
            ".+\\.card(?:2)?-[0-9]+\\.jpg", Pattern.CASE_INSENSITIVE);
    private static final float JPEG_QUALITY = 0.82f;
    private static final Color CARD_BG = new Color(0x0d0e12);
    private final ConcurrentHashMap<Path, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();

    public static boolean isAllowedWidth(int width) {
        return ALLOWED_WIDTHS.contains(width);
    }

    public static boolean isVariantFilename(String filename) {
        return VARIANT_FILENAME.matcher(filename).matches();
    }

    static void deleteJpegVariants(Path original) throws IOException {
        if (!supportsVariant(original.getFileName().toString())) return;
        for (int width : ALLOWED_WIDTHS) {
            Files.deleteIfExists(variantPath(original, VARIANT_SUFFIX, width));
            Files.deleteIfExists(variantPath(original, LEGACY_VARIANT_SUFFIX, width));
        }
    }

    /**
     * @return 캐시된 축소 JPEG 경로. 만들 수 없으면 null.
     */
    public Path ensureJpegVariant(Path original, int maxWidth) throws IOException {
        if (original == null || !Files.isRegularFile(original) || !isAllowedWidth(maxWidth)) {
            return null;
        }
        if (original.getParent() == null || !supportsVariant(original.getFileName().toString())) {
            return null;
        }
        Path variant = variantPath(original, VARIANT_SUFFIX, maxWidth);
        if (isCurrent(original, variant)) {
            return variant;
        }

        Path key = variant.toAbsolutePath().normalize();
        CompletableFuture<Path> generation = new CompletableFuture<>();
        CompletableFuture<Path> existing = inFlight.putIfAbsent(key, generation);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof IOException io) throw io;
                throw ex;
            }
        }
        try {
            // 캐시 확인과 진행 중 작업 등록 사이에 생성이 끝났을 수 있다.
            Path result = isCurrent(original, variant) ? variant : createJpegVariant(original, variant, maxWidth);
            generation.complete(result);
            return result;
        } catch (IOException | RuntimeException | Error ex) {
            generation.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(key, generation);
        }
    }

    private static boolean supportsVariant(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jfif")
                || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp")
                || lower.endsWith(".webp");
    }

    private static Path variantPath(Path original, String suffix, int width) {
        return original.resolveSibling(stem(original.getFileName().toString()) + suffix + width + ".jpg");
    }

    private static boolean isCurrent(Path original, Path variant) throws IOException {
        return Files.isRegularFile(variant)
                && Files.getLastModifiedTime(variant).compareTo(Files.getLastModifiedTime(original)) >= 0
                && Files.size(variant) > 0;
    }

    Path createJpegVariant(Path original, Path variant, int maxWidth) throws IOException {
        BufferedImage scaled = readScaledImage(original, maxWidth);
        if (scaled == null) return null;
        try {
            Path temp = Files.createTempFile(variant.getParent(), variant.getFileName() + "-", ".tmp");
            try {
                writeJpeg(scaled, temp);
                try {
                    Files.move(temp, variant, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, variant, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } finally {
            scaled.flush();
        }
        return variant;
    }

    private static BufferedImage readScaledImage(Path original, int maxWidth) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(original.toFile())) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                Orientation orientation = readOrientation(reader, input);
                boolean swapAxes = orientation == Orientation.LEFT_TOP || orientation == Orientation.RIGHT_TOP
                        || orientation == Orientation.RIGHT_BOTTOM || orientation == Orientation.LEFT_BOTTOM;
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                int displayWidth = swapAxes ? sourceHeight : sourceWidth;
                int displayHeight = swapAxes ? sourceWidth : sourceHeight;
                // 점 샘플링 계단 현상을 줄이도록 목표 폭의 2배까지만 건너뛰고, 나머지는 보간 축소한다.
                int subsampling = displayWidth / (maxWidth * 2);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling >= 2) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage source = reader.read(0, param);
                try {
                    int width = Math.min(maxWidth, displayWidth);
                    int height = Math.max(1, (int) Math.round(displayHeight * (width / (double) displayWidth)));
                    BufferedImage scaled = scaleToSize(source, swapAxes ? height : width, swapAxes ? width : height);
                    if (orientation == null || orientation == Orientation.TOP_LEFT) return scaled;
                    try {
                        return ExifFilterUtils.getFilterForOrientation(orientation).apply(scaled);
                    } finally {
                        scaled.flush();
                    }
                } finally {
                    source.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static Orientation readOrientation(ImageReader reader, ImageInputStream input) {
        try {
            if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) return null;
            Orientation orientation = ExifUtils.getExifOrientation(reader, 0);
            if (orientation != null) return orientation;
        } catch (IOException | RuntimeException ignored) {
            // EXIF 없이도 디코딩 가능한 JPEG는 계속 처리한다.
        }
        // JDK JPEG 리더가 SOI 직후의 APP1을 메타데이터에서 누락하는 경우를 처리한다.
        try {
            long position = input.getStreamPosition();
            try {
                input.seek(0);
                if (input.readUnsignedShort() != 0xffd8 || input.readUnsignedShort() != 0xffe1) return null;
                int size = input.readUnsignedShort() - 2;
                if (size < 6) return null;
                byte[] payload = new byte[size];
                input.readFully(payload);
                return ExifUtils.getOrientationFromExif(payload);
            } finally {
                input.seek(position);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static BufferedImage scaleToSize(BufferedImage source, int width, int height) {
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
