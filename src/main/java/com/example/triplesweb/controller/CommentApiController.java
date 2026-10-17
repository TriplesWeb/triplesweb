package com.example.triplesweb.controller;

import com.example.triplesweb.dto.CommentDto;
import com.example.triplesweb.service.CommentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시글 댓글 REST API(조회·생성·수정·삭제).
 * URL: {@code /api/articles/{articleId}/comments}
 * 레이어: OK — CommentService 사용.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/articles/{articleId}/comments")
public class CommentApiController {
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

        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        dto.setUserId(userId);

        return commentService.create(articleId, dto);
    }

    @PutMapping("{commentId}")
    public ResponseEntity<CommentDto> updateComment(@PathVariable Long commentId,
                                                    @RequestBody CommentDto dto,
                                                    HttpSession session) {
        String userId = (String) session.getAttribute("userId");

        if (userId == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        CommentDto updatedDto = commentService.update(commentId, dto);
        dto.setUserId(userId);

        return ResponseEntity.status(HttpStatus.OK).body(updatedDto);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<CommentDto> deleteComment(
            @PathVariable("commentId") Long commentId,
            HttpSession session) {

        String userId = (String) session.getAttribute("userId");

        if (userId == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        CommentDto deletedDto = commentService.delete(commentId);

        return ResponseEntity.status(HttpStatus.OK).body(deletedDto);
    }
}
