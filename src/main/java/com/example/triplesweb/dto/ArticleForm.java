package com.example.triplesweb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor

public class ArticleForm {
    private Long id;
    private Long displayId;
    private String userId;

    @NotBlank(message = "제목은 필수 입력 항목입니다.")
    private String title;
    @NotBlank(message = "내용은 필수 입력 항목입니다.")
    private String content;
    private String category;
    private List<MultipartFile> files;
    private int likesCount;
    private boolean liked;


    public String getCategoryName() {
        if (category == null) return "기타";
        return switch (category) {
            case "free" -> "자유게시판";
            case "chat" -> "잡담게시판";
            case "notice" -> "공지사항";
            case "trade" -> "교환게시판";
            case "sell" -> "거래게시판";
            case "info" -> "정보게시판";
            case "popular" -> "인기글";
            default -> "기타";
        };
    }
}
