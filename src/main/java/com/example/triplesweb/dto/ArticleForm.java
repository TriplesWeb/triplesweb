package com.example.triplesweb.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 게시글 작성·수정 폼 바인딩. Multipart 첨부를 포함한다.
 * 제목·본문 필수는 ArticleController.validateTitleAndContent에서 검사한다(마켓은 선택). */
@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor

public class ArticleForm {
    private Long id;
    private Long displayId;
    private String userId;

    private String title;
    private String content;
    private String category;
    private List<MultipartFile> files;


    public String getCategoryName() {
        if (category == null) return "기타";
        return switch (category) {
            case "free" -> "자유게시판";
            case "chat" -> "잡담게시판";
            case "trade" -> "교환게시판";
            case "sell" -> "거래게시판";
            case "group-orders" -> "분철게시판";
            case "popular" -> "인기글";
            default -> "기타";
        };
    }
}
