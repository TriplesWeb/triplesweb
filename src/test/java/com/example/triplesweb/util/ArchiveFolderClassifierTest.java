package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFolderClassifierTest {
    ArchiveFolderClassifier.UnitMembers aaa() {
        return new ArchiveFolderClassifier.UnitMembers(1L, "Acid Angel from Asia",
                Set.of("윤서연", "김유연", "김나경", "공유빈"));
    }

    @Test void twentyFourMembersAreTheGroupFolder() {
        String names = String.join(",", MemberPortraitOptions.koreanOrder());
        var match = ArchiveFolderClassifier.classify(names, List.of(aaa()));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.GROUP);
        assertThat(match.key()).isEqualTo("s");
        assertThat(match.label()).isEqualTo("전체 멤버");
        assertThat(ArchiveFolderClassifier.matches(match, "s", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "s1", List.of())).isFalse();
    }

    @Test void exactUnitSetGoesToThatUnitFolder() {
        var match = ArchiveFolderClassifier.classify("윤서연,김유연,김나경,공유빈", List.of(aaa()));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.UNIT);
        assertThat(match.key()).isEqualTo("unit-1");
        assertThat(match.label()).isEqualTo("Acid Angel from Asia");
        assertThat(ArchiveFolderClassifier.matches(match, "unit-1", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "combo-4", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(match, "combo", List.of("윤서연", "김유연", "김나경", "공유빈"))).isFalse();
    }

    @Test void twoMembersWithoutAMatchingUnitAreThePairChemistryFolder() {
        var seoyeonHyein = ArchiveFolderClassifier.classify("윤서연, 정혜린", List.of(aaa()));
        var chaeyeonYuyeon = ArchiveFolderClassifier.classify("김채연,김유연", List.of(aaa()));
        assertThat(seoyeonHyein.kind()).isEqualTo(ArchiveFolderClassifier.Kind.COMBO);
        assertThat(seoyeonHyein.key()).isEqualTo("combo-2");
        assertThat(seoyeonHyein.label()).isEqualTo("2명 케미");
        assertThat(chaeyeonYuyeon.key()).isEqualTo("combo-2");
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "combo-2", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(chaeyeonYuyeon, "combo-2", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "combo", List.of("정혜린", "윤서연"))).isTrue();
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "combo-2", List.of("윤서연", "정혜린"))).isTrue();
        assertThat(ArchiveFolderClassifier.matches(chaeyeonYuyeon, "combo-2", List.of("윤서연", "정혜린"))).isFalse();
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "combo-3", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.comboSize("combo-2")).isEqualTo(2);
        assertThat(ArchiveFolderClassifier.comboSize("combo")).isNull();
    }

    @Test void largerChemistryGoesOnlyToItsCountFolder() {
        var six = ArchiveFolderClassifier.classify(
                "윤서연,정혜린,이지우,김채연,김유연,김수민", List.of(aaa()));
        assertThat(six.kind()).isEqualTo(ArchiveFolderClassifier.Kind.COMBO);
        assertThat(six.key()).isEqualTo("combo-5");
        assertThat(ArchiveFolderClassifier.matches(six, "combo-2", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(six, "combo-3", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(six, "combo-4", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(six, "combo-5", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(six, "combo-6", List.of())).isTrue();
        var nine = ArchiveFolderClassifier.classify(
                "윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데", List.of(aaa()));
        assertThat(nine.kind()).isEqualTo(ArchiveFolderClassifier.Kind.COMBO);
        assertThat(ArchiveFolderClassifier.matches(nine, "combo-5", List.of())).isTrue();
        var trio = ArchiveFolderClassifier.classify("윤서연,정혜린,이지우", List.of());
        assertThat(trio.key()).isEqualTo("combo-3");
        assertThat(ArchiveFolderClassifier.matches(trio, "combo-2", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(trio, "combo-3", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(trio, "combo-4", List.of())).isFalse();
        var quartet = ArchiveFolderClassifier.classify("윤서연,정혜린,이지우,김채연", List.of());
        assertThat(quartet.key()).isEqualTo("combo-4");
        assertThat(ArchiveFolderClassifier.matches(quartet, "combo-4", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(quartet, "combo-5", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.comboLabel(2)).isEqualTo("2명 케미");
        assertThat(ArchiveFolderClassifier.comboLabel(3)).isEqualTo("3명 케미");
        assertThat(ArchiveFolderClassifier.comboLabel(4)).isEqualTo("4명 케미");
        assertThat(ArchiveFolderClassifier.comboLabel(5)).isEqualTo("5명 이상");
        assertThat(ArchiveFolderClassifier.canonicalComboFolder("combo-6")).isEqualTo("combo-5");
    }

    @Test void soloFolderIsOnlyThatOneMember() {
        var match = ArchiveFolderClassifier.classify("서연", List.of(aaa()));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.SOLO);
        assertThat(match.key()).isEqualTo("s1");
        assertThat(match.label()).isEqualTo("S1 윤서연");
        assertThat(ArchiveFolderClassifier.matches(match, "s1", List.of())).isTrue();
        var group = ArchiveFolderClassifier.classify("윤서연,정혜린", List.of());
        assertThat(ArchiveFolderClassifier.matches(group, "s1", List.of())).isFalse();
    }
}
