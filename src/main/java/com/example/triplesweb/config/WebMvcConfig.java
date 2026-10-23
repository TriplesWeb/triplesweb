package com.example.triplesweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code file.upload-dir} 업로드 경로를 {@code /uploads/**} 정적 리소스로 매핑한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 비우면 비활성. 로컬 bootRun에서 공유 DB + 서버 uploads 참조용 (예: https://triples.eht3r.cloud) */
    @Value("${upload.fallback-base-url:}")
    private String uploadFallbackBaseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        Path uploadPath = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();

        String uri = uploadPath.toUri().toString();

        if (!uri.endsWith("/")) {
            uri += "/";
        }

        System.out.println("======================================");
        System.out.println("UPLOAD DIR  = " + uploadDir);
        System.out.println("UPLOAD PATH = " + uploadPath);
        System.out.println("UPLOAD URI  = " + uri);
        System.out.println("BANNER1     = " +
                Files.exists(uploadPath.resolve("images/banner1.jpg")));
        if (StringUtils.hasText(uploadFallbackBaseUrl)) {
            System.out.println("UPLOAD FALLBACK = " + uploadFallbackBaseUrl.trim().replaceAll("/+$", ""));
        }
        System.out.println("======================================");

        var registration = registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uri);
        if (StringUtils.hasText(uploadFallbackBaseUrl)) {
            registration.resourceChain(true)
                    .addResolver(new UploadRemoteFallbackResolver(uploadFallbackBaseUrl.trim()));
        }
    }
}