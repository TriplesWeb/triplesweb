package com.example.triplesweb.controller;

import com.example.triplesweb.entity.UploadBlob;
import com.example.triplesweb.service.UploadContentStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code /uploads/**} 서빙. 로컬 파일이 없으면 공유 원본(운영) URL로 리다이렉트한다.
 * 공유 DB를 쓰는 로컬·협업자 PC에서도 멤버/유닛 이미지가 보이게 한다.
 */
@RestController
public class UploadsController {

    private final Path uploadRoot;
    private final String fallbackBaseUrl;
    private final UploadContentStore uploadContentStore;

    public UploadsController(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${upload.fallback-base-url:}") String fallbackBaseUrl,
            UploadContentStore uploadContentStore
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fallbackBaseUrl = StringUtils.hasText(fallbackBaseUrl)
                ? fallbackBaseUrl.trim().replaceAll("/+$", "")
                : "";
        this.uploadContentStore = uploadContentStore;
    }

    @RequestMapping(value = "/uploads/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String relative = relativeUploadPath(request);
        if (!StringUtils.hasText(relative)) {
            return ResponseEntity.notFound().build();
        }

        Path local = uploadRoot.resolve(relative).normalize();
        if (!local.startsWith(uploadRoot)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (Files.isRegularFile(local) && Files.isReadable(local)) {
            MediaType type = MediaTypeFactory.getMediaType(local.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(type)
                    .body(new FileSystemResource(local));
        }

        UploadBlob stored = uploadContentStore.find(relative).orElse(null);
        if (stored != null && stored.getData() != null && stored.getData().length > 0) {
            MediaType type = mediaType(stored.getContentType());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(type)
                    .body(new ByteArrayResource(stored.getData()));
        }

        if (!StringUtils.hasText(fallbackBaseUrl)) {
            return ResponseEntity.notFound().build();
        }

        // 운영 서버 자신으로 폴백하면 리다이렉트 루프 → 404
        try {
            String fallbackHost = URI.create(fallbackBaseUrl).getHost();
            if (fallbackHost != null && fallbackHost.equalsIgnoreCase(request.getServerName())) {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException ignored) {
            return ResponseEntity.notFound().build();
        }

        URI location = URI.create(fallbackBaseUrl + "/uploads/" + relative.replace('\\', '/'));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .build();
    }

    private static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String relativeUploadPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String prefix = context + "/uploads/";
        if (!uri.startsWith(prefix)) {
            return "";
        }
        String relative = uri.substring(prefix.length());
        // % 인코딩된 한글 파일명 등
        try {
            relative = java.net.URLDecoder.decode(relative, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep raw
        }
        return relative.replace('\\', '/').replaceFirst("^/+", "");
    }
}
