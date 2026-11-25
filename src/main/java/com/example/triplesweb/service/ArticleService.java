
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 게시글({@link Article}) 생성·조회·삭제 및 카테고리별 목록 계약.
 * 첨부 포함 조회를 제공한다.
 */
public interface ArticleService {
    Article create(Article article);
    Article update(Article article);
    /**
     * 제목·본문과 첨부 교체를 한 트랜잭션에서 저장한다.
     * 삭제된 첨부를 컬렉션에 남기면 cascade 가 지운 엔티티를 다시 저장하며 500이 난다.
     */
    Article updateWithFiles(Long id, String title, String content,
                            List<MultipartFile> multipartFiles,
                            List<String> uploadedFiles,
                            List<Long> deletedFileIds) throws IOException;
    Article show(Long id);
    List<Article> findRecentByCategory(String category, int limit);
    List<Article> searchByCategory(String category, String type, String keyword, int limit);
    List<Article> searchLatestByCategory(String category, String keyword, int limit);
    List<Article> findCategoryBatchBefore(String category, String type, String keyword, long beforeId, int size);
    Article delete(Long id);

    Article showWithFiles(Long id);
}
