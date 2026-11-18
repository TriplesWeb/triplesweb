package com.example.triplesweb.service;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.MemberPortraitOptions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumOstParticipationTest {

    @Test
    void partialOstKeepsFewerThanFullRoster() {
        Album album = albumWithArtists(3);
        assertThat(AlbumService.isPartialOstParticipation(album, MemberPortraitOptions.koreanOrder().size()))
                .isTrue();
    }

    @Test
    void fullRosterOstIsExcluded() {
        Album album = albumWithArtists(MemberPortraitOptions.koreanOrder().size());
        assertThat(AlbumService.isPartialOstParticipation(album, MemberPortraitOptions.koreanOrder().size()))
                .isFalse();
    }

    @Test
    void emptyArtistsAreExcluded() {
        Album album = new Album();
        album.setArtists(Set.of());
        assertThat(AlbumService.isPartialOstParticipation(album, 24)).isFalse();
    }

    private static Album albumWithArtists(int count) {
        Album album = new Album();
        LinkedHashSet<Artist> artists = new LinkedHashSet<>();
        for (long i = 1; i <= count; i++) {
            Artist artist = new Artist();
            artist.setArtistId(i);
            artists.add(artist);
        }
        album.setArtists(artists);
        return album;
    }
}
