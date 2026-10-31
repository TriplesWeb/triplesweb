// src/main/java/com/example/triplesweb/service/ArticleFileServiceImpl.java
package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleFile;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArticleFileRepository;
import com.example.triplesweb.util.HtmlMediaParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link ArticleFileService} 구현체. 멀티파트·토큰·본문 파싱 경로로 첨부를 게시글에 연결한다.
 * 저장소 메타 구성, 중복 savedName 방지, ID 기준 파일·DB 삭제를 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ArticleFileServiceImpl implements ArticleFileService {

    private final FileStorageService fileStorageService;
    private final ArticleFileRepository articleFileRepository;

    @Override
    public void uploadFile(MultipartFile mf, Article article) throws IOException {
        if (mf == null || mf.isEmpty() || article == null) return;

        String category = StringUtils.hasText(article.getCategory()) ? article.getCategory() : "free";

        // 파일 저장 (저장소가 SavedMeta 리턴) — category는 저장 경로용, DB 컬럼 아님
        SavedMeta saved = fileStorageService.saveWithMeta(mf, category);

        ArticleFile af = ArticleFile.builder()
                .article(article)
                .file(saved)
                .build();

        articleFileRepository.save(af);
        if (article.getFiles() != null) article.getFiles().add(af);
    }

    @Override
    public void attachUploadedFiles(List<String> uploadedFiles, Article article) {
        if (uploadedFiles == null || uploadedFiles.isEmpty() || article == null) return;

        // 해당 게시글에 이미 연결된 savedName 집합 (중복 방지)
        Set<String> existing = Optional.ofNullable(article.getFiles())
                .orElseGet(List::of).stream()
                .filter(f -> f.getFile() != null && StringUtils.hasText(f.getFile().getSavedName()))
                .map(f -> f.getFile().getSavedName())
                .collect(Collectors.toSet());

        for (String token : uploadedFiles) {
            if (!StringUtils.hasText(token)) continue;

            String url = toUploadsUrl(token);
            Path path = tryResolve(url);
            if (path == null) {
                log.warn("첨부 연결 실패(경로 해석 불가): token={}", token);
                continue;
            }
            String relative = fileStorageService.toRelativePath(url);

            String savedName = path.getFileName().toString();
            if (!StringUtils.hasText(savedName) || existing.contains(savedName)) continue;
            if (!Files.exists(path)) {
                log.warn("첨부 연결 실패(파일 없음): token={} path={}", token, path);
                continue;
            }

            long size;
            String contentType;
            try {
                size = Files.size(path);
                contentType = Files.probeContentType(path);
            } catch (IOException e) {
                log.warn("첨부 연결 실패(메타 읽기): token={} err={}", token, e.getMessage());
                continue;
            }
            if (!StringUtils.hasText(contentType)) {
                contentType = guessContentTypeByExt(savedName); // 확장자 기반 fallback
            }

            MediaType mt = contentType.startsWith("video/") ? MediaType.VIDEO
                    : contentType.startsWith("image/") ? MediaType.IMAGE
                    : MediaType.OTHER;

            String originalName = fileStorageService.readOriginalName(url);
            if (!StringUtils.hasText(originalName)) originalName = savedName;

            SavedMeta meta = SavedMeta.builder()
                    .originalName(originalName)
                    .savedName(savedName)
                    .url("/uploads/" + relative.replace("\\", "/"))
                    .size(size)
                    .fileType(contentType)
                    .mediaType(mt)
                    .build();
            ArticleFile af = ArticleFile.builder()
                    .article(article)
                    .file(meta)
                    .build();

            articleFileRepository.save(af);
            if (article.getFiles() != null) article.getFiles().add(af);
            existing.add(savedName);
        }
    }

    private String guessContentTypeByExt(String savedName) {
        String lower = savedName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov"))  return "video/quicktime";
        return "application/octet-stream";
    }

    @Override
    public void extractAndSaveFilesFromContent(Article article) {
        if (article == null || !StringUtils.hasText(article.getContent())) return;

        Set<String> urls = HtmlMediaParser.extractUploadUrls(article.getContent());
        if (urls.isEmpty()) return;

        Set<String> existing = Optional.ofNullable(article.getFiles())
                .orElseGet(List::of).stream()
                .filter(f -> f.getFile() != null && StringUtils.hasText(f.getFile().getSavedName()))
                .map(f -> f.getFile().getSavedName())
                .collect(Collectors.toSet());

        for (String url : urls) {
            Path path = tryResolve(url);
            if (path == null) continue;
            String relative = fileStorageService.toRelativePath(url);
            String fileName = path.getFileName().toString();

            if (!StringUtils.hasText(fileName) || existing.contains(fileName)) continue;
            if (!Files.exists(path)) continue;

            String originalName = fileStorageService.readOriginalName(url);
            if (!StringUtils.hasText(originalName)) originalName = fileName;

            long size;
            String contentType;
            try {
                size = Files.size(path);
                contentType = Files.probeContentType(path);
            } catch (IOException e) {
                continue;
            }
            if (!StringUtils.hasText(contentType)) contentType = guessContentTypeByExt(fileName);

            MediaType mt = contentType.startsWith("video/") ? MediaType.VIDEO
                    : contentType.startsWith("image/") ? MediaType.IMAGE
                    : MediaType.OTHER;

            SavedMeta meta = SavedMeta.builder()
                    .originalName(originalName)
                    .savedName(fileName)
                    .url("/uploads/" + relative.replace("\\", "/"))
                    .size(size)
                    .fileType(contentType)
                    .mediaType(mt)
                    .build();

            ArticleFile af = ArticleFile.builder()
                    .article(article)
                    .file(meta)
                    .build();

            articleFileRepository.save(af);
            if (article.getFiles() != null) article.getFiles().add(af);
            existing.add(fileName);
        }
    }

    @Override
    public void unlinkFiles(Article article, List<Long> fileIds) {
        if (article == null || article.getFiles() == null || fileIds == null || fileIds.isEmpty()) return;
        Set<Long> ids = new HashSet<>(fileIds);
        article.getFiles().removeIf(af -> {
            if (af.getId() == null || !ids.contains(af.getId())) return false;
            deletePhysicalQuietly(af);
            return true;
        });
    }

    @Override
    public void deleteFileById(Long id) {
        articleFileRepository.findById(id).ifPresent(af -> {
            if (af.getArticle() != null && af.getArticle().getFiles() != null) {
                af.getArticle().getFiles().remove(af);
            }
            deletePhysicalQuietly(af);
            articleFileRepository.delete(af);
        });
    }

    private void deletePhysicalQuietly(ArticleFile af) {
        SavedMeta meta = af.getFile();
        if (meta == null || !StringUtils.hasText(meta.getUrl())) return;
        try {
            fileStorageService.deleteFile(meta.getUrl());
        } catch (RuntimeException ex) {
            log.warn("첨부 파일 디스크 삭제 실패 id={}: {}", af.getId(), ex.getMessage());
        }
    }

    private Path tryResolve(String url) {
        if (!StringUtils.hasText(url)) return null;
        try {
            String relative = fileStorageService.toRelativePath(url);
            if (!StringUtils.hasText(relative)) return null;
            return fileStorageService.resolvePhysicalPath(relative);
        } catch (RuntimeException ex) {
            log.warn("첨부 경로를 열 수 없음: {}", ex.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findFileIdsByArticleId(Long articleId) {
        return articleFileRepository.findFileIdsByArticleId(articleId);
    }

    // -------------------- helpers --------------------
    private static String toUploadsUrl(String input) {
        if (!StringUtils.hasText(input)) return null;
        String s = input.trim();
        if (s.startsWith("/uploads/")) return s;
        if (s.startsWith("http")) {
            int idx = s.indexOf("/uploads/");
            return (idx >= 0) ? s.substring(idx) : s;
        }
        return "/uploads/" + s.replaceFirst("^/+", "");
    }

    private static String extractRelativePath(String uploadsUrl) {
        if (!StringUtils.hasText(uploadsUrl)) return null;
        String path = uploadsUrl.replaceFirst("^https?://[^/]+", "");
        int idx = path.indexOf("/uploads/");
        return (idx >= 0) ? path.substring(idx + "/uploads/".length()) : path.replaceFirst("^/+", "");
    }
}
