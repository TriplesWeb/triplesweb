package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ObjektFilterOrderTest {
    @Test void memberNamesAndSNumbersUseOneIdentityAndExtrasStayAfterS24() {
        assertThat(ObjektFilterOrder.sortedMembers(List.of("가", "S1 X S9", "YuBin", "Jiyeon", "SeoYeon", "HyeRin", "Alpha", "Zeta", "나")))
                .containsExactly("윤서연", "정혜린", "공유빈", "지연", "Alpha", "S1 X S9", "Zeta", "가", "나");
        var korean = List.of("윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈",
                "카에데", "서다현", "코토네", "곽연지", "니엔", "박소현", "신위", "마유", "린", "주빈",
                "정하연", "박시온", "김채원", "설린", "서아", "지연");
        for (int i = 0; i < 24; i++) {
            assertThat(MemberPortraitOptions.memberIndex("S" + (i + 1))).isEqualTo(i);
            assertThat(MemberPortraitOptions.memberIndex(korean.get(i))).isEqualTo(i);
        }
        assertThat(MemberPortraitOptions.memberIndex("하연")).isEqualTo(18);
        assertThat(MemberPortraitOptions.memberIndex("S1 윤서연")).isZero();
        assertThat(MemberPortraitOptions.memberIndex("S1 X S9")).isEqualTo(24);
    }

    @Test void seasonsFollowReleaseOrderRatherThanAlphabeticalOrder() {
        assertThat(ObjektFilterOrder.sortedSeasons(List.of("Atom01", "CREAM02", "Divine01", "Binary01", "Ever01", "Cream01", "Atom02", "Binary02", "Other", "Cream01")))
                .containsExactly("CREAM02", "Binary02", "Atom02", "Ever01", "Divine01", "Cream01", "Binary01", "Atom01", "Other");
    }

    @Test void duplicateNumberEnglishAndKoreanAliasesBecomeOneNameOnlyOption() {
        assertThat(ObjektFilterOrder.sortedMembers(List.of("S24", "JiYeon", "지연", "S21", "ChaeWon", "채원", "김채원", "S22", "Sullin", "설린", "S23", "SeoAh", "서아")))
                .containsExactly("김채원", "설린", "서아", "지연");
        assertThat(MemberPortraitOptions.sameMember("김채원", "S21")).isTrue();
        assertThat(MemberPortraitOptions.sameMember("설린", "S22")).isTrue();
        assertThat(MemberPortraitOptions.sameMember("S1 X S9", "윤서연")).isFalse();
    }

    @Test void portraitPickerKeepsTextOnlyEntriesAndDoesNotUseDatabaseIdsAsSNumbers() {
        Artist first = new Artist(); first.setArtistId(99L); first.setName("윤서연"); first.setEngName("seoyeon"); first.setProfileImage("https://example.com/seoyeon.webp");
        Artist last = new Artist(); last.setArtistId(1L); last.setName("지연"); last.setEngName("jiyeon"); last.setProfileImage("https://example.com/jiyeon.webp");
        var rows = MemberPortraitOptions.forObjektFilter(List.of("가", "Jiyeon", "S1 X S9", "SeoYeon"), List.of(last, first));
        assertThat(rows).extracting(row -> row.get("value")).containsExactly("윤서연", "지연", "S1 X S9", "가");
        assertThat(rows.get(0)).containsEntry("frame", 1).containsEntry("textOnly", false);
        assertThat(rows.get(1)).containsEntry("frame", 24);
        assertThat(rows.get(2)).containsEntry("textOnly", true).containsEntry("frame", 0);
        assertThat(MemberPortraitOptions.fromArtists(List.of(last, first), false)).extracting(row -> row.get("value"))
                .containsExactly("윤서연", "지연");
    }
}
