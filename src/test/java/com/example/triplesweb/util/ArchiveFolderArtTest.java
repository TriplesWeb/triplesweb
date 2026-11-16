package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFolderArtTest {
    @TempDir Path uploads;

    @Test void emptyDirectoryYieldsNoArt() {
        ArchiveFolderArt art = new ArchiveFolderArt(uploads.toString());
        assertThat(art.urlFor(ArchiveFolderClassifier.Kind.SOLO, "s1", "S1 윤서연")).isEmpty();
    }

    @Test void prefersExactThenPrefixedKoreanFileName() throws Exception {
        Path dir = uploads.resolve(ArchiveFolderArt.DIR_NAME);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("S1 윤서연.png"), "folder");
        Files.writeString(dir.resolve("2인.webp"), "combo");
        Files.writeString(dir.resolve("Glow.jpg"), "unit");
        ArchiveFolderArt art = new ArchiveFolderArt(uploads.toString());
        assertThat(art.urlFor(ArchiveFolderClassifier.Kind.SOLO, "s1", "S1 윤서연"))
                .isEqualTo("/uploads/%EB%AF%B8%EB%94%94%EC%96%B4%20%ED%8F%B4%EB%8D%94/S1%20%EC%9C%A4%EC%84%9C%EC%97%B0.png");
        assertThat(art.urlFor(ArchiveFolderClassifier.Kind.COMBO, "combo-2", "2명 케미"))
                .isEqualTo("/uploads/%EB%AF%B8%EB%94%94%EC%96%B4%20%ED%8F%B4%EB%8D%94/2%EC%9D%B8.webp");
        assertThat(art.urlFor(ArchiveFolderClassifier.Kind.UNIT, "unit-3", "Glow"))
                .isEqualTo("/uploads/%EB%AF%B8%EB%94%94%EC%96%B4%20%ED%8F%B4%EB%8D%94/Glow.jpg");
    }
}
