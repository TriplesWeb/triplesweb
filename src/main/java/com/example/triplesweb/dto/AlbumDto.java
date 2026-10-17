package com.example.triplesweb.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.*;

/** 앨범 정보 화면용 DTO. 수록곡명 등 표시용 필드 포함. */
@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class AlbumDto {
    @Id
    private Long albumId;

    private String albumName;
    private String albumImage;
    private String releaseDate;
    private String albumInfo;
    private String trackName;
}
