package com.example.triplesweb.entity;

import com.example.triplesweb.dto.CommentDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * 게시글 댓글 엔티티. {@link Article} N:1.
 */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false,fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    @ToString.Exclude
    private Article article;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date formattedCreatedAt;

    public static Comment createComment(CommentDto dto,Article article) {
        if (dto.getId() != null)
            throw new IllegalArgumentException("댓글 생성 실패! 댓글의 아이디가 없어야합니다. ");
        Comment comment = new Comment();
        comment.setArticle(article);
        comment.setUserId(dto.getUserId());
        comment.setBody(dto.getBody());
        return comment;
    }

    public void patch(CommentDto dto) {
        if (this.id != dto.getId()) {
            throw new IllegalArgumentException("댓글 수정 실패! 잘못된 id가 입력됐습니다.");
        }
        if (dto.getUserId() != null) {
            this.userId = dto.getUserId();
        }
        if (dto.getBody()!=null) {
            this.body = dto.getBody();
        }
    }
}
