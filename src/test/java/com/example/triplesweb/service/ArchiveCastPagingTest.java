package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import com.example.triplesweb.util.ArchiveSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
class ArchiveCastPagingTest {
    @Autowired ArchiveRepository archives;
    @Autowired EntityManager entityManager;

    @Test
    void comboPagesUseExactCastAndPreserveSourceTabsAndVisibleUnitExclusion() {
        save("첫 쌍", "윤서연,카에데", "FIRST", null);
        save("둘째 쌍", "S1XS9", "SECOND", 42L);
        save("보이는 유닛", "SeoYeon,Kaede", "UNIT", 7L);
        save("세 명", "S1,S2,S9", "TRIO", null);
        var units = List.of(new ArchiveFolderClassifier.UnitMembers(7L, "유닛", Set.of("윤서연", "카에데")));
        var service = service();

        var first = service.browseFolder(MediaType.IMAGE, 0, 1, null, List.of(), null, null,
                null, false, "combo-2", List.of("S1", "S9"), units, ArchiveSort.NEWEST);
        var second = service.browseFolder(MediaType.IMAGE, 1, 1, null, List.of(), null, null,
                null, false, "combo-2", List.of("S9", "S1"), units, ArchiveSort.NEWEST);
        var source = service.browseFolder(MediaType.IMAGE, 0, 1, null, List.of(), null, null,
                "first", false, "combo-2", List.of(), units, ArchiveSort.NEWEST);

        assertThat(first.page().getTotalElements()).isEqualTo(2);
        assertThat(first.page().getContent()).extracting(Archive::getTitle).containsExactly("둘째 쌍");
        assertThat(second.page().getContent()).extracting(Archive::getTitle).containsExactly("첫 쌍");
        assertThat(source.page().getContent()).extracting(Archive::getTitle).containsExactly("첫 쌍");
        assertThat(source.availableSources()).containsExactlyInAnyOrder("FIRST", "SECOND");
    }

    @Test
    void memberSortingKeepsUnknownCastLastAndUpdatesAfterEditing() {
        Archive edited = save("수정 예정", "S1 X S23", null, null);
        save("S9", "Kaede", null, null);
        save("기타", "unknown", null, null);
        assertThat(page(ArchiveSort.FIRST_MEMBER, 0)).containsExactly("수정 예정", "S9");
        assertThat(page(ArchiveSort.LAST_MEMBER, 0)).containsExactly("S9", "수정 예정");
        assertThat(page(ArchiveSort.LAST_MEMBER, 1)).containsExactly("기타");

        edited.setArtistNames("S24");
        archives.saveAndFlush(edited);
        assertThat(page(ArchiveSort.LAST_MEMBER, 0)).containsExactly("수정 예정", "S9");
    }

    private List<String> page(ArchiveSort sort, int page) {
        return service().pageByType(MediaType.IMAGE, page, 2, null, List.of(), null, null,
                null, false, false, sort).getContent().stream().map(Archive::getTitle).toList();
    }

    private ArchiveService service() {
        return new ArchiveService(archives, mock(FileStorageService.class), new ObjectMapper(), entityManager);
    }

    private Archive save(String title, String cast, String source, Long unitId) {
        Archive archive = new Archive(title, new SavedMeta("a.jpg", "a.jpg", "/uploads/a.jpg", 2L, "image/jpeg", MediaType.IMAGE));
        archive.setArtistNames(cast);
        archive.setSource(source);
        archive.setUnitId(unitId);
        return archives.saveAndFlush(archive);
    }
}
