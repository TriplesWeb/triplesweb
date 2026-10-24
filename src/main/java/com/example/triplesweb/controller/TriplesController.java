
package com.example.triplesweb.controller;

import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArtistAdminService;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.service.UnitService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/triples")
@RequiredArgsConstructor
public class TriplesController {

    private final ArtistRepository artistrepository;
    private final ArtistAdminService artistAdminService;
    private final AlbumService albumService;
    private final UnitService unitService;
    private final MediaService mediaService;

    /**
     * 그룹 및 멤버 소개 페이지.
     * 멤버별 미디어를 최대 8개씩 연결한다.
     */
    @GetMapping("/artist")
    @Transactional(readOnly = true)
    public String indexTriples(Model model) {

        // 유닛 정보를 포함한 아티스트 조회
        List<Artist> artistList =
                artistrepository.findAllWithUnits();

        // 아티스트 ID별 미디어 목록 조회
        Map<Long, List<MediaDto>> mediaByArtist =
                mediaService.byArtistMap(12);

        // 각 아티스트 객체에 미디어 목록 연결
        artistList.forEach(artist ->
                artist.setMediaContents(
                        mediaByArtist.getOrDefault(
                                artist.getArtistId(),
                                List.of()
                        )
                )
        );

        // 그룹 소개 정보
        GroupIntroDto group =
                artistAdminService.getGroupIntro();

        model.addAttribute("artist", artistList);
        model.addAttribute(
                "allUnits",
                artistAdminService.listUnitOptions()
        );
        model.addAttribute("groupTitle", group.getTitle());
        model.addAttribute("groupBody", group.getBody());
        model.addAttribute("groupImage", group.getImageUrl());
        model.addAttribute(
                "groupTabImage",
                group.getTabImageUrl()
        );

        return "triples/artist";
    }

    @GetMapping("/album")
    public String indexTriplesAlbum(Model model) {
        model.addAttribute(
                "albumSections",
                albumService.listGroupedBySection()
        );
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

    /**
     * 기존 아티스트 상세 링크를 해당 멤버 탭으로 연결한다.
     */
    @GetMapping("/artist/{id:[0-9]+}")
    public String triplesArtistDetail(
            @PathVariable("id") long artistId
    ) {
        if (!artistrepository.existsByArtistId(artistId)) {
            throw new IllegalArgumentException(
                    "해당 아티스트가 존재하지 않습니다. id="
                            + artistId
            );
        }

        return "redirect:/triples/artist?tab=member-" + artistId;
    }

    @GetMapping("/album/{id:[0-9]+}")
    public String triplesAlbumDetail(
            @PathVariable("id") long albumId,
            Model model
    ) {
        Album album = albumService.getDetail(albumId);
        model.addAttribute("album", album);

        return "triples/album_detail";
    }

    /**
     * 기존 유닛 상세 링크를 유닛 탭으로 연결한다.
     */
    @GetMapping("/unit/{id:[0-9]+}")
    public String triplesUnitDetail(
            @PathVariable("id") long unitId
    ) {
        unitService.getDetail(unitId);

        return "redirect:/triples/unit?tab=unit-" + unitId;
    }
}
