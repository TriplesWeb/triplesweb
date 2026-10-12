package com.example.triplesweb.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.*;

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
