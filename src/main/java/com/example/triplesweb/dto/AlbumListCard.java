package com.example.triplesweb.dto;

import com.example.triplesweb.util.ImageUrls;
import lombok.Getter;

/** 앨범 목록 카드용. LONGTEXT albumInfo를 실어 오지 않는다. */
@Getter
public class AlbumListCard {
    private final long albumId;
    private final String albumName;
    private final String displayAlbumImage;
    private final String releaseDate;
    private final String albumFormatLabel;
    private final boolean hasAlbumFormat;
    private final UnitRef unit;
    private final Long sectionId;

    public AlbumListCard(
            Long albumId,
            String albumName,
            String albumImage,
            String releaseDate,
            String albumFormat,
            String unitName,
            Long sectionId
    ) {
        this.albumId = albumId == null ? 0L : albumId;
        this.albumName = albumName == null ? "" : albumName;
        this.displayAlbumImage = ImageUrls.orPlaceholder(albumImage);
        this.releaseDate = releaseDate == null ? "" : releaseDate;
        String format = albumFormat == null ? "" : albumFormat.trim();
        this.hasAlbumFormat = !format.isBlank();
        this.albumFormatLabel = this.hasAlbumFormat ? format : null;
        this.unit = (unitName == null || unitName.isBlank()) ? null : new UnitRef(unitName);
        this.sectionId = sectionId;
    }

    @Getter
    public static final class UnitRef {
        private final String unitName;

        public UnitRef(String unitName) {
            this.unitName = unitName;
        }
    }
}
