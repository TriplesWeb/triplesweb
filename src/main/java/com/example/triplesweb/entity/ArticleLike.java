package com.example.triplesweb.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

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
