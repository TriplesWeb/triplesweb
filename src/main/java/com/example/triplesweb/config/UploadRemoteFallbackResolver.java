package com.example.triplesweb.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.util.List;

/**
 * 로컬 {@code file.upload-dir}에 없는 파일은 운영(또는 다른) 사이트의 /uploads/ 경로에서 제공한다.
 */
class UploadRemoteFallbackResolver extends PathResourceResolver {

    private final String fallbackBaseUrl;

    UploadRemoteFallbackResolver(String fallbackBaseUrl) {
        this.fallbackBaseUrl = fallbackBaseUrl.replaceAll("/+$", "");
    }

    @Override
    protected Resource resolveResourceInternal(
            @Nullable HttpServletRequest request,
            String requestPath,
            List<? extends Resource> locations,
            ResourceResolverChain chain
    ) {
        Resource local = super.resolveResourceInternal(request, requestPath, locations, chain);
        if (local != null && existsAndReadable(local)) {
            return local;
        }
        if (!StringUtils.hasText(fallbackBaseUrl)) {
            return null;
        }
        String path = requestPath.replaceFirst("^/+", "");
        try {
            return new UrlResource(fallbackBaseUrl + "/uploads/" + path);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean existsAndReadable(Resource resource) {
        return resource.exists() && resource.isReadable();
    }
}
