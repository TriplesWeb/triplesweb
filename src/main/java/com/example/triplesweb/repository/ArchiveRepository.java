package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Archive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
    List<Archive> findTop2000ByFile_MediaTypeOrderByIdDesc(MediaType type);

    @Query("""
            select a from Archive a
            where a.file.mediaType = :type and locate(lower(:keyword), lower(a.title)) > 0
            order by case when a.registeredOn is null then 1 else 0 end, a.registeredOn desc, a.id desc
            """)
    List<Archive> searchLatestByType(@Param("type") MediaType type, @Param("keyword") String keyword, Pageable pageable);

    /** 멤버 이름 부분 일치 후보 (정확한 멤버 매칭은 서비스에서 재필터). */
    List<Archive> findTop40ByFile_MediaTypeAndArtistNamesContainingIgnoreCaseOrderByIdDesc(
            MediaType type, String artistNames);
}
