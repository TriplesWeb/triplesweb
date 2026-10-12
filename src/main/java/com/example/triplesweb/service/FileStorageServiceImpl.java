package com.example.triplesweb.service;

import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.media.FileStorageException;
import jakarta.annotation.Nullable;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FileStorageServiceImpl implements FileStorageService {

    private final String uploadRoot;
    public FileStorageServiceImpl(@Value("${file.upload-dir}") String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    private static final Set<String> IMAGE_EXT = Set.of("jpg","jpeg","png","gif","webp","bmp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4","mov","m4v","avi","mkv","webm");

    @Override
    public String save(MultipartFile file) {
        return saveFile(file, null, null).getUrl();
    }

    @Override
    public String save(MultipartFile file, MediaType type) {
        return saveFile(file, type, null).getUrl();
    }

    @Override
    public SavedMeta saveWithMeta(MultipartFile file) {
        return saveFile(file, null, null);
    }

    // ✅ 하위폴더 지정 저장
    @Override
    public SavedMeta saveWithMeta(MultipartFile file, @Nullable String subdir) {
        return saveFile(file, null, subdir);
    }

    // ✅ 기존 2-인자 오버로드 유지(내부에서 3-인자로 위임)
    private SavedMeta saveFile(MultipartFile file, @Nullable MediaType forceType) {
        return saveFile(file, forceType, null);
    }

    // ✅ 실제 저장 로직(하위폴더 지원)
    private SavedMeta saveFile(MultipartFile file, @Nullable MediaType forceType, @Nullable String subdir) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("빈 파일 입니다.");

        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String ext = getExtension(originalName);
        String today = LocalDate.now().toString();

        boolean isImage = IMAGE_EXT.contains(ext);
        boolean isVideo = VIDEO_EXT.contains(ext);
        MediaType type = (forceType != null) ? forceType : inferType(isImage, isVideo);

        String typeDir = switch (type) {
            case IMAGE -> "images";
            case VIDEO -> "videos";
            default -> "files";
        };
        Path root = Paths.get(uploadRoot)
                .toAbsolutePath()
                .normalize();

        Path dir = (subdir == null || subdir.isBlank())
                ? root.resolve(typeDir).resolve(today)
                : root.resolve(typeDir).resolve(subdir).resolve(today);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new FileStorageException("업로드 파일 생성 실패: " + dir, e);
        }

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String savedName = ext.isEmpty() ? uuid : uuid + "." + ext;
        Path newPath = dir.resolve(savedName);

        try {
            file.transferTo(newPath.toFile());
        } catch (IOException e) {
            throw new FileStorageException("파일 저장 실패: " + originalName, e);
        }
        writeOriginalNameSidecar(newPath, originalName);
        String url = toPublicUrl(newPath);

        return SavedMeta.builder()
                .originalName(originalName)
                .savedName(savedName)
                .url(url)
                .size(file.getSize())
                .fileType(guessContentType(ext, type))
                .mediaType(type)
                .build();
    }

    @SneakyThrows
    @Override
    public boolean deleteFile(String fileChannel) {
        String relative = normalizeSavedName(fileChannel);
        if (relative == null || relative.isBlank()) return false;

        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        Path sidecar = target.resolveSibling(target.getFileName().toString() + ".orig");
        if (!target.startsWith(root)) throw new FileStorageException("잘못된 경로: " + relative);
        try {
            Files.deleteIfExists(sidecar);
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new FileStorageException("파일 삭제 실패: " + relative, e);
        }
    }

    private String getExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx < 0 || idx == name.length()-1) ? "" : name.substring(idx+1).toLowerCase(Locale.ROOT);
    }
    private MediaType inferType(boolean isImage, boolean isVideo) {
        if (isImage) return MediaType.IMAGE;
        if (isVideo) return MediaType.VIDEO;
        return MediaType.OTHER;
    }
    private String toPublicUrl(Path absolutePath) {
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path relative = root.relativize(absolutePath.toAbsolutePath().normalize());
        return "/uploads/" + relative.toString().replace("\\","/");
    }
    private String normalizeSavedName(String input) {
        if (input == null) return "";
        String s = input.replace("\\","/");
        return s.startsWith("/uploads/") ? s.substring("/uploads/".length()) : s;
    }
    private String guessContentType(String ext, MediaType type) {
        if (type == MediaType.IMAGE) return "image/" + (ext.isEmpty() ? "jpeg" : ext);
        if (type == MediaType.VIDEO) return "video/" + (ext.isEmpty() ? "mp4" : ext);
        return "application/octet-stream";
    }
    @Override
    public String toRelativePath(String fileChannel) {
        // 이미 구현해둔 normalize 로직 재사용
        return normalizeSavedName(fileChannel); // "/uploads/..." 시작하면 접두어 제거
    }
    @Override
    public Path resolvePhysicalPath(String fileChannel) {
        String relative = toRelativePath(fileChannel);
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();

        // 보안상 업로드 루트 밖으로 나가지 못하도록 확인
        if (!target.startsWith(root)) {
            throw new FileStorageException("잘못된 경로: " + fileChannel);
        }
        return target;
    }
    private void writeOriginalNameSidecar(Path savedPath, String originalName) {
        Path sidecar = savedPath.resolveSibling(savedPath.getFileName().toString() + ".orig");
        try {
            Files.writeString(sidecar, originalName == null ? "" : originalName,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // 로깅만 하고 지나가도 됨
        }
    }
    @Override
    public @Nullable String readOriginalName(String fileChannel) {
        String relative = toRelativePath(fileChannel);
        Path p = resolvePhysicalPath(relative);
        Path sidecar = p.resolveSibling(p.getFileName().toString() + ".orig");
        try {
            if (Files.exists(sidecar)) {
                return Files.readString(sidecar).trim();
            }
        } catch (IOException ignored) {}
        return null;
    }

    @Override
    public void deleteSidecar(String fileChannel) {
        String relative = toRelativePath(fileChannel);
        Path p = resolvePhysicalPath(relative);
        Path sidecar = p.resolveSibling(p.getFileName().toString() + ".orig");
        try { Files.deleteIfExists(sidecar); } catch (IOException ignored) {}
    }
}
