package com.example.triplesweb.repository;

import com.example.triplesweb.entity.AlbumSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlbumSectionRepository extends JpaRepository<AlbumSection, Long> {
    List<AlbumSection> findAllByOrderBySortOrderAscSectionIdAsc();

    Optional<AlbumSection> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
