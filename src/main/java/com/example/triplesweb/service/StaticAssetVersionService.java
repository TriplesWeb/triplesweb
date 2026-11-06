package com.example.triplesweb.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 정적 자산 URL에 파일 내용 해시 쿼리를 붙여 수동 {@code ?v=} 없이 캐시를 무효화한다.
 * 로컬 {@code src/main/resources/static} 변경은 mtime이 바뀌면 새 해시가 난다.
 */
@Service
public class StaticAssetVersionService {

    private static final HexFormat HEX = HexFormat.of();

    /** path → (mtimeOrZero + ":" + hash8) */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String versionedUrl(String rawPath) {
        String path = normalizePath(rawPath);
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String hash = hashFor(path);
        return "/" + path + "?v=" + hash;
    }

    private String normalizePath(String rawPath) {
        String path = String.valueOf(rawPath == null ? "" : rawPath).trim();
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private String hashFor(String relativePath) {
        Path file = Paths.get("src/main/resources/static").resolve(relativePath).normalize();
        if (Files.isRegularFile(file)) {
            try {
                long mtime = Files.getLastModifiedTime(file).toMillis();
                String key = relativePath + "@file@" + mtime;
                return cache.computeIfAbsent(key, ignored -> sha8(Files.readAllBytes(file)));
            } catch (Exception e) {
                return "dev";
            }
        }
        return cache.computeIfAbsent(relativePath + "@cp", ignored -> hashClasspath(relativePath));
    }

    private String hashClasspath(String relativePath) {
        try {
            Resource resource = new ClassPathResource("static/" + relativePath);
            if (!resource.exists()) {
                return "missing";
            }
            try (InputStream in = resource.getInputStream()) {
                return sha8(in.readAllBytes());
            }
        } catch (Exception e) {
            return "err";
        }
    }

    private static String sha8(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HEX.formatHex(digest, 0, 4);
        } catch (Exception e) {
            return Integer.toHexString(bytes.length);
        }
    }
}
