package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Chemistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChemistryRepository extends JpaRepository<Chemistry, Long> {

    @Query("select c from Chemistry c join fetch c.artistA join fetch c.artistB")
    List<Chemistry> findAllWithArtists();

    @Query("""
            select c from Chemistry c
            join fetch c.artistA
            join fetch c.artistB
            where c.artistA.artistId = :a and c.artistB.artistId = :b
            """)
    Optional<Chemistry> findByArtistPair(@Param("a") Long artistAId, @Param("b") Long artistBId);
}
