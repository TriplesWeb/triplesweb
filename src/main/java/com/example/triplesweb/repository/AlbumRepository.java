package com.example.triplesweb.repository;

import com.example.triplesweb.dto.AlbumListCard;
import com.example.triplesweb.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/**
 * 앨범({@link Album}) JPA 저장소.
 * {@link #findByAlbumName(String)}으로 앨범명 단건 조회를 제공한다.
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByAlbumName(String albumName);

    List<Album> findByUnit_UnitId(Long unitId);

    List<Album> findByUnit_UnitIdInOrderByReleaseDateAscAlbumIdAsc(Collection<Long> unitIds);

    /**
     * 목록 페이지용 경량 카드. albumInfo(LONGTEXT)를 제외하고 unit·section을 join한다.
     */
    @Query("""
            select new com.example.triplesweb.dto.AlbumListCard(
                a.albumId, a.albumName, a.albumImage, a.releaseDate, a.albumFormat,
                u.unitName, s.sectionId)
            from Album a
            left join a.unit u
            left join a.section s
            order by a.releaseDate desc, a.albumId desc
            """)
    List<AlbumListCard> findAllListCards();
}
