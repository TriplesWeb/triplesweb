package com.example.triplesweb.util;

import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.entity.Artist;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleMemberSwapTest {
    @Test void matchesHaveMemberAndSummarizesExtras() {
        ArticleObjekt haveSeoyeon = link("HAVE", "윤서연");
        ArticleObjekt haveHayeon = link("HAVE", "정하연");
        ArticleObjekt wantJiyeon = link("WANT", "지연");
        ArticleObjekt wantDuo = link("WANT", "S5 X S20");
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, wantJiyeon), "서연", "have")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, wantJiyeon), "지연", "have")).isFalse();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, wantJiyeon), "지연", "want")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, wantJiyeon), "지연", "all")).isTrue();

        ArticleObjekt haveKotone = link("HAVE", "코토네");
        ArticleObjekt haveNakyoung = link("HAVE", "NaKyoung");
        ArticleObjekt haveDuo = link("HAVE", "S11 X S7");
        ArticleObjekt wantNakyoung = link("WANT", "김나경");
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, haveKotone, haveHayeon), "코토네", "have")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveSeoyeon, haveKotone), "정하연", "have")).isFalse();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveKotone, haveNakyoung), "김나경", "have")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveKotone, haveNakyoung), "나경", "have")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveDuo), "김나경", "have")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveKotone, wantNakyoung), "김나경", "have")).isFalse();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveKotone, wantNakyoung), "김나경", "all")).isTrue();
        assertThat(ArticleMemberSwap.matchesMember(List.of(haveKotone, wantNakyoung), "코토네", "all")).isTrue();
        assertThat(ArticleMemberSwap.hasSide(List.of(haveKotone, wantNakyoung), "have")).isTrue();
        assertThat(ArticleMemberSwap.hasSide(List.of(haveKotone), "want")).isFalse();
        assertThat(ArticleMemberSwap.hasSide(List.of(wantNakyoung), "want")).isTrue();

        Artist seoyeon = artist(1L, "윤서연", "#111111");
        Artist hayeon = artist(19L, "정하연", "#222222");
        Artist jiyeon = artist(24L, "지연", "#333333");
        Map<String, Object> summary = ArticleMemberSwap.summarize(
                List.of(haveSeoyeon, haveHayeon), List.of(wantJiyeon, wantDuo),
                List.of(seoyeon, hayeon, jiyeon), true);
        assertThat(summary.get("hasMemberSwap")).isEqualTo(true);
        assertThat(summary.get("isTradeSwap")).isEqualTo(true);
        assertThat(summary.get("haveLeadName")).isEqualTo("윤서연");
        assertThat(summary.get("haveLeadColor")).isEqualTo("#111111");
        assertThat(summary.get("hasHaveMore")).isEqualTo(true);
        assertThat(summary.get("haveMoreCount")).isEqualTo(1);
        assertThat(summary.get("wantLeadName")).isEqualTo("지연");
        assertThat(summary.get("hasWantMore")).isEqualTo(true);
        assertThat(summary.get("wantMoreCount")).isEqualTo(2);

        Map<String, Object> sellWant = ArticleMemberSwap.summarize(
                List.of(), List.of(wantNakyoung), List.of(), false);
        assertThat(sellWant.get("hasWantLead")).isEqualTo(true);
        assertThat(sellWant.get("wantLeadName")).isEqualTo("김나경");
        assertThat(sellWant.get("isTradeSwap")).isEqualTo(false);
    }

    @Test void matchesListingRequiresSeasonClassAndMemberTogether() {
        ArticleObjekt have = link("HAVE", "코토네", "Cream02", "Z");
        ArticleObjekt want = link("WANT", "카에데", "Ever01", "A");
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "", "have", "Cream02", "")).isTrue();
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "", "have", "Ever01", "")).isFalse();
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "코토네", "have", "Cream02", "Z")).isTrue();
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "코토네", "have", "Cream02", "A")).isFalse();
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "카에데", "want", "Ever01", "A")).isTrue();
        assertThat(ArticleMemberSwap.matchesListing(List.of(have, want), "", "all", "Ever01", "A")).isTrue();
    }

    @Test void previewThumbsCapsAndCountsOverflow() {
        ArticleObjekt a = link("HAVE", "코토네", "Cream02", "Z");
        a.setThumbnailImage("https://example/a.jpg");
        a.setCollectionNo("369Z");
        ArticleObjekt b = link("HAVE", "카에데", "Cream02", "Z");
        b.setFrontImage("https://example/b.jpg");
        b.setCollectionNo("370Z");
        ArticleObjekt c = link("HAVE", "마유", "Cream02", "Z");
        c.setCollectionNo("371Z");
        assertThat(ArticleMemberSwap.previewThumbs(List.of(a, b, c), 2)).hasSize(2);
        assertThat(ArticleMemberSwap.previewMoreCount(List.of(a, b, c), 2)).isEqualTo(1);
        assertThat(ArticleMemberSwap.previewThumbs(List.of(a, b, c), 2).get(0).get("hasImage")).isEqualTo(true);
        assertThat(ArticleMemberSwap.previewThumbs(List.of(c), 2).get(0).get("hasImage")).isEqualTo(false);
    }

    private static ArticleObjekt link(String side, String member) {
        return link(side, member, "", "");
    }

    private static ArticleObjekt link(String side, String member, String season, String objektClass) {
        ArticleObjekt row = new ArticleObjekt();
        row.setSide(side);
        row.setMemberName(member);
        row.setSeason(season);
        row.setObjektClass(objektClass);
        return row;
    }

    private static Artist artist(Long id, String name, String color) {
        Artist artist = new Artist();
        artist.setArtistId(id);
        artist.setName(name);
        artist.setEngName(name);
        artist.setColor(color);
        return artist;
    }
}
