package com.example.triplesweb.util;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemberSetCatalogJsonTest {
    @Test
    void chemistryJsonEscapesAndKeepsExactPair() {
        ChemistryDto pair = ChemistryDto.builder()
                .id(3L)
                .memberA("마유")
                .memberB("주빈")
                .label("심장마빈즈")
                .build();
        String json = MemberSetCatalogJson.chemistry(List.of(pair), List.of());
        assertThat(json).contains("\"label\":\"심장마빈즈\"");
        assertThat(json).contains("/triples/chemistry?pair=3");
        assertThat(json).doesNotContain("<");
    }

    @Test
    void toggleLabelPrefersChemistryThenUnitThenGroup() {
        List<MemberSetCatalogJson.NamedSet> chems = List.of(
                new MemberSetCatalogJson.NamedSet("심장마빈즈", List.of("마유", "주빈")));
        List<MemberSetCatalogJson.NamedSet> units = List.of(
                new MemberSetCatalogJson.NamedSet("EVOLution", List.of("김유연", "김나경", "공유빈", "카에데")));
        assertThat(MemberSetCatalogJson.toggleLabel(List.of(), chems, units)).isEqualTo("멤버 선택");
        assertThat(MemberSetCatalogJson.toggleLabel(List.of("마유"), chems, units)).isEqualTo("마유");
        assertThat(MemberSetCatalogJson.toggleLabel(List.of("주빈", "마유"), chems, units)).isEqualTo("심장마빈즈");
        assertThat(MemberSetCatalogJson.toggleLabel(List.of("김나경", "공유빈", "카에데", "김유연"), chems, units))
                .isEqualTo("EVOLution");
        assertThat(MemberSetCatalogJson.toggleLabel(List.of("서연", "유연"), chems, units)).isEqualTo("2명");
        assertThat(MemberSetCatalogJson.toggleLabel(MemberPortraitOptions.koreanOrder(), chems, units))
                .isEqualTo("단체 콘텐츠");
    }

    @Test
    void unitsJsonUsesCheckboxOptions() {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("unitName", "LOVElution");
        option.put("unitId", 7L);
        option.put("membersCsv", "김유연,김나경");
        String json = MemberSetCatalogJson.units(List.of(option));
        assertThat(json).contains("\"name\":\"LOVElution\"");
        assertThat(json).contains("\"unitId\":7");
        assertThat(json).contains("김유연");
    }
}
