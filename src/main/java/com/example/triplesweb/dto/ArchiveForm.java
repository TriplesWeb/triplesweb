package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import lombok.*;

/** 아카이브 목록·편집용 DTO. {@link com.example.triplesweb.entity.Archive} ↔ 폼/화면 변환. */
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ArchiveForm {
    private Long id;
    private String title;
    private MediaType mediaType;
    private String savedName;
    private String originalName;

    public static ArchiveForm from(Archive archive) {
        SavedMeta f = archive.getFile();
        MediaType mt = (f != null && f.getMediaType() != null ? f.getMediaType() : archive.getType());
        String saved = (f != null) ? f.getSavedName() : null;
        String original  = (f != null) ? f.getOriginalName() : null;

        return ArchiveForm.builder()
                .id(archive.getId())
                .title(archive.getTitle())
                .mediaType(mt)
                .savedName(saved)
                .originalName(original)
                .build();
    }

    public boolean isImage() {
        return this.mediaType == MediaType.IMAGE;
    }

    public boolean isVideo() {
        return this.mediaType == MediaType.VIDEO;
    }
}
