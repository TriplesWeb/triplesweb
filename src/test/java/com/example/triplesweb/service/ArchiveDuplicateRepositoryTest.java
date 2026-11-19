package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
class ArchiveDuplicateRepositoryTest {
    @Autowired ArchiveRepository archives;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Vanity Teen Magazine"})
    void duplicateCheckIsScopedToMemberForBothUploadPaths(String description) throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 20);
        Archive existing = new Archive("", meta());
        existing.setArtistNames("윤서연");
        existing.setRegisteredOn(day);
        existing.setSource(ArchiveSource.COSMO_ROOM);
        existing.setDescription(description);
        archives.saveAndFlush(existing);

        FileStorageService storage = mock(FileStorageService.class);
        when(storage.saveWithMeta(any())).thenAnswer(invocation -> meta());
        ArchiveService service = new ArchiveService(archives, storage, new ObjectMapper());
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        String input = description == null || description.isEmpty() ? "   " : description;

        Archive other = service.uploadImages(List.of(file), "", "정혜린", day, ArchiveSource.COSMO_ROOM, input);
        assertThat(other.getArtistNames()).isEqualTo("정혜린");
        assertThatThrownBy(() -> service.uploadImages(List.of(file), "", "윤서연", day, ArchiveSource.COSMO_ROOM, input))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("같은 멤버");
        Archive single = service.save(file, "", MediaType.IMAGE, "이지우", day, ArchiveSource.COSMO_ROOM, input);
        assertThat(single.getArtistNames()).isEqualTo("이지우");
        assertThatThrownBy(() -> service.save(file, "", MediaType.IMAGE, " 윤서연 ", day, ArchiveSource.COSMO_ROOM, input))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("같은 멤버");
    }

    private static SavedMeta meta() {
        return new SavedMeta("a.jpg", "a.jpg", "/uploads/a.jpg", 2L, "image/jpeg", MediaType.IMAGE);
    }
}
