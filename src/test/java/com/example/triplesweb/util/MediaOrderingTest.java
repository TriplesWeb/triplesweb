package com.example.triplesweb.util;

import com.example.triplesweb.dto.MediaDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaOrderingTest {
    @Test
    void sortsByUploadDateThenRegistrationWhenDateIsMissing() {
        List<MediaDto> videos = new ArrayList<>(List.of(
                video(1L, "2024-01-01", 10, "10:00"),
                video(3L, null, 10, "10:00"),
                video(2L, "2026-02-06", 10, "10:00"),
                video(4L, null, 10, "10:00")
        ));

        MediaOrdering.sort(videos, "latest");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(2L, 1L, 4L, 3L);

        MediaOrdering.sort(videos, "oldest");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void sortsByViewsAndDuration() {
        List<MediaDto> videos = new ArrayList<>(List.of(
                video(1L, "2024-01-01", 100, "3:11"),
                video(2L, "2024-01-01", 5000, "1:02:03"),
                video(3L, "2024-01-01", 5000, "0:40"),
                video(4L, "2024-01-01", 7, null)
        ));

        MediaOrdering.sort(videos, "views");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(3L, 2L, 1L, 4L);

        MediaOrdering.sort(videos, "short");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(3L, 1L, 2L, 4L);

        MediaOrdering.sort(videos, "long");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(2L, 1L, 3L, 4L);
    }

    @Test
    void sortsByAdminRegistrationId() {
        List<MediaDto> videos = new ArrayList<>(List.of(
                video(1L, "2026-02-06", 10, "10:00"),
                video(4L, "2024-01-01", 10, "10:00"),
                video(2L, "2025-06-01", 10, "10:00")
        ));
        MediaOrdering.sort(videos, "registered");
        assertThat(videos).extracting(MediaDto::getId).containsExactly(4L, 2L, 1L);
    }

    private static MediaDto video(long id, String publishedOn, long views, String duration) {
        return MediaDto.builder()
                .id(id)
                .tab("mv")
                .youtubeId("video" + id)
                .title("영상")
                .publishedOn(publishedOn)
                .views(views)
                .duration(duration)
                .build();
    }
}
