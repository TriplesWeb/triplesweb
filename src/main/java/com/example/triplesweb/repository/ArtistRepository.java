package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    boolean existsByArtistId(Long artistId);

    boolean existsByEngName(String engName);

    Optional<Artist> findByEngName(String engName);
}
