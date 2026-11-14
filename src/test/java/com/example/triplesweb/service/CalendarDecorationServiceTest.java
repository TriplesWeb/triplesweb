package com.example.triplesweb.service;

import com.example.triplesweb.entity.CalendarDecoration;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.CalendarDecorationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarDecorationServiceTest {
    private final CalendarDecorationRepository repository = mock(CalendarDecorationRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final CalendarDecorationService service = new CalendarDecorationService(repository, storage);

    @Test
    void rejectsAnUploadWhoseBytesAreNotPngJpgOrWebp() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "sticker.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.create(
                "유닛 데뷔", "UNIT", LocalDate.of(2026, 11, 14), "AAA", "", "#00f5d4", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일 내용");
        verify(repository, never()).save(any());
    }

    @Test
    void storesAPngWhoseHeaderMatches() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0};
        MockMultipartFile file = new MockMultipartFile("image", "sticker.png", "image/png", png);
        when(storage.saveWithMeta(file, "calendar")).thenReturn(SavedMeta.builder()
                .originalName("sticker.png")
                .savedName("1.png")
                .url("/uploads/images/calendar/1.png")
                .size(png.length)
                .fileType("image/png")
                .mediaType(MediaType.IMAGE)
                .build());
        when(repository.save(any(CalendarDecoration.class))).thenAnswer(invocation -> {
            CalendarDecoration saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        service.create("유닛 데뷔", "UNIT", LocalDate.of(2026, 11, 14), "AAA", "", "#00f5d4", file);

        verify(storage).saveWithMeta(file, "calendar");
        verify(repository).save(any(CalendarDecoration.class));
    }
}
