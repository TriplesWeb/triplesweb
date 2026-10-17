package com.example.triplesweb.dto;

import lombok.*;

/** 파일 업로드 API 응답. 저장된 파일 식별·URL·크기 등 클라이언트에 반환. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponse {
    private String originalName;
    private String savedName;
    private String url;
    private long size;
    private String fileType;
}
