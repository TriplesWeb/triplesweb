package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 미디어·스케줄에서 연결된 멤버를 표시하기 위한 간단한 참조 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistTagDto {
    private Long artistId;
    private String name;
    private String engName;
}
