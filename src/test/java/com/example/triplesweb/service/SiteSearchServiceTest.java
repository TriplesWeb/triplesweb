package com.example.triplesweb.service;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.EventListCard;
import com.example.triplesweb.dto.EventSectionGroupDto;
import com.example.triplesweb.dto.ShopListCard;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        when(archives.searchLatestByType(eq(MediaType.IMAGE), anyString(), eq(8))).thenReturn(List.of());
        when(archives.searchLatestByType(eq(MediaType.VIDEO), anyString(), eq(8))).thenReturn(List.of());
        when(events.listGroupedBySection()).thenReturn(List.of());
        when(shop.listCards()).thenReturn(List.of());
        when(schedules.findAll()).thenReturn(List.of());
        when(articles.searchByCategory(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    void memberResultsRemainWithoutAboutSection() {
        Artist artist = new Artist();
        artist.setArtistId(1L);
        artist.setName("윤서연");
        artist.setEngName("SeoYeon");
        when(artists.findAll()).thenReturn(List.of(artist));
        when(chemistry.findAll()).thenReturn(List.of(ChemistryDto.builder().label("시선강탈")
                .memberA("서연").memberB("카에데").build()));
        SiteSearchService.Page page = search.search("서연", "about");
        assertThat(page.getActiveTab()).isEqualTo("cosmo");
        assertThat(page.getTabs()).extracting(SiteSearchService.Tab::getId).doesNotContain("about");
        assertThat(page.getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("윤서연");
        assertThat(page.getMembers().get(0).getEngName()).isEqualTo("SeoYeon");
        assertThat(page.getMembers().get(0).getProfileUrl()).isEqualTo("/triples/artist?tab=member-1");
        assertThat(page.getMembers().get(0).getProfileImage()).isEqualTo("/images/image-upload-pending.png");
    }

    @Test
    void aboutAppearsOnlyForMatchingAlbumUnitOrChemistryNames() {
        Album album = new Album();
        album.setAlbumId(14L);
        album.setAlbumName("ASSEMBLE");
        Unit unit = new Unit();
        unit.setUnitId(3L);
        unit.setUnitName("Acid Angel from Asia");
        unit.setSmallName("AAA");
        when(albums.listAll()).thenReturn(List.of(album));
        when(units.listVisible()).thenReturn(List.of(unit));
        when(chemistry.findAll()).thenReturn(List.of(ChemistryDto.builder().id(42L).label("시선강탈")
                .labelEn("Eye Catchers").memberA("서연").memberB("카에데").build()));

        assertThat(tab(search.search("assemble", "about"), "about").getHits())
                .extracting(SiteSearchService.Hit::getHref).containsExactly("/triples/album/14");
        assertThat(tab(search.search("AAA", null), "about").getHits())
                .extracting(SiteSearchService.Hit::getHref).containsExactly("/triples/unit?tab=unit-3");
        assertThat(tab(search.search("시선강탈", null), "about").getHits())
                .extracting(SiteSearchService.Hit::getHref).containsExactly("/triples/chemistry?pair=42");
        assertThat(tab(search.search("Eye Catchers", null), "about").getHits())
                .extracting(SiteSearchService.Hit::getKind).containsExactly("케미");
        assertThat(search.search("카에데", null).getTabs())
                .extracting(SiteSearchService.Tab::getId).doesNotContain("about");
    }

    @Test
    void cosmoTabDoesNotAddNicknameShortcutAndSurvivesGravityFailure() {
        when(objekts.findCollections("triples")).thenReturn(List.of(Map.of(
                "member", "HaYeon",
                "collectionNo", "323",
                "season", "Cream02",
                "thumbnailImage", "https://example.org/thumb.jpg",
                "frontImage", "https://example.org/front.jpg",
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
                .containsExactly("오브젝트");
        var objektHit = cosmo.getHits().stream().filter(SiteSearchService.Hit::isObjekt).findFirst().orElseThrow();
        assertThat(objektHit.getImageUrl()).isEqualTo("https://example.org/thumb.jpg");
        assertThat(cosmo.getHits().get(0).isObjekt()).isTrue();
        assertThat(cosmo.isHasMore()).isFalse();
        assertThat(cosmo.getCount()).isLessThanOrEqualTo(SiteSearchService.LIMIT);
        assertThat(search.search("cc323", null).getActiveTab()).isEqualTo("cosmo");
        assertThat(search.search("not-a-real-user", null).isHasAny()).isFalse();
    }

    @Test
    void mediaInfoAndScheduleTabsMapCanonicalLinks() {
        Archive photo = new Archive();
        photo.setTitle("서연 직캠");
        photo.setArtistNames("서연");
        when(archives.searchLatestByType(eq(MediaType.IMAGE), eq("서연"), eq(8)))
                .thenReturn(List.of(photo));
        when(media.allByTab(anyString())).thenReturn(List.of());
        when(media.allByTab("mv")).thenReturn(List.of(MediaDto.builder()
                .tab("mv").youtubeId("abcdefghijk").title("서연 MV").artistNames("서연").build()));
        Shop goods = new Shop();
        goods.setId(5L);
        goods.setTitle("서연 키링");
        goods.setSalesPeriod("");
        when(shop.listCards()).thenReturn(List.of(new ShopListCard(goods)));
        Article info = new Article();
        info.setId(11L);
        info.setTitle("서연 인터뷰");
        info.setUserId("admin");
        info.setCategory("info");
        when(articles.searchLatestByCategory(eq("info"), eq("서연"), eq(5)))
                .thenReturn(List.of(info));
        Schedule schedule = new Schedule("서연 생일", LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 6));
        when(schedules.findAll()).thenReturn(List.of(schedule));

        SiteSearchService.Page mediaPage = search.search("서연", "media");
        assertThat(tab(mediaPage, "media").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/archive/image_archive?q=%EC%84%9C%EC%97%B0%20%EC%A7%81%EC%BA%A0",
                        "/media/mv/abcdefghijk");
        assertThat(tab(mediaPage, "info").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/shop/5", "/article/info/11");
        SiteSearchService.Page infoPage = search.search("서연", "info");
        assertThat(tab(infoPage, "info").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/shop/5", "/article/info/11");
        SiteSearchService.Page schedulePage = search.search("서연", "schedule");
        assertThat(tab(schedulePage, "schedule").getHits()).extracting(SiteSearchService.Hit::getHref)
                .contains("/schedule");
        assertThat(tab(mediaPage, "media").isHasMore()).isFalse();
        assertThat(tab(mediaPage, "info").isHasMore()).isFalse();
        assertThat(tab(mediaPage, "schedule").isHasMore()).isFalse();
    }

    @Test
    void sortsMediaAcrossSourcesByDateBeforeLimit() {
        Archive photo = new Archive(); photo.setTitle("지연 사진"); photo.setRegisteredOn(LocalDate.of(2025, 1, 1));
        Archive video = new Archive(); video.setTitle("지연 영상"); video.setRegisteredOn(LocalDate.of(2026, 1, 1));
        when(archives.searchLatestByType(MediaType.IMAGE, "지연", 8)).thenReturn(List.of(photo));
        when(archives.searchLatestByType(MediaType.VIDEO, "지연", 8)).thenReturn(List.of(video));
        when(media.allByTab("mv")).thenReturn(List.of(
                MediaDto.builder().tab("mv").youtubeId("a").title("지연 미디어 최신").publishedOn("2026-09-01").build(),
                MediaDto.builder().tab("mv").youtubeId("b").title("지연 미디어 과거").publishedOn("2024-02-01").build()));
        assertThat(tab(search.search("지연", null), "media").getHits())
                .extracting(SiteSearchService.Hit::getLabel)
                .containsExactly("지연 미디어 최신", "지연 영상", "지연 사진", "지연 미디어 과거");
    }

    @Test
    void sortsInfoAndSchedulesAcrossSourcesBeforeTakingFive() {
        Event event = new Event(); event.setEventId(1L); event.setEventName("지연 행사");
        EventStop stop = new EventStop(); stop.setEventDate("2026.03.01~2026.12.31");
        event.setStops(List.of(stop));
        when(events.listGroupedBySection()).thenReturn(List.of(EventSectionGroupDto.builder()
                .name("공연").events(List.of(new EventListCard(event))).build()));
        Shop goods = new Shop(); goods.setId(2L); goods.setTitle("지연 굿즈"); goods.setSalesPeriod("2026.05.01~2026.12.01");
        when(shop.listCards()).thenReturn(List.of(new ShopListCard(goods)));
        Article article = new Article(); article.setId(3L); article.setTitle("지연 공지");
        article.setFormattedCreatedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        when(articles.searchLatestByCategory("notice", "지연", 5)).thenReturn(List.of(article));
        when(schedules.findAll()).thenReturn(List.of(
                new Schedule("지연 1", LocalDate.of(2020, 1, 1), null),
                new Schedule("지연 2", LocalDate.of(2022, 1, 1), null),
                new Schedule("지연 3", LocalDate.of(2023, 1, 1), null),
                new Schedule("지연 4", LocalDate.of(2024, 1, 1), null),
                new Schedule("지연 5", LocalDate.of(2025, 1, 1), null),
                new Schedule("지연 6", LocalDate.of(2026, 1, 1), null)));
        var page = search.search("지연", null);
        assertThat(tab(page, "info").getHits()).extracting(SiteSearchService.Hit::getLabel)
                .containsExactly("지연 굿즈", "지연 공지", "지연 행사");
        assertThat(tab(page, "schedule").getHits()).extracting(SiteSearchService.Hit::getLabel)
                .containsExactly("지연 6", "지연 5", "지연 4", "지연 3", "지연 2");
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
    void memberSearchMatchesAtLeastTwoCharactersInNamesAndOptionalRealName() {
        Artist hayeon = new Artist();
        hayeon.setArtistId(1L);
        hayeon.setName("정하연");
        hayeon.setEngName("HaYeon");
        Artist chaewon = new Artist();
        chaewon.setArtistId(2L);
        chaewon.setName("김채원");
        chaewon.setRealName("다른 본명");
        when(artists.findAll()).thenReturn(List.of(hayeon, chaewon));
        assertThat(search.search("하연", null).getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("정하연");
        assertThat(search.search("채원", null).getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("김채원");
        assertThat(search.search("본명", null).getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("김채원");
        assertThat(search.search("하", null).getMembers()).isEmpty();
        assertThat(search.search("HAYEON", null).getMembers()).extracting(SiteSearchService.Member::getName)
                .containsExactly("정하연");
    }

    @Test
    void cosmoShowsOnlyFiveNewestObjektsAndRetainsOtherResults() {
        when(objekts.findCollections("triples")).thenReturn(List.of(
                objekt("1", "2026-01-01"), objekt("5", "2026-05-01"),
                objekt("2", "2026-02-01"), objekt("6", "2026-06-01"),
                objekt("3", "2026-03-01"), objekt("4", "2026-04-01"),
                objekt("0", "")));
        Article trade = new Article();
        trade.setId(7L);
        trade.setTitle("지연 교환");
        when(articles.searchByCategory("trade", "title", "지연", 8)).thenReturn(List.of(trade));

        var cosmo = tab(search.search("지연", "cosmo"), "cosmo");
        assertThat(cosmo.getHits()).filteredOn(hit -> hit.getKind().equals("오브젝트"))
                .extracting(SiteSearchService.Hit::getLabel)
                .containsExactly("지연 6", "지연 5", "지연 4", "지연 3", "지연 2");
        assertThat(cosmo.getHits()).extracting(SiteSearchService.Hit::getKind).contains("교환");
        assertThat(cosmo.isHasMore()).isTrue();
        assertThat(cosmo.getMoreHref()).isEqualTo("/cosmo/objekt?search=%EC%A7%80%EC%97%B0");

        when(objekts.findCollections("triples")).thenReturn(List.of(
                objekt("1", "2026-01-01"), objekt("2", "2026-02-01"),
                objekt("3", "2026-03-01"), objekt("4", "2026-04-01")));
        assertThat(tab(search.search("지연", "cosmo"), "cosmo").isHasMore()).isFalse();
        when(objekts.findCollections("triples")).thenReturn(List.of(
                objekt("1", "2026-01-01"), objekt("2", "2026-02-01"),
                objekt("3", "2026-03-01"), objekt("4", "2026-04-01"), objekt("5", "2026-05-01")));
        assertThat(tab(search.search("지연", "cosmo"), "cosmo").isHasMore()).isTrue();
    }

    @Test
    void keywordFillsEverySection() {
        when(objekts.findCollections("triples")).thenReturn(List.of());
        SiteSearchService.Page page = search.search("없는검색어", "cosmo");
        assertThat(page.getTabs()).extracting(SiteSearchService.Tab::getId)
                .containsExactly("cosmo", "media", "info", "schedule");
        assertThat(page.getTabs()).allMatch(tab -> !tab.isHasHits() || tab.getCount() <= SiteSearchService.LIMIT);
    }

    @Test
    void emptyKeywordDoesNotQuerySourcesAndUnknownTabFallsBack() {
        SiteSearchService.Page empty = search.search("  ", "nope");
        assertThat(empty.isHasAny()).isFalse();
        assertThat(empty.getActiveTab()).isEqualTo("cosmo");
        assertThat(empty.getTabs()).extracting(SiteSearchService.Tab::getId)
                .containsExactly("cosmo", "media", "info", "schedule");
    }

    private static Map<String, Object> objekt(String number, String createdAt) {
        return Map.of("member", "지연", "collectionNo", number,
                "collectionId", "id-" + number, "slug", "slug-" + number, "createdAt", createdAt);
    }

    private static SiteSearchService.Tab tab(SiteSearchService.Page page, String id) {
        return page.getTabs().stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
    }
}
