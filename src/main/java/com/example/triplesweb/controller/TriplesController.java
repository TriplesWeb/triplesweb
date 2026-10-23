package com.example.triplesweb.controller;

import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArtistAdminService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * TRIPLES 아티스트·앨범·유닛 소개 및 상세(지연 로딩·유닛 트리 통합 표시 포함).
 * URL: {@code /triples/artist}, {@code /triples/album}, {@code /triples/unit}, 각 {@code /{id}}
 */
@Controller
@RequestMapping("/triples")
@RequiredArgsConstructor
public class TriplesController {

    private final ArtistRepository artistrepository;
    private final ArtistAdminService artistAdminService;
    private final AlbumService albumService;
    private final UnitService unitService;
    private final MediaService mediaService;

    @GetMapping("/artist")
    @Transactional(readOnly = true)
    public String indexTriples(Model model) {
        List<Artist> artistList = artistrepository.findAllWithUnits();
        var mediaByArtist = mediaService.byArtistMap(8);
        artistList.forEach(artist -> artist.setMediaContents(
                mediaByArtist.getOrDefault(artist.getArtistId(), java.util.List.of())));
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
        model.addAttribute("albumSections", albumService.listGroupedBySection());
        return "triples/album";
    }

    @GetMapping("/unit")
    public String indexTriplesUnit(Model model) {
        model.addAttribute("unit", unitService.listVisible());
        return "triples/unit";
    }

    @GetMapping("/chemistry")
    public String indexTriplesChemistry() {
        return "triples/chemistry";
    }

    @GetMapping("/chemistry-en")
    public String indexTriplesChemistryEn() {
        return "triples/chemistry-en";
    }

    /** 상세는 그룹 소개 탭에 통합. 케미/유닛 등 기존 링크는 해당 멤버 탭으로 보낸다. */
    @GetMapping("/artist/{id:[0-9]+}")
    public String TriplesArtistDetail(@PathVariable("id") long artistId) {
        if (!artistrepository.existsByArtistId(artistId)) {
            throw new IllegalArgumentException("해당 아티스트가 존재하지 않습니다. id=" + artistId);
        }
        return "redirect:/triples/artist?tab=member-" + artistId;
    }

    @GetMapping("/album/{id:[0-9]+}")
    public String TriplesAlbumDetail(@PathVariable("id") long albumId, Model model) {
        Album album = albumService.getDetail(albumId);
        model.addAttribute("album", album);
        return "triples/album_detail";
    }

    /** 상세는 유닛 목록 탭에 통합. 기존 링크는 해당 탭으로 보낸다. */
    @GetMapping("/unit/{id:[0-9]+}")
    public String TriplesUnitDetail(@PathVariable("id") long unitId) {
        unitService.getDetail(unitId); // 존재 여부 검증
        return "redirect:/triples/unit?tab=unit-" + unitId;
    }
}
