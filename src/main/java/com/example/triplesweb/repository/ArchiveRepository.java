package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Archive;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.triplesweb.entity.MediaType;

/**
 * 아카이브({@link Archive}) JPA 저장소.
 * {@link MediaType}별 페이징 및 제목 키워드(대소문자 무시) 검색을 지원한다.
 */
public interface ArchiveRepository extends JpaRepository<Archive, Long>, JpaSpecificationExecutor<Archive> {
    Page<Archive> findByFile_MediaTypeAndTitleContainingIgnoreCase(MediaType type, String q, Pageable pageable);
    Page<Archive> findByFile_MediaType(MediaType type, Pageable pageable);
    long countByFile_MediaType(MediaType type);

    @Query("""
            select a.file.mediaType, count(a)
            from Archive a
            group by a.file.mediaType
            """)
    List<Object[]> countGroupByMediaType();

    @Query("select a.id, unitId from Archive a join a.unitIds unitId")
    List<Object[]> findFolderIndexUnitIds();

    @Query("select distinct a from Archive a left join fetch a.unitIds where a.id in :ids")
    List<Archive> findWithUnitIdsByIdIn(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Archive a where a.id = :id")
    Optional<Archive> findByIdForUnitAssignment(@Param("id") long id);

    List<Archive> findTop2000ByFile_MediaTypeOrderByIdDesc(MediaType type);

    /** 사진 게시글 중복: 같은 멤버·날짜·출처·설명(빈 설명 포함). */
    @Query("""
            select (count(a) > 0) from Archive a
            where a.file.mediaType = :type
              and a.registeredOn = :registeredOn
              and a.source = :source
              and coalesce(a.artistNames, '') = :artistNames
              and coalesce(a.description, '') = :description
            """)
    boolean existsImageDuplicate(@Param("type") MediaType type,
                                 @Param("registeredOn") LocalDate registeredOn,
                                 @Param("source") String source,
                                 @Param("description") String description,
                                 @Param("artistNames") String artistNames);

    long countBySourceIgnoreCase(String source);

    @Query("""
            select distinct upper(a.source) from Archive a
            where a.file.mediaType = :type and a.source is not null
            """)
    List<String> findSourceCodesWithPosts(@Param("type") MediaType type);

    @Query("""
            select a from Archive a
            where a.file.mediaType = :type and locate(lower(:keyword), lower(a.title)) > 0
            order by a.registeredOn desc, a.id desc
            """)
    List<Archive> searchLatestByType(@Param("type") MediaType type, @Param("keyword") String keyword, Pageable pageable);

    /** 멤버 이름 부분 일치 후보 (정확한 멤버 매칭은 서비스에서 재필터). */
    List<Archive> findTop40ByFile_MediaTypeAndArtistNamesContainingIgnoreCaseOrderByIdDesc(
            MediaType type, String artistNames);
}
