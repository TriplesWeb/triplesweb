package com.example.triplesweb.service;

import com.example.triplesweb.dto.AlbumAdminDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AlbumAdminLyricsTest {

    @Autowired AlbumAdminService albumAdminService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void preservesLyricsWhenResavingEditDtoAndValidatesTheLimit() throws Exception {
        String lyrics = "ALL: 첫 줄\n\n김채연: 두 번째 줄";
        String tracksJson = objectMapper.writeValueAsString(List.of(
                AlbumAdminDto.TrackItem.builder().title("첫 곡").lyrics(lyrics).build(),
                AlbumAdminDto.TrackItem.builder().title("두 번째 곡").lyrics(" \n\t").build()));
        long albumId = albumAdminService.create("가사 편집 테스트", "2026-11-24", "", "SUBUNIT",
                null, null, null, null, List.of(), tracksJson, null, null).getAlbumId();
        try {
            var tracks = albumAdminService.get(albumId).getTracks();
            assertThat(tracks).extracting(AlbumAdminDto.TrackItem::getLyrics).containsExactly(lyrics, null);

            update(albumId, objectMapper.writeValueAsString(tracks));
            assertThat(albumAdminService.get(albumId).getTracks())
                    .extracting(AlbumAdminDto.TrackItem::getLyrics).containsExactly(lyrics, null);

            tracks.get(0).setLyrics("가".repeat(20_000));
            update(albumId, objectMapper.writeValueAsString(tracks));
            tracks.get(0).setLyrics("가".repeat(20_001));
            String oversized = objectMapper.writeValueAsString(tracks);
            assertThatThrownBy(() -> update(albumId, oversized))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20,000");
            assertThat(albumAdminService.get(albumId).getTracks().get(0).getLyrics()).hasSize(20_000);

            tracks.get(0).setLyrics("");
            update(albumId, objectMapper.writeValueAsString(tracks));
            assertThat(albumAdminService.get(albumId).getTracks().get(0).getLyrics()).isNull();
        } finally {
            albumAdminService.delete(albumId);
        }
    }

    private void update(long albumId, String tracksJson) {
        albumAdminService.update(albumId, "가사 편집 테스트", "2026-11-24", "", "SUBUNIT",
                null, null, null, null, List.of(), tracksJson, null, null, false);
    }
}
