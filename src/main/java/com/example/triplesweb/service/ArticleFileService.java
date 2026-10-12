
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ArticleFileService {
    void uploadFile(MultipartFile mf, Article article) throws IOException;
    void attachUploadedFiles(List<String> uploadedFiles, Article article);
    void extractAndSaveFilesFromContent(Article article);
    void deleteFileById(Long id);

    List<Long> findFileIdsByArticleId(Long articleId);
}
