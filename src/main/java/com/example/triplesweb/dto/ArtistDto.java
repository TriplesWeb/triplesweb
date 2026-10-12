package com.example.triplesweb.dto;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class ArtistDto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long artistId;
    private String engName;
    private String nationality;
    private String birthDate;
    private String birthPlace;
    private String DebutDate;
    private String affiliatedUnit;
    private String color;
    private String profileImage;
}
