package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
class ArchivePostArtistsTest {
    @Autowired ArchiveRepository archives;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test void uploadStoresPickerArtistsOnly() throws Exception {
        LocalDate day = LocalDate.of(2026, 7, 1);
        ArchiveService service = service();
        MockMultipartFile first = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile second = new MockMultipartFile("file", "b.jpg", "image/jpeg", new byte[]{2});

        Archive saved = service.uploadImages(List.of(first, second), "", "윤서연", day, "COSMO_ROOM", "유닛 다음 멤버", 0);

        entityManager.flush();
        entityManager.clear();
        Archive reloaded = archives.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getArtistNames()).isEqualTo("윤서연");
        assertThat(reloaded.getImageArtistsJson()).isNull();
        assertThat(reloaded.getImagesJson()).isEqualTo("[\"/uploads/1.jpg\",\"/uploads/2.jpg\"]");
    }

    @Test void updateClearsStoredPerImageArtists() throws Exception {
        LocalDate day = LocalDate.of(2026, 7, 1);
        Archive existing = new Archive("", meta("/uploads/a.jpg"));
        existing.setArtistNames("윤서연,정혜린");
        existing.setImageArtistsJson("[\"윤서연,정혜린\",\"윤서연\"]");
        existing.setImagesJson("[\"/uploads/a.jpg\",\"/uploads/b.jpg\"]");
        existing.setRegisteredOn(day);
        existing.setSource("COSMO_ROOM");
        existing.setDescription("기존");
        archives.saveAndFlush(existing);

        Archive saved = service().update(existing.getId(), "", List.of(), "윤서연", null, false, day,
                "COSMO_ROOM", "기존", null, null, null);
        entityManager.flush();
        entityManager.clear();
        Archive reloaded = archives.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getArtistNames()).isEqualTo("윤서연");
        assertThat(reloaded.getImageArtistsJson()).isNull();
    }

    private ArchiveService service() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        FileStorageService storage = mock(FileStorageService.class);
        when(storage.saveWithMeta(any())).thenAnswer(invocation -> meta("/uploads/" + seq.incrementAndGet() + ".jpg"));
        return new ArchiveService(archives, storage, new ObjectMapper(), entityManager);
    }

    private static SavedMeta meta(String url) {
        return new SavedMeta("a.jpg", "a.jpg", url, 2L, "image/jpeg", MediaType.IMAGE);
    }
}
