package com.example.triplesweb.controller;

import com.example.triplesweb.entity.UploadBlob;
import com.example.triplesweb.service.UploadContentStore;
import com.example.triplesweb.service.UploadImageResizeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.context.request.ServletWebRequest;

import java.net.URI;
import java.io.IOException;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code /uploads/**} 서빙. 로컬 파일이 없으면 공유 원본(운영) URL로 리다이렉트한다.
 * 공유 DB를 쓰는 로컬·협업자 PC에서도 멤버/유닛 이미지가 보이게 한다.
 */
@RestController
public class UploadsController {

    private static final Pattern UUID_FILENAME = Pattern.compile(
            "[0-9a-f]{32}(?:\\.card-[0-9]+\\.jpg|\\.[^.]+)?", Pattern.CASE_INSENSITIVE);

    private final Path uploadRoot;
    private final Path uploadRootReal;
    private final String fallbackBaseUrl;
    private final UploadContentStore uploadContentStore;
    private final UploadImageResizeService imageResizeService;

    public UploadsController(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${upload.fallback-base-url:}") String fallbackBaseUrl,
            UploadContentStore uploadContentStore,
            UploadImageResizeService imageResizeService
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path real = this.uploadRoot;
        try {
            Files.createDirectories(this.uploadRoot);
            real = this.uploadRoot.toRealPath();
        } catch (IOException ignored) {
            // 루트가 아직 없으면 normalize 경로로 심볼릭 링크 검사를 한다.
        }
        this.uploadRootReal = real;
        this.fallbackBaseUrl = StringUtils.hasText(fallbackBaseUrl)
                ? fallbackBaseUrl.trim().replaceAll("/+$", "")
                : "";
        this.uploadContentStore = uploadContentStore;
        this.imageResizeService = imageResizeService;
    }

    @RequestMapping(value = "/uploads/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Resource> serve(HttpServletRequest request, HttpServletResponse response) {
        String relative = relativeUploadPath(request);
        if (!StringUtils.hasText(relative)) {
            return ResponseEntity.notFound().build();
        }

        if (relative.toLowerCase(Locale.ROOT).endsWith(".orig")) {
            return ResponseEntity.notFound().build();
        }
        Path local = uploadRoot.resolve(relative).normalize();
        if (!local.startsWith(uploadRoot)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (Files.isRegularFile(local) && Files.isReadable(local)) {
            try {
                if (!local.toRealPath().startsWith(uploadRootReal)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            } catch (IOException e) {
                return ResponseEntity.notFound().build();
            }
            return fileResponse(maybeCardVariant(local, request), local, request, response);
        }

        UploadBlob stored = uploadContentStore.find(relative).orElse(null);
        if (stored != null && stored.getData() != null && stored.getData().length > 0) {
            // 이후 요청은 디스크에서 바로 서빙하도록 DB 히트를 디스크에 캐시한다.
            try {
                Path parent = local.getParent();
                if (parent != null) Files.createDirectories(parent);
                if (!Files.exists(local)) {
                    Files.write(local, stored.getData());
                }
                if (Files.isRegularFile(local) && Files.isReadable(local)
                        && local.toRealPath().startsWith(uploadRootReal)) {
                    return fileResponse(maybeCardVariant(local, request), local, request, response);
                }
            } catch (IOException ignored) {
                // 디스크 캐시 실패 시 메모리 응답으로 이어간다.
            }
            MediaType type = imageType(relative, mediaType(stored.getContentType()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl(local))
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, safeDisposition(type))
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

    private ResponseEntity<Resource> fileResponse(Path preferred, Path fallback,
                                                  HttpServletRequest request, HttpServletResponse response) {
        Path local = preferred != null ? preferred : fallback;
        String cacheControl = cacheControl(fallback);
        long lastModified;
        try {
            lastModified = Files.getLastModifiedTime(local).toMillis();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
        if (new ServletWebRequest(request, response).checkNotModified(lastModified)) {
            return ResponseEntity.status(response.getStatus())
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                    .lastModified(lastModified)
                    .build();
        }

        MediaType type = imageType(local.getFileName().toString(), MediaTypeFactory.getMediaType(local.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .lastModified(lastModified)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, safeDisposition(type))
                .contentType(type)
                .body(new FileSystemResource(local));
    }

    private static String cacheControl(Path upload) {
        return UUID_FILENAME.matcher(upload.getFileName().toString()).matches()
                ? "public, max-age=31536000, immutable"
                : "public, max-age=86400";
    }

    private Path maybeCardVariant(Path local, HttpServletRequest request) {
        Integer width = parseCardWidth(request.getParameter("w"));
        if (width == null || imageResizeService == null) return null;
        try {
            Path variant = imageResizeService.ensureJpegVariant(local, width);
            if (variant == null) return null;
            if (!variant.toRealPath().startsWith(uploadRootReal)) return null;
            return variant;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Integer parseCardWidth(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            int width = Integer.parseInt(raw.trim());
            return UploadImageResizeService.isAllowedWidth(width) ? width : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String safeDisposition(MediaType type) {
        // 업로드한 HTML/SVG/XML 등은 사이트 권한으로 실행되지 않게 다운로드한다.
        return Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
                "video/mp4", "video/webm", "video/quicktime", "audio/mpeg", "audio/ogg")
                .contains(type.toString().toLowerCase(Locale.ROOT)) ? "inline" : "attachment";
    }

    private static MediaType imageType(String filename, MediaType fallback) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jfif")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return fallback;
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
