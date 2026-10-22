package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitAdminDto {
    private Long unitId;
    private String unitName;
    private String smallName;
    private String unitImage;
    private String formationDate;
    private String debutDate;
    private boolean visible;
    private Long parentUnitId;
    private String parentUnitName;
    @Builder.Default
    private List<Long> artistIds = new ArrayList<>();
    @Builder.Default
    private List<MemberOption> members = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberOption {
        private Long artistId;
        private String name;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitOption {
        private Long unitId;
        private String unitName;
    }
}
