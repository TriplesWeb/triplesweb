package com.example.triplesweb.dto;

import lombok.*;

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
