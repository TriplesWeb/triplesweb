package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArchiveSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArchiveSourceRepository extends JpaRepository<ArchiveSource, Long> {
    List<ArchiveSource> findAllByOrderBySortOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ArchiveSource s order by s.sortOrder, s.id")
    List<ArchiveSource> findAllForReorder();

    Optional<ArchiveSource> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
