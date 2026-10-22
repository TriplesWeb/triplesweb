package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 앨범({@link Album}) JPA 저장소.
 * {@link #findByAlbumName(String)}으로 앨범명 단건 조회를 제공한다.
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByAlbumName(String albumName);

    List<Album> findByUnit_UnitId(Long unitId);
}
