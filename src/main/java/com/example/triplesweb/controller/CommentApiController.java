package com.example.triplesweb.controller;

import com.example.triplesweb.dto.CommentDto;
import com.example.triplesweb.service.CommentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시글 댓글 REST API(조회·생성·수정·삭제).
 * URL: {@code /api/articles/{articleId}/comments}
 * 로그인 기능 제거에 맞춰 작성자는 기본 {@code 익명}으로 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/articles/{articleId}/comments")
public class CommentApiController {
    private static final String ANON_USER = "익명";

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> showComment(
            @PathVariable("articleId") Long articleId) {
        return commentService.show(articleId);
    }

    @PostMapping
    public CommentDto createComment(@PathVariable("articleId") Long articleId,
                                    @RequestBody CommentDto dto,
                                    HttpSession session) {
        dto.setUserId(resolveUserId(dto.getUserId(), session));
        return commentService.create(articleId, dto);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentDto> updateComment(@PathVariable("articleId") Long articleId,
                                                    @PathVariable("commentId") Long commentId,
                                                    @RequestBody CommentDto dto,
                                                    HttpSession session) {
        dto.setId(commentId);
        dto.setUserId(resolveUserId(dto.getUserId(), session));
        CommentDto updatedDto = commentService.update(commentId, dto);
        return ResponseEntity.status(HttpStatus.OK).body(updatedDto);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<CommentDto> deleteComment(
            @PathVariable("articleId") Long articleId,
            @PathVariable("commentId") Long commentId,
            HttpSession session) {
        CommentDto deletedDto = commentService.delete(commentId);
        return ResponseEntity.status(HttpStatus.OK).body(deletedDto);
    }

    private static String resolveUserId(String requested, HttpSession session) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        Object sessionUser = session != null ? session.getAttribute("userId") : null;
        if (sessionUser instanceof String s && StringUtils.hasText(s)) {
            return s.trim();
        }
        return ANON_USER;
    }
}
