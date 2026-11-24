package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFolderClassifierTest {
    ArchiveFolderClassifier.UnitMembers aaa() {
        return new ArchiveFolderClassifier.UnitMembers(1L, "Acid Angel from Asia",
                Set.of("윤서연", "김유연", "김나경", "공유빈"), "2022-09-25");
    }

    @Test void twentyFourMembersAreTheGroupFolder() {
        String names = String.join(",", MemberPortraitOptions.koreanOrder());
        var match = ArchiveFolderClassifier.classify(names, List.of(aaa()));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.GROUP);
        assertThat(match.key()).isEqualTo("s");
        assertThat(match.label()).isEqualTo("전체 멤버");
        assertThat(ArchiveFolderClassifier.matches(match, "s", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "s", List.of("윤서연", "정혜린"))).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "s1", List.of())).isFalse();
    }

    @Test void exactUnitCastWithoutAssignmentIsChemistry() {
        var match = ArchiveFolderClassifier.classify("윤서연,김유연,김나경,공유빈", List.of(aaa()));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.COMBO);
        assertThat(match.key()).isEqualTo("combo-4");
        assertThat(ArchiveFolderClassifier.matches(match, "unit-1", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(match, "combo-4", List.of())).isTrue();
    }

    @Test void assignedUnitIdGoesToThatUnitFolder() {
        var match = ArchiveFolderClassifier.classify(
                "윤서연,김유연,김나경,공유빈", List.of(aaa()), Set.of(1L));
        assertThat(match.kind()).isEqualTo(ArchiveFolderClassifier.Kind.UNIT);
        assertThat(match.key()).isEqualTo("unit-1");
        assertThat(match.label()).isEqualTo("Acid Angel from Asia");
        assertThat(ArchiveFolderClassifier.matches(match, "unit-1", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "combo-4", List.of())).isFalse();
    }

    @Test void assignedUnitsMatchEveryUnitFolderAndMemberSelection() {
        var kre = new ArchiveFolderClassifier.UnitMembers(2L, "+(KR)ystal Eyes",
                Set.of("윤서연", "이지우", "김채연", "김수민"));
        var units = List.of(aaa(), kre);
        var match = ArchiveFolderClassifier.classify(
                "윤서연,이지우,김채연,김유연,김수민,김나경,공유빈", units, Set.of(1L, 2L));
        assertThat(match.unitIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(ArchiveFolderClassifier.matches(match, "unit-1", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "unit-2", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(match, "unit-3", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(match, "combo-5", List.of())).isFalse();
        assertThat(ArchiveFolderClassifier.matches(match, "s", List.copyOf(kre.members()), units)).isTrue();
    }

    @Test void assignedUnitKeepsUnitFolderEvenBeforeFormation() {
        String cast = "윤서연,김유연,김나경,공유빈";
        var units = List.of(aaa());
        assertThat(ArchiveFolderClassifier.unitIdForExactCast(cast, LocalDate.of(2022, 9, 24), units)).isNull();
        var assigned = ArchiveFolderClassifier.classify(cast, units, Set.of(1L));
        assertThat(ArchiveFolderClassifier.matches(assigned, "unit-1", List.of())).isTrue();
    }

    @Test void exactUnitSetWithoutAssignmentIsNeverUnitFolder() {
        String cast = "윤서연,김유연,김나경,공유빈";
        var unassigned = ArchiveFolderClassifier.classify(cast, List.of(aaa()));
        assertThat(unassigned.kind()).isEqualTo(ArchiveFolderClassifier.Kind.COMBO);
        assertThat(unassigned.key()).isEqualTo("combo-4");
        assertThat(ArchiveFolderClassifier.matches(
                unassigned, "s", List.of(cast.split(",")), List.of(aaa()))).isFalse();
    }

    @Test void unitMemberFolderRequiresExactCastNotSuperset() {
        var kre = new ArchiveFolderClassifier.UnitMembers(2L, "+(KR)ystal Eyes",
                Set.of("윤서연", "이지우", "김채연", "김수민"));
        var acidEyes = new ArchiveFolderClassifier.UnitMembers(3L, "Acid Eyes",
                Set.of("윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈"));
        var units = List.of(aaa(), kre, acidEyes);
        var kreOnly = ArchiveFolderClassifier.classify("윤서연,이지우,김채연,김수민", units, Set.of(2L));
        var acidEyesCast = ArchiveFolderClassifier.classify(
                "윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈", units, Set.of(3L));
        List<String> kreMembers = List.of("윤서연", "이지우", "김채연", "김수민");
        assertThat(kreOnly.kind()).isEqualTo(ArchiveFolderClassifier.Kind.UNIT);
        assertThat(ArchiveFolderClassifier.matches(kreOnly, "s", kreMembers, units)).isTrue();
        assertThat(ArchiveFolderClassifier.matches(acidEyesCast, "s", kreMembers, units)).isFalse();
        // 유닛이 아닌 임의 멤버 선택은 기존처럼 포함(슈퍼셋) 매칭
        assertThat(ArchiveFolderClassifier.matches(acidEyesCast, "s", List.of("윤서연", "정혜린"), units)).isTrue();
    }

    @Test void folderSMemberFilterUsesExactUnitGateForBrowsePaging() {
        // 솔로·비유닛은 DB requireAllMembers 페이징, 유닛 구성과 같으면 후보 축소 후 정확 매칭.
        assertThat(ArchiveFolderClassifier.isExactUnitMemberSet(Set.of("이지우"), List.of(aaa()))).isFalse();
        assertThat(ArchiveFolderClassifier.isExactUnitMemberSet(Set.of("윤서연", "정혜린"), List.of(aaa()))).isFalse();
        assertThat(ArchiveFolderClassifier.isExactUnitMemberSet(
                Set.of("윤서연", "김유연", "김나경", "공유빈"), List.of(aaa()))).isTrue();
    }

    @Test void unitIdForExactCastRespectsFormationAndAssignmentGap() {
        var acidEyes = new ArchiveFolderClassifier.UnitMembers(14L, "Acid Eyes",
                Set.of("윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈"),
                "2022-12-26");
        String cast = "윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈";
        assertThat(ArchiveFolderClassifier.unitIdForExactCast(
                cast, LocalDate.of(2023, 1, 3), List.of(aaa(), acidEyes))).isEqualTo(14L);
        assertThat(ArchiveFolderClassifier.unitIdForExactCast(
                cast, LocalDate.of(2022, 9, 12), List.of(aaa(), acidEyes))).isNull();
        assertThat(ArchiveFolderClassifier.unitIdForExactCast(
                "윤서연,김유연,김나경,공유빈", LocalDate.of(2022, 9, 25), List.of(aaa(), acidEyes)))
                .isEqualTo(1L);
        assertThat(ArchiveFolderClassifier.unitIdForExactCast(
                "윤서연,정혜린", LocalDate.of(2023, 1, 1), List.of(aaa(), acidEyes))).isNull();
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
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "s", List.of("윤서연", "정혜린"))).isTrue();
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "s", List.of("윤서연"))).isTrue();
        assertThat(ArchiveFolderClassifier.matches(seoyeonHyein, "s", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(chaeyeonYuyeon, "s", List.of("윤서연", "정혜린"))).isFalse();
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
        assertThat(ArchiveFolderClassifier.matches(match, "s", List.of())).isTrue();
        var group = ArchiveFolderClassifier.classify("윤서연,정혜린", List.of());
        assertThat(ArchiveFolderClassifier.matches(group, "s1", List.of())).isFalse();
        var untagged = ArchiveFolderClassifier.classify("", List.of());
        assertThat(untagged.kind()).isEqualTo(ArchiveFolderClassifier.Kind.OTHER);
        assertThat(ArchiveFolderClassifier.matches(untagged, "s", List.of())).isTrue();
        assertThat(ArchiveFolderClassifier.matches(untagged, "s", List.of("윤서연"))).isFalse();
    }
}
