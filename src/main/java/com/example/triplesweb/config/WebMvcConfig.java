package com.example.triplesweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 업로드 디렉터리 기동 로그 + 정적 자산 Cache-Control.
 * {@code /uploads/**} 서빙·원격 폴백은 {@link com.example.triplesweb.controller.UploadsController}가 담당한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 비우면 비활성. 로컬/협업자 bootRun에서 공유 DB + 서버 uploads 참조용 */
    @Value("${upload.fallback-base-url:}")
    private String uploadFallbackBaseUrl;

    /**
     * 로컬은 {@code application-local.properties}에서 0으로 두어 src 변경이 바로 보이게 한다.
     * 운영 기본은 7일(초) + immutable. URL {@code ?v=} 해시는 {@link com.example.triplesweb.view.AssetModelAdvice}가 부여한다.
     */
    @Value("${spring.web.resources.cache.period:604800}")
    private long staticCachePeriodSeconds;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl cacheControl = staticCachePeriodSeconds <= 0
                ? CacheControl.noStore()
                : CacheControl.maxAge(staticCachePeriodSeconds, TimeUnit.SECONDS).cachePublic().immutable();
        // file: 을 classpath 보다 앞에 둔다. bootRun 중 src 수정이 옛 build/ 산출물에 가려지지 않게.
        // 운영 jar 에는 src 경로가 없어 자연스럽게 classpath 로 넘어간다.
        registry.addResourceHandler("/css/**")
                .addResourceLocations("file:./src/main/resources/static/css/", "classpath:/static/css/")
                .setCacheControl(cacheControl);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("file:./src/main/resources/static/js/", "classpath:/static/js/")
                .setCacheControl(cacheControl);
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("file:./src/main/resources/static/fonts/", "classpath:/static/fonts/")
                .setCacheControl(cacheControl);
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:./src/main/resources/static/images/", "classpath:/static/images/")
                .setCacheControl(cacheControl);
    }

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
