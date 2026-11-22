package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
}
