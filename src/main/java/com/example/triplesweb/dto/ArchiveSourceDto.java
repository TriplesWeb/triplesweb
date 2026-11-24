package com.example.triplesweb.dto;

import com.example.triplesweb.entity.ArchiveSource;

/** 아카이브 출처 관리·옵션용 DTO. */
public record ArchiveSourceDto(
        Long id,
        String code,
        String slug,
        String label,
        String color,
        int sortOrder
) {
    public static ArchiveSourceDto from(ArchiveSource source) {
        return new ArchiveSourceDto(
                source.getId(),
                source.getCode(),
                source.getSlug(),
                source.getLabel(),
                source.getColor(),
                source.getSortOrder());
    }
}
