package com.example.triplesweb.infra;

import com.example.triplesweb.util.ISODuration;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YouTube Data API v3로 동영상 snippet·재생시간·조회수와 채널 업로드 목록을 조회한다(외부 API, DB 미사용).
 */
@Slf4j
@Component
public class YoutubeDataApiClient {
    private static final String VIDEOS = "https://www.googleapis.com/youtube/v3/videos";
    private static final String CHANNELS = "https://www.googleapis.com/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS = "https://www.googleapis.com/youtube/v3/playlistItems";
    private static final String SEARCH = "https://www.googleapis.com/youtube/v3/search";
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PLAYLIST_PAGES = 500;
    private static final int MAX_SEARCH_PAGES = 10;
    private static final Duration SEARCH_PAUSE = Duration.ofMinutes(30);

    private final RestTemplate rest;
    private final RestTemplate syncRest;
    private final Map<String, String> cachedUploadsPlaylists = new ConcurrentHashMap<>();
    private final Map<String, String> cachedChannelIds = new ConcurrentHashMap<>();
    private volatile Instant searchRetryAt = Instant.EPOCH;

    @Value("${youtube.api.key:}")
    private String apiKey;

    public YoutubeDataApiClient() {
        this.rest = restTemplate(2_000, 3_000);
        this.syncRest = restTemplate(10_000, 30_000);
    }

    YoutubeDataApiClient(RestTemplate rest) {
        this.rest = rest;
        this.syncRest = rest;
    }

    public boolean hasApiKey() {
        return !apiKey().isEmpty();
    }

    /** 환경 변수에 붙은 공백·개행은 URI 쿼리에서 허용되지 않는다. */
    private String apiKey() {
        return apiKey == null ? "" : apiKey.strip();
    }

    /**
     * 조회수와 함께, 비어 있는 공개일·길이를 채울 수 있도록 snippet·contentDetails를 같은 요청에서 읽는다.
     * 한 요청에 최대 50개이며, 키가 없거나 호출이 실패하면 빈 맵을 반환한다.
     */
    public Map<String, LiveVideo> fetchLive(Collection<String> videoIds) {
        Map<String, LiveVideo> live = new LinkedHashMap<>();
        for (ChannelVideo video : fetchChannelVideos(videoIds, rest)) {
            live.put(video.id(), new LiveVideo(video.viewCount(), video.publishedOn(), video.duration()));
        }
        return live;
    }

    /**
     * 영상 ID의 현재 조회수를 가져온다. 한 요청에 최대 50개이며, 키가 없거나 호출이 실패하면 빈 맵을 반환한다.
     */
    public Map<String, Long> fetchViewCounts(Collection<String> videoIds) {
        Map<String, Long> counts = new LinkedHashMap<>();
        fetchLive(videoIds).forEach((id, video) -> {
            if (video.viewCount() != null) counts.put(id, video.viewCount());
        });
        return counts;
    }

    /**
     * 핸들로 업로드 플레이리스트를 찾고, 실패하면 채널 ID로 다시 찾는다. 성공한 ID는 프로세스 동안 재사용한다.
     */
    public String resolveUploadsPlaylist(String handle, String channelId) {
        if (!hasApiKey()) return null;
        String key = (handle == null ? "" : handle.trim()) + "|" + (channelId == null ? "" : channelId.trim());
        String cached = cachedUploadsPlaylists.get(key);
        if (cached != null) return cached;
        String playlist = fetchUploadsByHandle(handle);
        if (playlist == null || playlist.isBlank()) playlist = fetchUploadsByChannelId(channelId);
        if (playlist == null || playlist.isBlank()) return null;
        cachedUploadsPlaylists.put(key, playlist);
        return playlist;
    }

    /**
     * 업로드 플레이리스트의 video ID. {@code maxPages}가 1 이상이면 그 페이지만, 아니면 채널 전체를 읽는다.
     * 첫 페이지 요청이 실패하면 null.
     */
    public List<String> listUploadedVideoIds(String playlistId, int maxPages) {
        if (!hasApiKey() || playlistId == null || playlistId.isBlank()) return null;
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        int pages = 0;
        int limit = maxPages > 0 ? Math.min(maxPages, MAX_PLAYLIST_PAGES) : MAX_PLAYLIST_PAGES;
        do {
            PlaylistItemsResponse response = requestPlaylistItems(playlistId, pageToken);
            if (response == null) return pages == 0 ? null : ids;
            pages++;
            if (response.getItems() != null) {
                for (PlaylistItemsResponse.Item item : response.getItems()) {
                    if (item == null || item.getContentDetails() == null) continue;
                    String videoId = item.getContentDetails().getVideoId();
                    if (videoId != null && videoId.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(videoId);
                }
            }
            pageToken = response.getNextPageToken();
            if (pageToken == null || pageToken.isBlank()) break;
        } while (pages < limit);
        return ids;
    }

    /**
     * 핸들(@name) 또는 채널 ID(UC…)를 채널 ID로 바꾼다. 성공한 값은 프로세스 동안 재사용한다.
     */
    public String resolveChannelId(String handleOrId) {
        if (!hasApiKey() || handleOrId == null || handleOrId.isBlank()) return null;
        String trimmed = handleOrId.trim();
        if (trimmed.matches("^UC[a-zA-Z0-9_-]{22}$")) return trimmed;
        String handle = normalizeHandle(trimmed);
        if (handle.isEmpty()) return null;
        String cached = cachedChannelIds.get(handle.toLowerCase(Locale.ROOT));
        if (cached != null && !cached.isBlank()) return cached;
        URI uri = UriComponentsBuilder.fromUriString(CHANNELS)
                .queryParam("part", "id")
                .queryParam("forHandle", handle)
                .queryParam("key", apiKey())
                .encode()
                .build()
                .toUri();
        ChannelsResponse response = get(syncRest, uri, ChannelsResponse.class);
        if (response == null || response.getItems() == null || response.getItems().length == 0) return null;
        ChannelsResponse.Item item = response.getItems()[0];
        if (item == null || item.getId() == null || item.getId().isBlank()) return null;
        String id = item.getId().trim();
        cachedChannelIds.put(handle.toLowerCase(Locale.ROOT), id);
        return id;
    }

    /**
     * 검색 API로 영상 ID를 모은다. {@code channelId}가 있으면 그 채널 안에서만 찾는다.
     * {@code maxPages}가 1 이상이면 그 페이지만, 아니면 한 페이지만 읽는다.
     */
    public List<String> searchVideoIds(String query, String channelId, int maxPages) {
        if (!hasApiKey() || query == null || query.isBlank()) return List.of();
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        int pages = 0;
        int limit = maxPages > 0 ? Math.min(maxPages, MAX_SEARCH_PAGES) : 1;
        do {
            SearchResponse response = requestSearch(query, channelId, pageToken);
            if (response == null) return pages == 0 ? List.of() : ids;
            pages++;
            if (response.getItems() != null) {
                for (SearchResponse.Item item : response.getItems()) {
                    if (item == null || item.getId() == null) continue;
                    String videoId = item.getId().getVideoId();
                    if (videoId != null && videoId.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(videoId);
                }
            }
            pageToken = response.getNextPageToken();
            if (pageToken == null || pageToken.isBlank()) break;
        } while (pages < limit);
        return ids;
    }

    /** 채널 동기화용. snippet·길이·조회수·라이브 여부를 50개씩 읽는다. */
    public List<ChannelVideo> fetchChannelVideos(Collection<String> videoIds) {
        return fetchChannelVideos(videoIds, syncRest);
    }

    private List<ChannelVideo> fetchChannelVideos(Collection<String> videoIds, RestTemplate template) {
        if (!hasApiKey() || videoIds == null || videoIds.isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (String raw : new LinkedHashSet<>(videoIds)) {
            if (raw != null && raw.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(raw);
        }
        List<ChannelVideo> videos = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += PAGE_SIZE) {
            List<String> chunk = ids.subList(offset, Math.min(offset + PAGE_SIZE, ids.size()));
            VideosResponse response = requestVideos(chunk, template);
            if (response == null || response.getItems() == null) continue;
            for (VideosResponse.Item item : response.getItems()) {
                ChannelVideo video = ChannelVideo.from(item);
                if (video != null) videos.add(video);
            }
        }
        return videos;
    }

    private String fetchUploadsByHandle(String handle) {
        String trimmed = normalizeHandle(handle);
        if (trimmed.isEmpty()) return null;
        URI uri = UriComponentsBuilder.fromUriString(CHANNELS)
                .queryParam("part", "contentDetails")
                .queryParam("forHandle", trimmed)
                .queryParam("key", apiKey())
                .encode()
                .build()
                .toUri();
        return uploadsPlaylist(get(syncRest, uri, ChannelsResponse.class));
    }

    private String fetchUploadsByChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) return null;
        URI uri = UriComponentsBuilder.fromUriString(CHANNELS)
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId.trim())
                .queryParam("key", apiKey())
                .encode()
                .build()
                .toUri();
        return uploadsPlaylist(get(syncRest, uri, ChannelsResponse.class));
    }

    private SearchResponse requestSearch(String query, String channelId, String pageToken) {
        if (Instant.now().isBefore(searchRetryAt)) return null;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(SEARCH)
                .queryParam("part", "snippet")
                .queryParam("type", "video")
                .queryParam("maxResults", PAGE_SIZE)
                .queryParam("order", "date")
                .queryParam("q", query)
                .queryParam("key", apiKey());
        if (channelId != null && !channelId.isBlank()) builder.queryParam("channelId", channelId.trim());
        if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
        try {
            return syncRest.getForObject(builder.encode().build().toUri(), SearchResponse.class);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 429) {
                Instant now = Instant.now();
                Instant retryAt = now.plus(SEARCH_PAUSE);
                HttpHeaders headers = error.getResponseHeaders();
                Instant retryAfter = parseRetryAfter(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER), now);
                searchRetryAt = retryAfter.isAfter(retryAt) ? retryAfter : retryAt;
                log.warn("YouTube search.list returned HTTP 429. Search paused until {}.", searchRetryAt);
            } else {
                log.warn("YouTube search.list returned HTTP {}.", error.getStatusCode().value());
            }
            return null;
        } catch (RestClientException ignored) {
            return null;
        }
    }

    private static Instant parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) return now;
        try {
            return now.plusSeconds(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException invalidHeader) {
                return now;
            }
        }
    }

    /** The generated channel format playlists are optional; a missing membership is never format evidence. */
    private static String uploadFormatPlaylist(String channelId, boolean shorts) {
        if (channelId == null || !channelId.matches("UC[A-Za-z0-9_-]{22}")) return null;
        return (shorts ? "UUSH" : "UULF") + channelId.substring(2);
    }

    public List<String> listUploadFormatVideoIds(String channelId, boolean shorts, int maxPages) {
        return listUploadedVideoIds(uploadFormatPlaylist(channelId, shorts), maxPages);
    }

    public boolean hasUploadFormat(String channelId, String videoId, boolean shorts) {
        String playlist = uploadFormatPlaylist(channelId, shorts);
        if (!hasApiKey() || playlist == null || videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return false;
        URI uri = UriComponentsBuilder.fromUriString(PLAYLIST_ITEMS)
                .queryParam("part", "contentDetails")
                .queryParam("playlistId", playlist)
                .queryParam("videoId", videoId)
                .queryParam("maxResults", 1)
                .queryParam("key", apiKey())
                .encode().build().toUri();
        PlaylistItemsResponse response = get(syncRest, uri, PlaylistItemsResponse.class);
        if (response == null || response.getItems() == null) return false;
        for (PlaylistItemsResponse.Item item : response.getItems()) {
            if (item != null && item.getContentDetails() != null
                    && videoId.equals(item.getContentDetails().getVideoId())) return true;
        }
        return false;
    }

    private PlaylistItemsResponse requestPlaylistItems(String playlistId, String pageToken) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(PLAYLIST_ITEMS)
                .queryParam("part", "contentDetails")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", PAGE_SIZE)
                .queryParam("key", apiKey());
        if (pageToken != null && !pageToken.isBlank()) builder.queryParam("pageToken", pageToken);
        return get(syncRest, builder.encode().build().toUri(), PlaylistItemsResponse.class);
    }

    private VideosResponse requestVideos(List<String> ids, RestTemplate template) {
        try {
            String url = VIDEOS
                    + "?part=snippet,contentDetails,statistics&id=" + String.join(",", ids)
                    + "&key=" + apiKey();
            return get(template, URI.create(url), VideosResponse.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String uploadsPlaylist(ChannelsResponse response) {
        if (response == null || response.getItems() == null || response.getItems().length == 0) return null;
        ChannelsResponse.Item item = response.getItems()[0];
        if (item == null || item.getContentDetails() == null || item.getContentDetails().getRelatedPlaylists() == null) {
            return null;
        }
        String uploads = item.getContentDetails().getRelatedPlaylists().getUploads();
        return uploads == null || uploads.isBlank() ? null : uploads.trim();
    }

    private static String normalizeHandle(String handle) {
        if (handle == null) return "";
        String trimmed = handle.trim();
        if (trimmed.startsWith("@")) trimmed = trimmed.substring(1);
        return trimmed;
    }

    private static String publishedDay(VideosResponse.Item item) {
        if (item == null || item.getSnippet() == null || item.getSnippet().getPublishedAt() == null) return null;
        String raw = item.getSnippet().getPublishedAt().trim();
        if (raw.length() < 10 || raw.charAt(4) != '-') return null;
        return raw.substring(0, 10);
    }

    private static String clock(VideosResponse.Item item) {
        if (item == null || item.getContentDetails() == null) return null;
        String clock = ISODuration.toClock(item.getContentDetails().getDuration());
        return clock == null || clock.isBlank() || "0:00".equals(clock) ? null : clock;
    }

    public record LiveVideo(Long viewCount, String publishedOn, String duration) {}

    public record ChannelVideo(
            String id,
            String title,
            String publishedOn,
            String duration,
            String isoDuration,
            Long viewCount,
            String liveBroadcastContent,
            String channelId,
            String channelTitle,
            String description
    ) {
        public ChannelVideo(
                String id,
                String title,
                String publishedOn,
                String duration,
                String isoDuration,
                Long viewCount,
                String liveBroadcastContent
        ) {
            this(id, title, publishedOn, duration, isoDuration, viewCount, liveBroadcastContent, "", "", "");
        }

        static ChannelVideo from(VideosResponse.Item item) {
            if (item == null || item.getId() == null || item.getId().isBlank()) return null;
            VideosResponse.Snippet snippet = item.getSnippet();
            String title = snippet == null || snippet.getTitle() == null ? "" : snippet.getTitle().trim();
            String live = snippet == null || snippet.getLiveBroadcastContent() == null
                    ? "" : snippet.getLiveBroadcastContent().trim();
            String iso = item.getContentDetails() == null ? "" : item.getContentDetails().getDuration();
            String channelId = snippet == null || snippet.getChannelId() == null ? "" : snippet.getChannelId().trim();
            String channelTitle = snippet == null || snippet.getChannelTitle() == null ? "" : snippet.getChannelTitle().trim();
            String description = snippet == null || snippet.getDescription() == null ? "" : snippet.getDescription();
            return new ChannelVideo(
                    item.getId(), title, publishedDay(item), clock(item), iso, parseViewCount(item), live,
                    channelId, channelTitle, description);
        }

        public boolean liveOrUpcoming() {
            String live = liveBroadcastContent == null ? "" : liveBroadcastContent.trim().toLowerCase(Locale.ROOT);
            if ("live".equals(live) || "upcoming".equals(live)) return true;
            String iso = isoDuration == null ? "" : isoDuration.trim().toUpperCase(Locale.ROOT);
            return "PT0S".equals(iso) || "P0D".equals(iso);
        }
    }

    private static Long parseViewCount(VideosResponse.Item item) {
        if (item == null || item.getStatistics() == null || item.getStatistics().getViewCount() == null) return null;
        try {
            return Long.parseLong(item.getStatistics().getViewCount().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public VideosResponse fetchVideo(String urlOrId) {

        System.out.println("==================================================");
        System.out.println(">>> [DEBUG] 전달된 id의 글자 수: " + (urlOrId != null ? urlOrId.length() : "null"));

        if (!hasApiKey()) {
            return null;
        }
        if (urlOrId == null || urlOrId.isBlank()) {
            return null;
        }

        String videoId = YoutubeIdExtractor.extract(urlOrId);
        if (videoId == null || videoId.isBlank()) {
            videoId = urlOrId.trim();
        }

        try {
            String url = VIDEOS
                    + "?part=snippet,contentDetails,statistics&id=" + videoId
                    + "&key=" + apiKey();
            return rest.getForObject(URI.create(url), VideosResponse.class);

        } catch (Throwable t) {
            return null;
        }
    }

    private static <T> T get(RestTemplate template, URI uri, Class<T> type) {
        try {
            return template.getForObject(uri, type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static RestTemplate restTemplate(int connectMs, int readMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }

    @Data
    public static class VideosResponse {
        private Item[] items;

        @Data
        public static class Item {
            private String id;
            private Snippet snippet;
            private ContentDetails contentDetails;
            private Statistics statistics;
        }

        @Data
        public static class Snippet {
            private String title;
            private String description;
            private String publishedAt;
            private String liveBroadcastContent;
            private String channelId;
            private String channelTitle;
        }

        @Data
        public static class ContentDetails {
            private String duration; // PT3M59S
        }

        @Data
        public static class Statistics {
            private String viewCount;
        }
    }

    @Data
    public static class ChannelsResponse {
        private Item[] items;

        @Data
        public static class Item {
            private String id;
            private ChannelContentDetails contentDetails;
        }

        @Data
        public static class ChannelContentDetails {
            private RelatedPlaylists relatedPlaylists;
        }

        @Data
        public static class RelatedPlaylists {
            private String uploads;
        }
    }

    @Data
    public static class SearchResponse {
        private String nextPageToken;
        private Item[] items;

        @Data
        public static class Item {
            private SearchId id;
            private VideosResponse.Snippet snippet;
        }

        @Data
        public static class SearchId {
            private String videoId;
        }
    }

    @Data
    public static class PlaylistItemsResponse {
        private String nextPageToken;
        private Item[] items;

        @Data
        public static class Item {
            private PlaylistContentDetails contentDetails;
        }

        @Data
        public static class PlaylistContentDetails {
            private String videoId;
        }
    }
}
