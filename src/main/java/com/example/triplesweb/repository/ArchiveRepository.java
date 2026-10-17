package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Archive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.triplesweb.entity.MediaType;

/**
 * 아카이브({@link Archive}) JPA 저장소.
 * {@link MediaType}별 페이징 및 제목 키워드(대소문자 무시) 검색을 지원한다.
 */
public interface ArchiveRepository extends JpaRepository<Archive, Long> {
    Page<Archive> findByFile_MediaTypeAndTitleContainingIgnoreCase(MediaType type, String q, Pageable pageable);
    Page<Archive> findByFile_MediaType(MediaType type, Pageable pageable);
}
