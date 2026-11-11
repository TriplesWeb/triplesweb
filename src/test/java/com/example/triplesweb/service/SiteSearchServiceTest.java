package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.dto.ShopCardDto;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteSearchServiceTest {
    private ArtistSnapshotService artists;
    private AlbumService albums;
    private UnitService units;
    private ChemistryService chemistry;
    private ObjektService objekts;
    private GravityService gravity;
    private ArticleService articles;
    private ArchiveService archives;
    private MediaService media;
    private EventService events;
    private ShopService shop;
    private ScheduleService schedules;
    private SiteSearchService search;

    @BeforeEach
    void setUp() {
        artists = mock(ArtistSnapshotService.class);
        albums = mock(AlbumService.class);
        units = mock(UnitService.class);
        chemistry = mock(ChemistryService.class);
        objekts = mock(ObjektService.class);
        gravity = mock(GravityService.class);
        articles = mock(ArticleService.class);
        archives = mock(ArchiveService.class);
        media = mock(MediaService.class);
        events = mock(EventService.class);
        shop = mock(ShopService.class);
        schedules = mock(ScheduleService.class);
        search = new SiteSearchService(artists, albums, units, chemistry, objekts, gravity,
                articles, archives, media, events, shop, schedules);
        when(artists.findAll()).thenReturn(List.of());
        when(albums.listAll()).thenReturn(List.of());
        when(units.listVisible()).thenReturn(List.of());
        when(chemistry.findAll()).thenReturn(List.of());
        when(objekts.findCollections("triples")).thenReturn(List.of());
        when(gravity.hasCosmoToken()).thenReturn(false);
        when(archives.pageByType(eq(MediaType.IMAGE), eq(0), eq(8), anyString())).thenReturn(new PageImpl<>(List.of()));
        when(archives.pageByType(eq(MediaType.VIDEO), eq(0), eq(8), anyString())).thenReturn(new PageImpl<>(List.of()));
        when(events.listGroupedBySection()).thenReturn(List.of());
        when(shop.listCards()).thenReturn(List.of());
        when(schedules.findAll()).thenReturn(List.of());
        when(articles.searchByCategory(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    void aboutTabMapsMembersAlbumsUnitsAndChemistry() {
        Artist artist = new Artist();
        artist.setArtistId(1L);
        artist.setName("윤서연");
        artist.setEngName("SeoYeon");
        when(artists.findAll()).thenReturn(List.of(artist));
        Album album = new Album();
        album.setAlbumId(14L);
        album.setAlbumName("ASSEMBLE");
        when(albums.listAll()).thenReturn(List.of(album));
        Unit unit = new Unit();
        unit.setUnitId(3L);
        unit.setUnitName("Acid Angel from Asia");
        unit.setSmallName("AAA");
        when(units.listVisible()).thenReturn(List.of(unit));
        when(chemistry.findAll()).thenReturn(List.of(ChemistryDto.builder()
                .label("시선강탈").memberA("서연").memberB("카에데").build()));

        SiteSearchService.Page page = search.search("서연", "about");
        SiteSearchService.Tab about = tab(page, "about");
        assertThat(about.isActive()).isTrue();
        assertThat(page.getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("윤서연");
        assertThat(page.getMembers().get(0).getEngName()).isEqualTo("SeoYeon");
        assertThat(page.getMembers().get(0).getProfileUrl()).isEqualTo("/triples/artist?tab=member-1");
        assertThat(page.getMembers().get(0).getProfileImage()).isEqualTo("/images/image-upload-pending.png");
        assertThat(about.getHits()).extracting(SiteSearchService.Hit::getKind)
                .contains("케미")
                .doesNotContain("멤버");
        assertThat(about.getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/triples/chemistry")
                .doesNotContain("/triples/artist?tab=member-1");

        SiteSearchService.Page assemble = search.search("ASSEMBLE", "about");
        assertThat(tab(assemble, "about").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/triples/album/14");
        SiteSearchService.Page aaa = search.search("AAA", "about");
        assertThat(tab(aaa, "about").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/triples/unit?tab=unit-3");
    }

    @Test
    void cosmoTabAddsNicknameShortcutAndObjektHitsAndSurvivesGravityFailure() {
        when(objekts.findCollections("triples")).thenReturn(List.of(Map.of(
                "member", "HaYeon",
                "collectionNo", "323",
                "season", "Cream02",
                "searchText", "Cream02 HaYeon 323",
                "collectionId", "Cream02 HaYeon 323",
                "slug", "hayeon-cc323"
        )));
        when(gravity.hasCosmoToken()).thenReturn(true);
        when(gravity.fetchGravityList("tripleS")).thenThrow(new IllegalStateException("no token"));

        SiteSearchService.Page page = search.search("cc323", "cosmo");
        SiteSearchService.Tab cosmo = tab(page, "cosmo");
        assertThat(cosmo.isActive()).isTrue();
        assertThat(cosmo.getHits()).extracting(SiteSearchService.Hit::getKind)
                .contains("코스모 아이디", "오브젝트");
        assertThat(cosmo.getHits().get(0).getHref()).isEqualTo("/cosmo/collection?nickname=cc323");
        assertThat(cosmo.getMoreHref()).isEqualTo("/cosmo/objekt?search=cc323");
        assertThat(cosmo.getCount()).isLessThanOrEqualTo(SiteSearchService.LIMIT);
        assertThat(search.search("cc323", null).getActiveTab()).isEqualTo("cosmo");
    }

    @Test
    void mediaInfoAndScheduleTabsMapCanonicalLinks() {
        Archive photo = new Archive();
        photo.setTitle("서연 직캠");
        photo.setArtistNames("서연");
        when(archives.pageByType(eq(MediaType.IMAGE), eq(0), eq(8), eq("서연")))
                .thenReturn(new PageImpl<>(List.of(photo)));
        when(media.allByTab(anyString())).thenReturn(List.of());
        when(media.allByTab("mv")).thenReturn(List.of(MediaDto.builder()
                .tab("mv").youtubeId("abcdefghijk").title("서연 MV").artistNames("서연").build()));
        ShopCardDto goods = new ShopCardDto();
        goods.setId(5L);
        goods.setCategory("merch");
        goods.setName("서연 키링");
        when(shop.listCards()).thenReturn(List.of(goods));
        Article info = new Article();
        info.setId(11L);
        info.setTitle("서연 인터뷰");
        info.setUserId("admin");
        info.setCategory("info");
        when(articles.searchByCategory(eq("info"), eq("title"), eq("서연"), eq(8)))
                .thenReturn(List.of(info));
        Schedule schedule = new Schedule("서연 생일", LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6));
        when(schedules.findAll()).thenReturn(List.of(schedule));

        SiteSearchService.Page mediaPage = search.search("서연", "media");
        assertThat(tab(mediaPage, "media").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/archive/image_archive?q=%EC%84%9C%EC%97%B0%20%EC%A7%81%EC%BA%A0",
                        "/media/mv/abcdefghijk");
        assertThat(tab(mediaPage, "info").getHits()).isEmpty();
        SiteSearchService.Page infoPage = search.search("서연", "info");
        assertThat(tab(infoPage, "info").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/shop/item/merch/5", "/article/info/11");
        SiteSearchService.Page schedulePage = search.search("서연", "schedule");
        assertThat(tab(schedulePage, "schedule").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/schedule");
        assertThat(tab(mediaPage, "media").getMoreHref()).startsWith("/archive/image_archive?q=");
    }

    @Test
    void membersStayAtTopEvenOnOtherTabsAndExactNameWins() {
        Artist seoyeon = new Artist();
        seoyeon.setArtistId(1L);
        seoyeon.setName("윤서연");
        seoyeon.setEngName("SeoYeon");
        Artist yeon = new Artist();
        yeon.setArtistId(2L);
        yeon.setName("서연");
        yeon.setEngName("Yeon");
        when(artists.findAll()).thenReturn(List.of(seoyeon, yeon));

        SiteSearchService.Page cosmo = search.search("서연", "cosmo");
        assertThat(cosmo.getActiveTab()).isEqualTo("cosmo");
        assertThat(cosmo.isHasMembers()).isTrue();
        assertThat(cosmo.getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("서연", "윤서연");
        assertThat(tab(cosmo, "cosmo").getHits()).extracting(SiteSearchService.Hit::getKind)
                .doesNotContain("멤버");
    }

    @Test
    void emptyKeywordDoesNotQuerySourcesAndUnknownTabFallsBack() {
        SiteSearchService.Page empty = search.search("  ", "nope");
        assertThat(empty.isHasAny()).isFalse();
        assertThat(empty.getActiveTab()).isEqualTo("about");
        assertThat(empty.getTabs()).extracting(SiteSearchService.Tab::getId)
                .containsExactly("about", "cosmo", "media", "info", "schedule");
    }

    private static SiteSearchService.Tab tab(SiteSearchService.Page page, String id) {
        return page.getTabs().stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
    }
}
