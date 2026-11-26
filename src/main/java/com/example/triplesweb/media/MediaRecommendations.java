package com.example.triplesweb.media;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.util.MemberPortraitOptions;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class MediaRecommendations {
    private static final Set<String> COMMON_WORDS = Set.of(
            "triples", "트리플에스", "official", "video", "shorts", "signal", "behind",
            "비하인드", "직캠", "fancam", "full", "performance", "stage", "kpop", "youtube");

    private MediaRecommendations() {}

    public static List<MediaDto> choose(MediaDto anchor, List<MediaDto> candidates) {
        Set<String> anchorMembers = members(anchor);
        Set<String> anchorKeywords = keywords(anchor.getTitle());
        LocalDate anchorDate = date(anchor.getPublishedOn());
        Set<String> seen = new HashSet<>();
        seen.add(anchor.getYoutubeId());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return candidates.stream()
                .filter(video -> anchor.getTab().equals(video.getTab()))
                .filter(video -> video.getYoutubeId() != null && !video.getYoutubeId().isBlank())
                .filter(video -> seen.add(video.getYoutubeId()))
                .map(video -> {
                    Set<String> videoMembers = members(video);
                    long matchedMembers = videoMembers.stream().filter(anchorMembers::contains).count();
                    double castWeight = 6.0 * matchedMembers / Math.max(1, Math.max(anchorMembers.size(), videoMembers.size()));
                    long matchedKeywords = keywords(video.getTitle()).stream().filter(anchorKeywords::contains).count();
                    LocalDate videoDate = date(video.getPublishedOn());
                    double dateWeight = anchorDate == null || videoDate == null ? 0.0
                            : 1.0 / (1.0 + Math.abs(ChronoUnit.DAYS.between(anchorDate, videoDate)) / 30.0);
                    double weight = 1.0 + castWeight + Math.min(matchedKeywords, 4) + dateWeight;
                    // Independent exponential scores sample without replacement in proportion to weight.
                    return new RankedVideo(video, -Math.log1p(-random.nextDouble()) / weight);
                })
                .sorted(Comparator.comparingDouble(RankedVideo::score))
                .limit(5)
                .map(RankedVideo::video)
                .toList();
    }

    private static Set<String> members(MediaDto video) {
        String names = video.getArtistNames();
        if (names == null || names.isBlank()) {
            List<String> lineup = MemberPortraitOptions.koreanOrder();
            return MediaHashtags.tags(video.getTitle(), null).stream()
                    .mapToInt(MemberPortraitOptions::memberIndex)
                    .filter(index -> index < lineup.size())
                    .mapToObj(lineup::get)
                    .collect(Collectors.toSet());
        }
        return Arrays.stream(names.split(","))
                .map(MemberPortraitOptions::memberName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());
    }

    private static Set<String> keywords(String title) {
        if (title == null || title.isBlank()) return Set.of();
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 2 && !COMMON_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record RankedVideo(MediaDto video, double score) {}
}
