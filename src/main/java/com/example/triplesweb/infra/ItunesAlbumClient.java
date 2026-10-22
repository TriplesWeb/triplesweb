package com.example.triplesweb.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apple iTunes Search/Lookup API — 앨범 커버·발매일·트랙리스트.
 * 공개 API, 키 불필요. (https://performance-partners.apple.com/search-api)
 */
@Component
public class ItunesAlbumClient {

    public static final long TRIPLES_ARTIST_ID = 1651595986L;

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ItunesAlbumClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RemoteAlbum> fetchTriplesAlbums() {
        Map<Long, RemoteAlbum> byId = new LinkedHashMap<>();
        for (RemoteAlbum album : lookupArtistAlbums(TRIPLES_ARTIST_ID)) {
            byId.putIfAbsent(album.collectionId(), album);
        }
        // 아티스트 목록에 안 잡히는 싱글 보완
        for (String term : List.of("Touch+ +(KR)ystal Eyes", "Acid Eyes Cherry Gene")) {
            for (RemoteAlbum album : searchAlbums(term, 5)) {
                byId.putIfAbsent(album.collectionId(), album);
            }
        }
        List<RemoteAlbum> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(RemoteAlbum::releaseDate, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    public List<RemoteTrack> fetchTracks(long collectionId) {
        JsonNode root = getJson("https://itunes.apple.com/lookup?id=" + collectionId + "&entity=song&country=us");
        List<RemoteTrack> tracks = new ArrayList<>();
        if (root == null || !root.has("results")) return tracks;
        for (JsonNode node : root.get("results")) {
            if (!"track".equals(text(node, "wrapperType"))) continue;
            int num = node.path("trackNumber").asInt(tracks.size() + 1);
            String title = text(node, "trackName");
            if (title == null || title.isBlank()) continue;
            long ms = node.path("trackTimeMillis").asLong(0);
            tracks.add(new RemoteTrack(num, title.trim(), formatDuration(ms)));
        }
        tracks.sort(Comparator.comparingInt(RemoteTrack::trackNum));
        return tracks;
    }

    private List<RemoteAlbum> lookupArtistAlbums(long artistId) {
        JsonNode root = getJson("https://itunes.apple.com/lookup?id=" + artistId + "&entity=album&limit=200&country=us");
        return parseAlbums(root);
    }

    private List<RemoteAlbum> searchAlbums(String term, int limit) {
        String q = URLEncoder.encode(term, StandardCharsets.UTF_8);
        JsonNode root = getJson("https://itunes.apple.com/search?term=" + q + "&entity=album&country=us&limit=" + limit);
        return parseAlbums(root);
    }

    private List<RemoteAlbum> parseAlbums(JsonNode root) {
        List<RemoteAlbum> out = new ArrayList<>();
        if (root == null || !root.has("results")) return out;
        for (JsonNode node : root.get("results")) {
            if (!"collection".equals(text(node, "wrapperType"))) continue;
            long id = node.path("collectionId").asLong(0);
            String name = text(node, "collectionName");
            if (id <= 0 || name == null || name.isBlank()) continue;
            String release = text(node, "releaseDate");
            if (release != null && release.length() >= 10) release = release.substring(0, 10);
            String art = text(node, "artworkUrl100");
            if (art != null) {
                art = art.replace("100x100bb", "600x600bb").replace("100x100", "600x600");
            }
            out.add(new RemoteAlbum(id, name.trim(), release, art, node.path("trackCount").asInt(0)));
        }
        return out;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "triplesweb/1.0 (album-catalog)")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) return null;
            return objectMapper.readTree(res.body());
        } catch (Exception e) {
            throw new IllegalStateException("iTunes API 호출 실패: " + e.getMessage(), e);
        }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "";
        long totalSec = ms / 1000;
        return (totalSec / 60) + ":" + String.format(Locale.ROOT, "%02d", totalSec % 60);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public record RemoteAlbum(long collectionId, String name, String releaseDate, String artworkUrl, int trackCount) {}

    public record RemoteTrack(int trackNum, String title, String duration) {}
}
