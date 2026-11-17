package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 멤버 프로필에 붙는 OST 참여 카드.
 */
@Getter
@AllArgsConstructor
public class ArtistOstAlbumCard {
    private final Long albumId;
    private final String albumName;
    private final String displayAlbumImage;
}
