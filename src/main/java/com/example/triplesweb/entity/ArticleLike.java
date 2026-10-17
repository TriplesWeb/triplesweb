package com.example.triplesweb.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * 게시글 좋아요 엔티티({@code article_like}). {@link Article} N:1, (article_id, userId) 유일.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "article_like", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"article_id", "userid"})
})
public class ArticleLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false)
    private String userId;

    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Article article;
}
