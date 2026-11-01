package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/** Rarely changing artist rows kept in memory to avoid Tailscale MariaDB RTT on hot pages. */
@Service
@RequiredArgsConstructor
public class ArtistSnapshotService {
    private final ArtistRepository artistRepository;
    private volatile List<Artist> artists = List.of();

    @PostConstruct
    void load() {
        refreshQuietly();
    }

    @Scheduled(fixedDelayString = "${artist.cache.refresh-ms:300000}", initialDelayString = "60000")
    public void refresh() {
        artists = List.copyOf(artistRepository.findAll(Sort.by(Sort.Direction.ASC, "artistId")));
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (RuntimeException ignored) {
            // Keep empty snapshot; first request may retry once DB is reachable.
        }
    }

    public List<Artist> findAll() {
        List<Artist> snapshot = artists;
        if (snapshot.isEmpty()) {
            refreshQuietly();
            snapshot = artists;
        }
        return snapshot;
    }
}
