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
    }

    private static ArticleObjekt link(String side, String member) {
        ArticleObjekt row = new ArticleObjekt();
        row.setSide(side);
        row.setMemberName(member);
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
