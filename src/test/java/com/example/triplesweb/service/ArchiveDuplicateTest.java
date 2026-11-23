package com.example.triplesweb.service;

import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.repository.ArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveDuplicateTest {

    @Mock ArchiveRepository archiveRepository;
    @Mock FileStorageService fileStorageService;
    @Mock ObjectMapper objectMapper;
    @Mock EntityManager entityManager;
    @InjectMocks ArchiveService archiveService;

    @Test
    void uploadImagesRejectsSameDateSourceDescription() {
        LocalDate day = LocalDate.of(2023, 4, 24);
        when(archiveRepository.existsImageDuplicate(MediaType.IMAGE, day, "EXTERNAL", "Vanity Teen Magazine", "윤서연"))
                .thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                archiveService.uploadImages(java.util.List.of(file), "", "윤서연", day, "EXTERNAL", "Vanity Teen Magazine"));

        assertTrue(error.getMessage().contains("같은 멤버·날짜·출처·설명"));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void uploadImagesRejectsSameDateSourceWithEmptyDescription() {
        LocalDate day = LocalDate.of(2023, 4, 24);
        when(archiveRepository.existsImageDuplicate(eq(MediaType.IMAGE), eq(day), eq("COSMO_ROOM"), eq(""), eq("윤서연")))
                .thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                archiveService.uploadImages(java.util.List.of(file), "", "윤서연", day, "COSMO_ROOM", "   "));

        assertTrue(error.getMessage().contains("설명을 비운"));
        verifyNoInteractions(fileStorageService);
    }
}
