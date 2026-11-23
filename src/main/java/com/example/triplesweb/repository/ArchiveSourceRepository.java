package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArchiveSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArchiveSourceRepository extends JpaRepository<ArchiveSource, Long> {
    List<ArchiveSource> findAllByOrderBySortOrderAscIdAsc();

    List<ArchiveSource> findByVisibleTrueOrderBySortOrderAscIdAsc();

    Optional<ArchiveSource> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
