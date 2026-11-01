
package com.example.triplesweb.controller;

import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArtistAdminService;
import com.example.triplesweb.service.ChemistryViewCache;
import com.example.triplesweb.service.MemberSpotlightService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.MemberPortraitOptions;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;

import java.util.List;

@Controller
@RequestMapping("/triples")
@RequiredArgsConstructor
public class TriplesController {

    private final ArtistRepository artistrepository;
    private final ArtistAdminService artistAdminService;
    private final AlbumService albumService;
    private final UnitService unitService;
    private final MemberSpotlightService memberSpotlightService;
    private final ChemistryViewCache chemistryViewCache;

    /**
     * 멤버 소개(그룹 탭 포함).
     * 멤버별 오브젝트·아카이브·공식 미디어·일정은 탭 선택 시 로드한다.
     * `/triples/overview`(그룹 입문 메뉴)는 단독 페이지가 없어 404 → 공통 에러 화면으로 간다.
     */
    @GetMapping("/members")
    @Transactional(readOnly = true)
    public String members(Model model) {
        return renderArtistPage(model, "멤버", "/triples/members");
    }

    /** 북마크 호환용. 내용은 /triples/members 와 동일. */
    @GetMapping("/artist")
    @Transactional(readOnly = true)
    public String indexTriples(Model model) {
        return renderArtistPage(model, "ABOUT 트리플에스", "/triples/artist");
    }

    private String renderArtistPage(Model model, String pageHeading, String artistBase) {
        List<Artist> artistList = artistrepository.findAllWithUnits();
        GroupIntroDto group = artistAdminService.getGroupIntro();

        model.addAttribute("pageHeading", pageHeading);
        model.addAttribute("artistBase", artistBase);
        model.addAttribute("artist", artistList);
        model.addAttribute("allUnits", artistAdminService.listUnitOptions());
        model.addAttribute("groupTitle", group.getTitle());
        model.addAttribute("groupBody", group.getBody());
        model.addAttribute("groupImage", group.getImageUrl());
        model.addAttribute("groupTabImage", group.getTabImageUrl());
        return "triples/artist";
    }

    /** 선택한 멤버의 콘텐츠만 요청 시 조회한다. 첫 화면에서는 전체 멤버 자료를 읽지 않는다. */
    @GetMapping(value = "/artist/{id:[0-9]+}/feeds", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String artistFeeds(@PathVariable("id") long id, Model model) {
        Artist artist = artistrepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 멤버가 없습니다."));
        memberSpotlightService.attach(List.of(artist));
        model.addAttribute("artist", List.of(artist));
        return "triples/partials/artist-feeds";
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
    public String indexTriplesChemistry(Model model) {
        addChemistryPageData(model, false);
        return "triples/chemistry";
    }

    @GetMapping("/chemistry-en")
    public String indexTriplesChemistryEn(Model model) {
        addChemistryPageData(model, true);
        model.addAttribute("pageLang", "en");
        model.addAttribute("pageHtmlExtraAttrs", "data-chem-lang=\"en\"");
        return "triples/chemistry-en";
    }

    /** 캐시된 표 HTML만 심는다. 3인 피커는 탭 전환 시 조각으로 불러 첫 응답을 가볍게 유지한다. */
    private void addChemistryPageData(Model model, boolean englishLabels) {
        model.addAttribute("chemTheadHtml", chemistryViewCache.thead(englishLabels));
        model.addAttribute("chemTbodyHtml", chemistryViewCache.tbody(englishLabels));
    }

    /** 3인 이상 탭용 멤버 피커 HTML 조각. */
    @GetMapping(value = "/chemistry/picker", produces = MediaType.TEXT_HTML_VALUE)
    public String chemistryMemberPicker(
            @RequestParam(value = "lang", defaultValue = "ko") String lang,
            Model model
    ) {
        boolean english = lang != null && lang.toLowerCase().startsWith("en");
        List<Artist> artists = artistrepository.findAll(Sort.by(Sort.Direction.ASC, "artistId"));
        model.addAttribute("memberPickerOptions", MemberPortraitOptions.fromArtists(artists, english));
        return "partials/member-portrait-picker";
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
