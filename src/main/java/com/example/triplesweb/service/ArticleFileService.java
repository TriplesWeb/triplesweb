
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 게시글 첨부 파일({@link com.example.triplesweb.entity.ArticleFile}) 업로드·연결·삭제 계약.
 * 사전 업로드 토큰 연결, 본문 HTML에서 미디어 URL 추출 저장을 포함한다.
 */
public interface ArticleFileService {
    void uploadFile(MultipartFile mf, Article article) throws IOException;
    void attachUploadedFiles(List<String> uploadedFiles, Article article);
    void extractAndSaveFilesFromContent(Article article);
    /** 컬렉션에서 빼고 디스크 파일만 지운다. DB 행은 orphanRemoval 로 삭제된다. */
    void unlinkFiles(Article article, List<Long> fileIds);
    void deleteFileById(Long id);

    List<Long> findFileIdsByArticleId(Long articleId);
}
