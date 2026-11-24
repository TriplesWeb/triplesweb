package com.example.triplesweb.service;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArchiveSourceRepository;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import com.example.triplesweb.util.ArchiveSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
@Import(ArchiveSourceService.class)
class ArchiveCastPagingTest {
    @Autowired ArchiveRepository archives;
    @Autowired EntityManager entityManager;
    @Autowired ArchiveSourceService sourceService;
    @Autowired ArchiveSourceRepository sources;

    @Test
    void comboPagesUseExactCastAndPreserveSourceFilterAndVisibleUnitExclusion() {
        save("첫 쌍", "윤서연,카에데", "FIRST");
        save("둘째 쌍", "S1XS9", "SECOND", 42L, 43L);
        save("보이는 유닛", "SeoYeon,Kaede", "UNIT", 42L, 7L);
        save("세 명", "S1,S2,S9", "TRIO");
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
    void sourceExistenceIsDistinctAndScopedToMediaType() {
        save("사진 하나", "S1", "first");
        save("사진 둘", "S1", "FIRST");
        save("출처 없는 사진", "S1", null);
        var video = new Archive("영상", new SavedMeta("a.mp4", "a.mp4", "/uploads/a.mp4", 2L, "video/mp4", MediaType.VIDEO));
        video.setSource("video_only");
        archives.saveAndFlush(video);

        assertThat(archives.findSourceCodesWithPosts(MediaType.IMAGE)).containsExactly("FIRST");
        assertThat(archives.findSourceCodesWithPosts(MediaType.VIDEO)).containsExactly("VIDEO_ONLY");
        var service = service();
        assertThat(service.availableSources(MediaType.IMAGE, "사진 하나", List.of("윤서연"), null, null))
                .containsExactly("first");
        assertThat(service.availableSources(MediaType.IMAGE, "사진 하나", List.of("카에데"), null, null)).isEmpty();
        assertThat(service.availableSources(MediaType.VIDEO, "사진 하나", List.of(), null, null)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSourceCreationStoresUniqueCodes() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        List<ArchiveSourceDto> created = new ArrayList<>();
        try {
            Callable<ArchiveSourceDto> create = () -> sourceService.create("동시 생성", null, 10);
            for (var result : pool.invokeAll(java.util.Collections.nCopies(8, create), 15, TimeUnit.SECONDS)) {
                created.add(result.get());
            }

            assertThat(created).extracting(ArchiveSourceDto::code).hasSize(8).doesNotHaveDuplicates()
                    .allMatch(code -> code.matches("^[A-Z][A-Z0-9_]{0,31}$"));
        } finally {
            pool.shutdownNow();
            sources.deleteAllById(created.stream().map(ArchiveSourceDto::id).toList());
        }
    }

    @Test
    void memberSortingKeepsUnknownCastLastAndUpdatesAfterEditing() {
        Archive edited = save("수정 예정", "S1 X S23", null);
        save("S9", "Kaede", null);
        save("기타", "unknown", null);
        assertThat(page(ArchiveSort.FIRST_MEMBER, 0)).containsExactly("수정 예정", "S9");
        assertThat(page(ArchiveSort.LAST_MEMBER, 0)).containsExactly("S9", "수정 예정");
        assertThat(page(ArchiveSort.LAST_MEMBER, 1)).containsExactly("기타");

        edited.setArtistNames("S24");
        archives.saveAndFlush(edited);
        assertThat(page(ArchiveSort.LAST_MEMBER, 0)).containsExactly("수정 예정", "S9");
    }

    @Test
    void multipleAssignmentsAreLoadedAndCountedInEveryUnitFolder() {
        Archive shared = save("두 유닛", "윤서연,정혜린,카에데", "SHARED", 7L, 8L);
        save("두 번째 유닛", "윤서연,카에데", "SECOND", 8L);
        entityManager.clear();
        var service = service();
        var members = Set.of("윤서연", "카에데");
        var units = List.of(
                new ArchiveFolderClassifier.UnitMembers(7L, "첫 유닛", members),
                new ArchiveFolderClassifier.UnitMembers(8L, "둘째 유닛", Set.of("정혜린", "카에데")));

        var first = service.browseFolder(MediaType.IMAGE, 0, 10, null, List.of(), null, null,
                null, false, "unit-7", List.of(), units, ArchiveSort.NEWEST);
        var second = service.browseFolder(MediaType.IMAGE, 0, 10, null, List.of(), null, null,
                null, false, "unit-8", List.of(), units, ArchiveSort.NEWEST);
        var byMembers = service.browseFolder(MediaType.IMAGE, 0, 10, null, List.copyOf(members), null, null,
                null, false, "s", List.of(), units, ArchiveSort.NEWEST);

        assertThat(first.page().getContent()).extracting(Archive::getId).containsExactly(shared.getId());
        assertThat(first.page().getTotalElements()).isEqualTo(1);
        assertThat(second.page().getContent()).extracting(Archive::getTitle).containsExactly("두 번째 유닛", "두 유닛");
        assertThat(second.page().getTotalElements()).isEqualTo(2);
        assertThat(Hibernate.isInitialized(first.page().getContent().get(0).getUnitIds())).isTrue();
        assertThat(first.page().getContent().get(0).getUnitIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(first.availableSources()).containsExactly("SHARED");
        assertThat(second.availableSources()).containsExactlyInAnyOrder("SHARED", "SECOND");
        assertThat(byMembers.page().getContent()).extracting(Archive::getId).containsExactly(shared.getId());
        assertThat(service.index()).filteredOn(archive -> archive.getId().equals(shared.getId()))
                .singleElement().satisfies(archive -> assertThat(archive.getUnitIds()).containsExactlyInAnyOrder(7L, 8L));
    }

    @Test
    void assignmentNormalizesIdsAndClearsAllWhenEmpty() {
        Archive saved = save("유닛 변경", "윤서연", null, 7L);
        var service = service();
        assertThat(service.index().get(0).getUnitIds()).containsExactly(7L);

        service.assignUnitIds(saved.getId(), Arrays.asList(8L, null, 0L, -2L, 8L, 9L));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.index().get(0).getUnitIds()).containsExactlyInAnyOrder(8L, 9L);
        assertThat(archives.findById(saved.getId()).orElseThrow().getUnitIds()).containsExactlyInAnyOrder(8L, 9L);
        var cached = service.index();
        service.assignUnitIds(saved.getId(), List.of(9L, 8L, 9L));
        assertThat(service.index()).isSameAs(cached);

        service.assignUnitIds(saved.getId(), null);
        entityManager.flush();
        entityManager.clear();
        assertThat(archives.findById(saved.getId()).orElseThrow().getUnitIds()).isEmpty();
    }

    @Test
    void exactCastBackfillOnlyFillsArchivesWithoutAssignments() {
        Archive pending = save("미지정", "윤서연,카에데", null);
        Archive assigned = save("수동 지정", "윤서연,카에데", null, 8L, 9L);
        var service = service();
        var unit = new ArchiveFolderClassifier.UnitMembers(7L, "유닛", Set.of("윤서연", "카에데"));

        assertThat(service.backfillUnitIdsFromExactCast(List.of(unit))).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        assertThat(archives.findById(pending.getId()).orElseThrow().getUnitIds()).containsExactly(7L);
        assertThat(archives.findById(assigned.getId()).orElseThrow().getUnitIds()).containsExactlyInAnyOrder(8L, 9L);
        assertThat(service.backfillUnitIdsFromExactCast(List.of(unit))).isZero();
    }

    private List<String> page(ArchiveSort sort, int page) {
        return service().pageByType(MediaType.IMAGE, page, 2, null, List.of(), null, null,
                null, false, false, sort).getContent().stream().map(Archive::getTitle).toList();
    }

    private ArchiveService service() {
        return new ArchiveService(archives, mock(FileStorageService.class), new ObjectMapper(), entityManager);
    }

    private Archive save(String title, String cast, String source, Long... unitIds) {
        Archive archive = new Archive(title, new SavedMeta("a.jpg", "a.jpg", "/uploads/a.jpg", 2L, "image/jpeg", MediaType.IMAGE));
        archive.setArtistNames(cast);
        archive.setSource(source);
        archive.setUnitIds(Set.of(unitIds));
        return archives.saveAndFlush(archive);
    }
}
