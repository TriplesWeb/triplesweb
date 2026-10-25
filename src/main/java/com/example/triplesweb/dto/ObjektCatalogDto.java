package com.example.triplesweb.dto;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Objekt 컬렉션 API 한 건. 엔티티와 화면용 맵으로 바꾼다.
 */
public record ObjektCatalogDto(
        String externalId,
        String createdAt,
        String slug,
        String collectionId,
        String season,
        String member,
        String artist,
        String collectionNo,
        String objektClass,
        String thumbnailImage,
        String frontImage,
        String backImage,
        String backgroundColor,
        String textColor,
        String onOffline,
        String hasAudio,
        String originalFrontImage,
        String originalBackImage,
        String frontMedia,
        String bandImageUrl
) {
    public static ObjektCatalogDto from(JsonNode node) {
        return new ObjektCatalogDto(
                text(node, "id"),
                text(node, "createdAt"),
                text(node, "slug"),
                text(node, "collectionId"),
                text(node, "season"),
                text(node, "member"),
                text(node, "artist"),
                text(node, "collectionNo"),
                text(node, "class"),
                text(node, "thumbnailImage"),
                text(node, "frontImage"),
                text(node, "backImage"),
                text(node, "backgroundColor"),
                text(node, "textColor"),
                text(node, "onOffline"),
                text(node, "hasAudio"),
                text(node, "originalFrontImage"),
                text(node, "originalBackImage"),
                text(node, "frontMedia"),
                text(node, "bandImageUrl")
        );
    }

    public ObjektCatalog toEntity() {
        ObjektCatalog row = new ObjektCatalog();
        row.setExternalId(externalId);
        row.setCreatedAt(createdAt);
        row.setSlug(slug);
        row.setCollectionId(collectionId);
        row.setSeason(season);
        row.setMember(member);
        row.setArtist(artist);
        row.setCollectionNo(collectionNo);
        row.setObjektClass(objektClass);
        row.setThumbnailImage(thumbnailImage);
        row.setFrontImage(frontImage);
        row.setBackImage(backImage);
        row.setBackgroundColor(backgroundColor);
        row.setTextColor(textColor);
        row.setOnOffline(onOffline);
        row.setHasAudio(hasAudio);
        row.setOriginalFrontImage(originalFrontImage);
        row.setOriginalBackImage(originalBackImage);
        row.setFrontMedia(frontMedia);
        row.setBandImageUrl(bandImageUrl);
        row.setSearchText(String.join(" ", collectionId, slug, season, member, artist, collectionNo, objektClass).trim());
        row.setSeasonKey(season.toLowerCase());
        row.setClassKey(objektClass.toLowerCase());
        row.setNumberValue(extractNumber(collectionNo));
        return row;
    }

    public static Map<String, Object> toView(ObjektCatalog row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getExternalId() == null ? "" : row.getExternalId());
        item.put("createdAt", blank(row.getCreatedAt()));
        item.put("slug", blank(row.getSlug()));
        item.put("collectionId", blank(row.getCollectionId()));
        item.put("season", blank(row.getSeason()));
        item.put("member", MemberPortraitOptions.memberName(blank(row.getMember())));
        item.put("artist", blank(row.getArtist()));
        item.put("collectionNo", blank(row.getCollectionNo()));
        item.put("class", blank(row.getObjektClass()));
        item.put("thumbnailImage", blank(row.getThumbnailImage()));
        item.put("frontImage", blank(row.getFrontImage()));
        item.put("backImage", blank(row.getBackImage()));
        item.put("backgroundColor", blank(row.getBackgroundColor()));
        item.put("textColor", blank(row.getTextColor()));
        item.put("onOffline", blank(row.getOnOffline()));
        item.put("hasAudio", blank(row.getHasAudio()));
        item.put("originalFrontImage", blank(row.getOriginalFrontImage()));
        item.put("originalBackImage", blank(row.getOriginalBackImage()));
        item.put("frontMedia", blank(row.getFrontMedia()));
        item.put("bandImageUrl", blank(row.getBandImageUrl()));
        item.put("searchText", blank(row.getSearchText()));
        item.put("seasonKey", blank(row.getSeasonKey()));
        item.put("classKey", blank(row.getClassKey()));
        item.put("numberValue", row.getNumberValue());
        return Map.copyOf(item);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    static int extractNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }
}
