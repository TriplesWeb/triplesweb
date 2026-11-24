package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path source = temp.resolve("big.png");
        BufferedImage image = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, 1600, 1200);
        g.dispose();
        ImageIO.write(image, "png", source.toFile());

        UploadImageResizeService service = new UploadImageResizeService();
        Path variant = service.ensureJpegVariant(source, UploadImageResizeService.CARD_WIDTH);

        assertThat(variant).isNotNull();
        assertThat(Files.size(variant)).isLessThan(Files.size(source));
        assertThat(variant.getFileName().toString()).endsWith(".card-480.jpg");
        BufferedImage out = ImageIO.read(variant.toFile());
        assertThat(out.getWidth()).isEqualTo(480);
        assertThat(service.ensureJpegVariant(source, UploadImageResizeService.CARD_WIDTH)).isEqualTo(variant);
    }

    @ParameterizedTest
    @CsvSource({"6401, 1600, 150", "800, 800, 600"})
    void generatesDetailVariantWithoutUpscaling(int originalWidth, int expectedWidth, int expectedHeight) throws Exception {
        Path source = temp.resolve("detail.png");
        ImageIO.write(new BufferedImage(originalWidth, 600, BufferedImage.TYPE_INT_RGB), "png", source.toFile());

        Path variant = new UploadImageResizeService().ensureJpegVariant(source, 1600);
        assertThat(variant).isEqualTo(temp.resolve("detail.card-1600.jpg"));
        BufferedImage output = ImageIO.read(variant.toFile());
        assertThat(output.getWidth()).isEqualTo(expectedWidth);
        assertThat(output.getHeight()).isEqualTo(expectedHeight);
    }

    @Test void concurrentRequestsGenerateTheSameDetailVariantOnce() throws Exception {
        Path source = temp.resolve("concurrent.png");
        Path variant = temp.resolve("concurrent.card-1600.jpg");
        ImageIO.write(new BufferedImage(3200, 1600, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        var service = spy(new UploadImageResizeService());
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
