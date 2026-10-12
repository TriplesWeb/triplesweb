package com.example.triplesweb.service;


import com.example.triplesweb.dto.CommentDto;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Comment;
import com.example.triplesweb.repository.ArticleRepository;
import com.example.triplesweb.repository.CommentRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;

    private final ArticleRepository articleRepository;

    public List<CommentDto> show(Long articleId) {
        return commentRepository.findByArticleId(articleId)
                .stream()
                .map(CommentDto::createCommentDto)
                .collect(Collectors.toList());
    }
    @Transactional
    public CommentDto create(Long articleId, CommentDto dto) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("댓글 생성 실패! "+ "대상 게시글이 없습니다."));
        Comment comment = Comment.createComment(dto,article);
        Comment created = commentRepository.save(comment);
        return CommentDto.createCommentDto(created);
    }
    @Transactional
    public CommentDto update(Long id, CommentDto dto) {
        Comment target = commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("댓글 수정 실패!" + "대상 댓글이 없습니다."));
        target.patch(dto);
        Comment updated = commentRepository.save(target);
        return CommentDto.createCommentDto(updated);
    }
    @Transactional
    public CommentDto delete(Long id) {
        Comment target = commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("댓글 삭제 실패! "+ "대상이 없습니다. "));
        commentRepository.delete(target);
        return CommentDto.createCommentDto(target);
    }
}
