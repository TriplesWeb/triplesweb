package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UploadImageResizeServiceTest {
    @TempDir Path temp;

    @Test void createsCachedJpegVariantSmallerThanSource() throws Exception {
        Path source = temp.resolve("images/2026-11-25/big.png");
        Files.createDirectories(source.getParent());
        BufferedImage image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, 1600, 1200);
        g.dispose();
        ImageIO.write(image, "png", source.toFile());

        UploadImageResizeService service = new UploadImageResizeService(temp.toString());
        Path variant = service.ensureJpegVariant(source, UploadImageResizeService.CARD_WIDTH);

        assertThat(variant).isEqualTo(temp.resolve("thumbs/images/2026-11-25/big.card2-480.jpg"));
        assertThat(source.resolveSibling("big.card2-480.jpg")).doesNotExist();
        assertThat(Files.size(variant)).isLessThan(Files.size(source));
        BufferedImage out = ImageIO.read(variant.toFile());
        assertThat(out.getWidth()).isEqualTo(480);
        assertThat(service.ensureJpegVariant(source, UploadImageResizeService.CARD_WIDTH)).isEqualTo(variant);
    }

    @Test void rejectsSourcesOutsideUploadRootOrInsideThumbs() throws Exception {
        Path uploads = Files.createDirectories(temp.resolve("uploads"));
        Path thumbs = Files.createDirectories(uploads.resolve("thumbs"));
        Path outside = temp.resolve("outside.png");
        Path cached = thumbs.resolve("cached.png");
        var image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", outside.toFile());
        ImageIO.write(image, "png", cached.toFile());
        Path outsideLink = Files.createSymbolicLink(uploads.resolve("outside.png"), outside);
        Path cachedLink = Files.createSymbolicLink(uploads.resolve("cached.png"), cached);
        var service = new UploadImageResizeService(uploads.toString());

        for (Path source : new Path[]{outside, cached, outsideLink, cachedLink}) {
            assertThat(service.ensureJpegVariant(source, 480)).isNull();
        }
        try (var files = Files.list(thumbs)) {
            assertThat(files.toList()).containsExactly(cached);
        }
    }

    @Test void rejectsMirroredDirectoriesThatResolveOutsideTheUploadRoot() throws Exception {
        Path uploads = Files.createDirectories(temp.resolve("uploads"));
        Path sourceDirectory = Files.createDirectories(uploads.resolve("images"));
        Path outside = Files.createDirectories(temp.resolve("elsewhere"));
        Files.createSymbolicLink(uploads.resolve("thumbs"), outside);
        Path source = sourceDirectory.resolve("photo.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", source.toFile());

        assertThat(new UploadImageResizeService(uploads.toString()).ensureJpegVariant(source, 480)).isNull();
        assertThat(outside.resolve("images")).doesNotExist();
    }

    @Test void variantIsReadableByOtherAccounts() throws Exception {
        Path source = temp.resolve("perm.png");
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", source.toFile());

        Path variant = new UploadImageResizeService(temp.toString()).ensureJpegVariant(source, 1600);

        assertThat(variant).isNotNull();
        if (Files.getFileAttributeView(variant, PosixFileAttributeView.class) != null) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(variant))).isEqualTo("rw-r--r--");
        }
    }

    @ParameterizedTest
    @CsvSource({"6401, 1600, 150", "800, 800, 600"})
    void generatesDetailVariantWithoutUpscaling(int originalWidth, int expectedWidth, int expectedHeight) throws Exception {
        Path source = temp.resolve("detail.png");
        ImageIO.write(new BufferedImage(originalWidth, 600, BufferedImage.TYPE_INT_RGB), "png", source.toFile());

        Path variant = new UploadImageResizeService(temp.toString()).ensureJpegVariant(source, 1600);
        assertThat(variant).isEqualTo(temp.resolve("thumbs/detail.card2-1600.jpg"));
        BufferedImage output = ImageIO.read(variant.toFile());
        assertThat(output.getWidth()).isEqualTo(expectedWidth);
        assertThat(output.getHeight()).isEqualTo(expectedHeight);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rotatesExifJpegAndResizesByDisplayedWidth(boolean app1First) throws Exception {
        Path source = temp.resolve("oriented.jpg");
        BufferedImage input = new BufferedImage(1200, 2400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = input.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 600, 2400);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(600, 0, 600, 2400);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(input, "jpg", source.toFile());
        input.flush();

        byte[] jpeg = Files.readAllBytes(source);
        // APP1 Exif, big-endian TIFF, one IFD entry (Orientation = 6, rotate clockwise).
        byte[] app1 = {
                (byte) 0xff, (byte) 0xe1, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1,
                1, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0
        };
        int offset = app1First ? 2 : 4 + Short.toUnsignedInt(ByteBuffer.wrap(jpeg).getShort(4));
        Files.write(source, ByteBuffer.allocate(jpeg.length + app1.length)
                .put(jpeg, 0, offset).put(app1).put(jpeg, offset, jpeg.length - offset).array());

        var service = new UploadImageResizeService(temp.toString());
        for (int width : new int[]{480, 1600}) {
            Files.write(temp.resolve("oriented.card-" + width + ".jpg"), jpeg);
            Files.write(temp.resolve("oriented.card2-" + width + ".jpg"), jpeg);
            Path variant = service.ensureJpegVariant(source, width);
            assertThat(variant).isEqualTo(temp.resolve("thumbs/oriented.card2-" + width + ".jpg"));
            BufferedImage output = ImageIO.read(variant.toFile());
            assertThat(output.getWidth()).isEqualTo(width);
            assertThat(output.getHeight()).isEqualTo(width / 2);
            assertThat(new Color(output.getRGB(width / 2, output.getHeight() / 4)).getRed()).isGreaterThan(200);
            assertThat(new Color(output.getRGB(width / 2, output.getHeight() * 3 / 4)).getBlue()).isGreaterThan(200);
            output.flush();
        }
    }

    @Test void concurrentRequestsGenerateTheSameDetailVariantOnce() throws Exception {
        Path source = temp.resolve("concurrent.png");
        Path variant = temp.resolve("thumbs/concurrent.card2-1600.jpg");
        ImageIO.write(new BufferedImage(3200, 1600, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        var service = spy(new UploadImageResizeService(temp.toString()));
        int requestCount = 8;
        var start = new CyclicBarrier(requestCount);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            var requests = new ArrayList<Future<Path>>();
            for (int i = 0; i < requestCount; i++) {
                requests.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return service.ensureJpegVariant(source, 1600);
                }));
            }
            for (Future<Path> request : requests) {
                assertThat(request.get(10, TimeUnit.SECONDS)).isEqualTo(variant);
            }
            verify(service, times(1)).createJpegVariant(source, variant, 1600);
            assertThat(ImageIO.read(variant.toFile()).getWidth()).isEqualTo(1600);
        } finally {
            executor.shutdownNow();
        }
    }
}
