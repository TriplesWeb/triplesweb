package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.UnitRepository;
import com.example.triplesweb.service.ArtistAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * TRIPLES 아티스트·앨범·유닛 소개 및 상세(지연 로딩·유닛 트리 통합 표시 포함).
 * URL: {@code /triples/artist}, {@code /triples/album}, {@code /triples/unit}, 각 {@code /{id}}
 * 레이어: Optional — Album·Artist·Unit Repository 직접 사용(Repository 단독 허용).
 */
@Controller
@RequestMapping("/triples")
@RequiredArgsConstructor
public class TriplesController {

    private final AlbumRepository albumrepository;
    private final ArtistRepository artistrepository;
    private final UnitRepository unitrepository;
    private final ArtistAdminService artistAdminService;

    @GetMapping("/artist")
    @Transactional(readOnly = true)
    public String indexTriples(Model model) {
        List<Artist> artistList = artistrepository.findAllWithUnits();
        GroupIntroDto group = artistAdminService.getGroupIntro();
        model.addAttribute("artist", artistList);
        model.addAttribute("allUnits", artistAdminService.listUnitOptions());
        model.addAttribute("groupTitle", group.getTitle());
        model.addAttribute("groupBody", group.getBody());
        model.addAttribute("groupImage", group.getImageUrl());
        model.addAttribute("groupTabImage", group.getTabImageUrl());
        return "triples/artist";
    }

    @GetMapping("/album")
    public String indexTriplesAlbum(Model model) {
        List<Album> albumList = albumrepository.findAll();
        model.addAttribute("album", albumList);
    return "triples/album";
    }
    @GetMapping("/unit")
    public String indexTriplesUnit(Model model) {
        List<Unit> unitList = unitrepository.findAll();
        model.addAttribute("unit", unitList);
        return "triples/unit";
    }

    @GetMapping("/chemistry")
    public String indexTriplesChemistry() {
        return "triples/chemistry";
    }

    /** 상세는 그룹 소개 탭에 통합. 케미/유닛 등 기존 링크는 해당 멤버 탭으로 보낸다. */
    @GetMapping("/artist/{id:[0-9]+}")
    public String TriplesArtistDetail(@PathVariable("id") long artistId) {
        if (!artistrepository.existsByArtistId(artistId)) {
            throw new IllegalArgumentException("해당 아티스트가 존재하지 않습니다. id=" + artistId);
        }
        // redirect: 는 #fragment를 버리므로 tab 쿼리로 전달
        return "redirect:/triples/artist?tab=member-" + artistId;
    }
    @GetMapping("/album/{id:[0-9]+}")
    @Transactional(readOnly = true)
    public String TriplesAlbumDetail(@PathVariable("id") long albumId, Model model) {
        Album album = albumrepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));;
        if (album.getUnit() != null && album.getUnit().getSubUnits() != null) {
            album.getUnit().getSubUnits().size(); // Hibernate Lazy Loading 강제 컬렉션 조회를 일으킴
        }

        model.addAttribute("album", album);
        model.addAttribute("isAlbum2", (albumId == 2L));
        return "triples/album_detail";
    }
    @GetMapping("/unit/{id:[0-9]+}")
    @Transactional(readOnly = true)
    public String TriplesUnitDetail(@PathVariable("id") long unitId, Model model) {
        Unit unit = unitrepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유닛이 존재하지 않습니다. id=" + unitId));

        List<Album> albums = new ArrayList<>(unit.getAlbums());

        // 하위 유닛인 경우 상위 유닛의 앨범도 통합하여 보여줌
        if (unit.getParentUnit() != null && unit.getParentUnit().getAlbums() != null) {
            for (Album parentAlbum : unit.getParentUnit().getAlbums()) {
                if (!albums.contains(parentAlbum)) {
                    albums.add(parentAlbum);
                }
            }
        }
        if (unit.getSubUnits() != null && !unit.getSubUnits().isEmpty()) {
            for (Unit subUnit : unit.getSubUnits()) {
                for (Album subAlbum : subUnit.getAlbums()) {
                    if (!albums.contains(subAlbum)) {
                        albums.add(subAlbum);
                    }
                }
            }
        }
        model.addAttribute("unit", unit );
        model.addAttribute("albums", albums);
        return "triples/unit_detail";
    }
}
