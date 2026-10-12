package com.example.triplesweb.controller;

import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.infra.YoutubeOembedClient;
import com.example.triplesweb.util.ISODuration;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaResolveController {
    private final YoutubeOembedClient oembed;
    private final YoutubeDataApiClient yt;

    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(@RequestParam String urlOrId){
        String id = YoutubeIdExtractor.extract(urlOrId);
        if (id.isBlank()) return ResponseEntity.badRequest().body(Map.of("error","INVALID_URL_OR_ID"));

        Map<String,Object> res = new LinkedHashMap<>();
        res.put("youtubeId", id);

        try {
            var oe = oembed.fetch("https://www.youtube.com/watch?v=" + id);
            if (oe!=null){ res.put("title", oe.getTitle()); res.put("thumbnail", oe.getThumbnail_url()); }
        } catch (Exception ignored) {}

        try {
            var vr = yt.fetchVideo(id);
            if (vr!=null && vr.getItems()!=null && vr.getItems().length>0){
                var item = vr.getItems()[0];
                if (item.getSnippet()!=null){
                    res.put("title", item.getSnippet().getTitle());
                    res.put("description", item.getSnippet().getDescription());
                    String pub = item.getSnippet().getPublishedAt();
                    if (pub!=null && pub.length()>=10) res.put("publishedAt", pub.substring(0,10).replace("-","."));
                }
                if (item.getContentDetails()!=null){
                    res.put("duration", ISODuration.toClock(item.getContentDetails().getDuration()));
                }
                if (item.getStatistics()!=null){
                    res.put("views", item.getStatistics().getViewCount());
                }
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(res);
    }
}
