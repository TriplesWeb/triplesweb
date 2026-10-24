package com.example.triplesweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 업로드 디렉터리 기동 로그.
 * {@code /uploads/**} 서빙·원격 폴백은 {@link com.example.triplesweb.controller.UploadsController}가 담당한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 비우면 비활성. 로컬/협업자 bootRun에서 공유 DB + 서버 uploads 참조용 */
    @Value("${upload.fallback-base-url:}")
    private String uploadFallbackBaseUrl;

    @jakarta.annotation.PostConstruct
    void logUploadConfig() {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        System.out.println("======================================");
        System.out.println("UPLOAD DIR  = " + uploadDir);
        System.out.println("UPLOAD PATH = " + uploadPath);
        System.out.println("BANNER1     = " + Files.exists(uploadPath.resolve("images/banner1.jpg")));
        if (StringUtils.hasText(uploadFallbackBaseUrl)) {
            System.out.println("UPLOAD FALLBACK = " + uploadFallbackBaseUrl.trim().replaceAll("/+$", ""));
        } else {
            System.out.println("UPLOAD FALLBACK = (disabled)");
        }
        System.out.println("======================================");
    }
}
