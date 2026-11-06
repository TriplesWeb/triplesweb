package com.example.triplesweb.view;

import com.example.triplesweb.service.StaticAssetVersionService;
import com.samskivert.mustache.Mustache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Mustache에서 {@code {{#asset}}/js/chemistry.js{{/asset}}} → {@code /js/chemistry.js?v=&lt;hash&gt;}.
 * {@code @WebMvcTest} 슬라이스에 서비스 빈이 없어도 동작하도록 {@link ObjectProvider}를 쓴다.
 */
@ControllerAdvice
public class AssetModelAdvice {

    private final StaticAssetVersionService staticAssetVersionService;

    public AssetModelAdvice(ObjectProvider<StaticAssetVersionService> provider) {
        this.staticAssetVersionService = provider.getIfAvailable(StaticAssetVersionService::new);
    }

    @ModelAttribute("asset")
    public Mustache.Lambda asset() {
        return (frag, out) -> out.write(staticAssetVersionService.versionedUrl(frag.execute()));
    }
}
