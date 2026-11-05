package com.example.triplesweb.service;

import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.util.ImageUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeAlbumHeroSelectionTest {

    @Test
    void excludesPreReleaseSinglesIncompleteCoversAndEmptyInfo() {
        assertFalse(AlbumService.isHomeHeroEligible(album("선공개 싱글", "/uploads/a.jpg", "소개")));
        assertFalse(AlbumService.isHomeHeroEligible(album("디지털 싱글", null, "소개")));
        assertFalse(AlbumService.isHomeHeroEligible(album("디지털 싱글", ImageUrls.PLACEHOLDER, "소개")));
        assertFalse(AlbumService.isHomeHeroEligible(album("정규 1집", "/uploads/a.svg", "소개")));
        assertFalse(AlbumService.isHomeHeroEligible(album("디지털 싱글", "/uploads/a.jpg", "  ")));
        assertTrue(AlbumService.isHomeHeroEligible(album("디지털 싱글", "/uploads/a.webp", "소개 문구")));
        assertTrue(AlbumService.isHomeHeroEligible(album("EP", "/uploads/a.jpg", "소개")));
    }

    @Test
    void prefersLongerAlbumBodyIntroAndKeepsFirstSentenceShort() {
        String info = """
                tripleS가 24인 완전체 <LOVE&POP> pt.2를 통해 다시 한번 새로운 음악을 선보인다.

                <LOVE&POP> pt.2는 pt.1 ‘LOVE’와 pt.3 ‘POP’을 잇는 ‘&’의 의미를 담은 싱글이다. ‘AND’는 서로를 연결하는 접속사로, 영원히 끝나지 않을 우리들의 순간과 무한한 가능성을 담아 ‘Andless’라는 이름을 지었다.
                """;
        String summary = HomeAlbumHero.summarize(info);
        assertEquals("LOVE&POP pt.2는 pt.1 ‘LOVE’와 pt.3 ‘POP’을 잇는 ‘&’의 의미를 담은 싱글이다.", summary);
        assertFalse(summary.contains("Andless"), summary);
        assertTrue(summary.length() <= 72, summary);
    }

    @Test
    void albumHeroCtaPointsToDetail() {
        Album album = album("디지털 싱글", "/uploads/a.webp", "앨범 소개 문장이 충분히 깁니다.");
        album.setAlbumId(14L);
        album.setAlbumName("LOVE&POP pt.2");
        HomeAlbumHero hero = HomeAlbumHero.from(album);
        assertEquals("/triples/album/14", hero.getHref());
        assertEquals("앨범 자세히 보기", hero.getCtaLabel());
    }

    private static Album album(String format, String image, String info) {
        Album album = new Album();
        album.setAlbumFormat(format);
        album.setAlbumImage(image);
        album.setAlbumInfo(info);
        album.setAlbumName("테스트");
        album.setReleaseDate("2026-01-01");
        return album;
    }
}
