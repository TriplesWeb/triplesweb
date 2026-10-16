package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Article;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article,Long> {

    List<Article> findByLikesCountGreaterThanEqualOrderByLikesCountDesc(int minLikes, Pageable pageable);

    List<Article> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrUserIdContainingIgnoreCase(
            String title, String content, String userId, Sort sort);
    List<Article> findByUserId(String userId, Sort sort);
    @Query("""
       select distinct a
         from Article a
         left join fetch a.files af
         left join fetch af.file m
        where a.id = :id
       """)
    Optional<Article> findByIdWithFiles(@Param("id") Long id);


    List<Article> findByCategoryIgnoreCaseOrderByIdDesc(String category, Pageable pageable);

    List<Article> findPopularByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT MAX(a.displayId) FROM Article a WHERE a.category = :category")
    Long findMaxDisplayIdByCategory(@Param("category") String category);

    List<Article> findByCategoryOrderByDisplayIdDesc(String category);

}
