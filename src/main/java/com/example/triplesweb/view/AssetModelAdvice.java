package com.example.triplesweb.view;

import com.example.triplesweb.service.StaticAssetVersionService;
import com.samskivert.mustache.Mustache;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Mustache에서 {@code {{#asset}}/js/chemistry.js{{/asset}}} → {@code /js/chemistry.js?v=&lt;hash&gt;}.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class AssetModelAdvice {

    private final StaticAssetVersionService staticAssetVersionService;

    @ModelAttribute("asset")
    public Mustache.Lambda asset() {
        return (frag, out) -> out.write(staticAssetVersionService.versionedUrl(frag.execute()));
    }
}
