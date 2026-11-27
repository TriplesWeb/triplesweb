package com.example.triplesweb.service;

import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.media.MediaHashtags;
import org.springframework.stereotype.Service;
import org.springframework.util.ConcurrentLruCache;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MediaYoutubeCastService {
    private static final int MAX_LOOKUPS_PER_HOUR = 100;

    private final YoutubeDataApiClient youtube;
    private final ConcurrentLruCache<Lookup, List<String>> members = new ConcurrentLruCache<>(2048, this::fetchMembers);
    private Instant requestHour = Instant.EPOCH;
    private int lookupCount;

    public MediaYoutubeCastService(YoutubeDataApiClient youtube) {
        this.youtube = youtube;
    }

    public List<String> findMembers(String youtubeId) {
        if (!youtube.hasApiKey()) return List.of();
        return members.get(new Lookup(youtubeId, Instant.now().truncatedTo(ChronoUnit.HOURS)));
    }

    private List<String> fetchMembers(Lookup lookup) {
        if (!reserveLookup(lookup.hour())) return List.of();
        return MediaHashtags.taggedTripleSMembers(null, youtube.fetchDescription(lookup.youtubeId()));
    }

    private synchronized boolean reserveLookup(Instant hour) {
        if (hour.isAfter(requestHour)) {
            requestHour = hour;
            lookupCount = 0;
        }
        if (lookupCount >= MAX_LOOKUPS_PER_HOUR) return false;
        lookupCount++;
        return true;
    }

    private record Lookup(String youtubeId, Instant hour) {}
}
