package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Archive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.triplesweb.entity.MediaType;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {
    Page<Archive> findByFile_MediaTypeAndTitleContainingIgnoreCase(MediaType type, String q, Pageable pageable);
    Page<Archive> findByFile_MediaType(MediaType type, Pageable pageable);
}
