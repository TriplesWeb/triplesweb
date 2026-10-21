package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serves the detail page linked from /cosmo/gravity.
 * The metadata below mirrors the archive cards currently present in gravity.mustache.
 * It is presentation/archive data, not a live COSMO vote response.
 */
@Controller
public class CosmoGravityDetailController {

    private static final Map<String, Map<String, Object>> ARCHIVE = createArchive();

    private final GravityService gravityService;
    private final String apolloPollMap;

    public CosmoGravityDetailController(
            GravityService gravityService,
            @Value("${gravity.apollo-poll-map:233:274}") String apolloPollMap
    ) {
        this.gravityService = gravityService;
        this.apolloPollMap = apolloPollMap == null ? "" : apolloPollMap;
    }

    @GetMapping("/cosmo/gravity/{id}")
    public String gravityDetail(
            @PathVariable String id,
            @RequestParam(required = false) Long pollId,
            Model model
    ) {
        String normalizedId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        Long selectedPollId = pollId != null
                ? (pollId > 0 ? pollId : null)
                : resolveMappedPollId(normalizedId);
        if (pollId != null && pollId <= 0) {
            model.addAttribute("apolloPollError", "Poll ID는 1 이상의 숫자여야 합니다.");
        }
        model.addAttribute("apolloPollId", selectedPollId);
        model.addAttribute("hasApolloPollId", selectedPollId != null);

        // Numeric IDs are looked up live first. Static archive records remain a fallback
        // when no COSMO token is configured or the upstream API is unavailable.
        if (normalizedId.matches("\\d+") && gravityService.hasCosmoToken()) {
            try {
                JsonNode apiGravity = gravityService.fetchGravityDetail(Long.parseLong(normalizedId));
                if (pollId == null) {
                    Long livePollId = findFirstPollId(apiGravity);
                    if (livePollId != null) {
                        selectedPollId = livePollId;
                        model.addAttribute("apolloPollId", selectedPollId);
                        model.addAttribute("hasApolloPollId", true);
                    }
                }
                Map<String, Object> liveDetail = gravityService.buildGravityDetailView(apiGravity);
                model.addAttribute("gravity", liveDetail);
                model.addAttribute("isKnownGravity", true);
                model.addAttribute("isLiveGravity", true);
                return "cosmo/gravity-detail";
            } catch (Exception exception) {
                model.addAttribute("apiError", GravityService.describeApiError(exception));
            }
        } else if (!gravityService.hasCosmoToken()) {
            model.addAttribute("archiveNotice",
                    "현재는 COSMO_ACCESS_TOKEN이 설정되지 않아 저장된 아카이브 요약을 표시합니다. 실시간 정보는 아직 조회되지 않았습니다.");
        }

        Map<String, Object> detail = ARCHIVE.get(normalizedId);

        if (detail == null) {
            detail = createDetail(
                    normalizedId,
                    "그래비티 상세 정보",
                    "ARCHIVE ENTRY",
                    "상세 정보 준비 중",
                    "현재 목록에 연결된 상세 기록이 없습니다.",
                    "-",
                    "집계 데이터 미연동",
                    "이 항목의 설명과 후보 정보는 아직 등록되지 않았습니다. COSMO API 연동 후 실제 투표 정보로 교체할 수 있습니다.",
                    List.of()
            );
            model.addAttribute("isKnownGravity", false);
        } else {
            model.addAttribute("isKnownGravity", true);
        }

        model.addAttribute("gravity", detail);
        return "cosmo/gravity-detail";
    }

    /**
     * Resolve a Poll ID without requiring a COSMO token. Configure multiple entries as
     * comma-separated GravityID:PollID pairs, for example 233:274,234:275.
     */
    private Long resolveMappedPollId(String gravityId) {
        if (apolloPollMap.isBlank() || gravityId == null || gravityId.isBlank()) return null;
        for (String entry : apolloPollMap.split(",")) {
            String[] pair = entry.trim().split(":", 2);
            if (pair.length != 2 || !gravityId.equals(pair[0].trim())) continue;
            try {
                long candidate = Long.parseLong(pair[1].trim());
                if (candidate > 0) return candidate;
            } catch (NumberFormatException ignored) {
                // Ignore malformed mapping entries; an explicit pollId can still be supplied.
            }
        }
        return null;
    }

    private static Long findFirstPollId(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) return null;
        JsonNode gravity = source;
        if (source.path("gravity").isObject()) gravity = source.path("gravity");
        else if (source.path("data").isObject()) {
            JsonNode data = source.path("data");
            gravity = data.path("gravity").isObject() ? data.path("gravity") : data;
        }
        JsonNode polls = gravity.path("polls");
        if (!polls.isArray()) return null;
        for (JsonNode poll : polls) {
            long candidate = poll.path("id").asLong(0);
            if (candidate > 0) return candidate;
        }
        return null;
    }

    private static Map<String, Map<String, Object>> createArchive() {
        Map<String, Map<String, Object>> archive = new LinkedHashMap<>();

        archive.put("8", createDetail(
                "8", "ASSEMBLE24 완전체 타이틀곡 투표", "S1–S24 FULL UNIT", "최종 의결 완료",
                "ASSEMBLE24 정규 앨범 타이틀곡 선정을 위한 그래비티 기록입니다.",
                "2024.02.15 ~ 2024.02.20", "1,842,590 COMO",
                "목록 페이지에 기재된 요약을 기준으로 구성했습니다. 후보별 득표량과 투표 수는 실시간 COSMO/Apollo 응답을 아직 연결하지 않았습니다.",
                candidates(
                        "Girls Never Die", "타이틀곡 후보",
                        "Midnight Flower", "타이틀곡 후보"
                )
        ));

        archive.put("7", createDetail(
                "7", "Visionary Vision (VV) 댄스 디멘션 리더 선출 Gravity", "CREAM01", "투표 완료",
                "Visionary Vision 퍼포먼스 유닛의 리더를 선정하는 그래비티 기록입니다.",
                "2023.11.02 ~ 2023.11.05", "714,120 COMO",
                "목록 설명에 언급된 후보를 표시합니다. 최종 득표량과 투표자별 집계는 API 연결 후 표시해야 합니다.",
                candidates(
                        "S12 곽연지", "리더 후보",
                        "S1 윤서연", "리더 후보"
                )
        ));

        archive.put("6", createDetail(
                "6", "Aria(아리아) 발라드 디멘션 5인 멤버 선정 Gravity", "BINARY01", "투표 완료",
                "아리아 발라드 디멘션 멤버 구성을 위한 그래비티 기록입니다.",
                "2023.10.12 ~ 2023.10.16", "852,940 COMO",
                "목록 페이지에 적힌 선정 멤버 요약입니다. 멤버별 실제 득표량은 확인된 API 응답이 없어 표시하지 않습니다.",
                candidates(
                        "다현", "선정 멤버 기록", "채연", "선정 멤버 기록",
                        "지우", "선정 멤버 기록", "카에데", "선정 멤버 기록",
                        "니엔", "선정 멤버 기록"
                )
        ));

        archive.put("4", createDetail(
                "4", "LOVElution & EVOLution", "DIMENSION FORMATION", "의결 채택",
                "LOVElution과 EVOLution의 듀얼 디멘션 8:8 멤버 밸런스 조합을 다룬 그래비티 기록입니다.",
                "2023.04.21 ~ 2023.04.28", "986,210 COMO",
                "현재 목록에 기록된 설명과 수치만 표시합니다. 세부 선택 항목과 결과 분포는 API 연동 전입니다.",
                candidates(
                        "LOVElution", "디멘션", "EVOLution", "디멘션"
                )
        ));

        archive.put("1", createDetail(
                "1", "AAA vs +(KR)ystal Eyes", "1ST HISTORIC GRAVITY", "의결 채택",
                "tripleS 최초의 유닛 결성 그래비티 기록입니다.",
                "2022.09.21 ~ 2022.09.25", "481,200 COMO",
                "목록 페이지에 기재된 아카이브 요약입니다. 후보별 실제 득표량은 COSMO/Apollo API 응답을 연결한 뒤 표시할 수 있습니다.",
                candidates(
                        "Acid Angel from Asia", "유닛", "+(KR)ystal Eyes", "유닛"
                )
        ));

        archive.put("fandom", createDetail(
                "FANDOM", "WAV (웨이브) 공식 팬덤명 확정", "OFFICIAL FANDOM", "의결 채택",
                "tripleS 공식 팬덤명을 정한 이벤트 그래비티 기록입니다.",
                "2022.12.01 ~ 2022.12.06", "312,890 COMO",
                "목록 페이지의 요약을 사용했습니다. 세부 투표 결과는 실시간 API 데이터가 연결되지 않은 상태입니다.",
                candidates("WAV (웨이브)", "공식 팬덤명")
        ));

        return archive;
    }

    private static Map<String, Object> createDetail(
            String id,
            String title,
            String category,
            String status,
            String description,
            String period,
            String como,
            String resultNote,
            List<Map<String, String>> candidates
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", id);
        detail.put("title", title);
        detail.put("category", category);
        detail.put("status", status);
        detail.put("description", description);
        detail.put("period", period);
        detail.put("como", como);
        detail.put("resultNote", resultNote);
        detail.put("candidates", candidates);
        detail.put("hasCandidates", !candidates.isEmpty());
        detail.put("candidateCount", candidates.size());
        return detail;
    }

    private static List<Map<String, String>> candidates(String... nameAndType) {
        List<Map<String, String>> candidates = new ArrayList<>();
        for (int i = 0; i + 1 < nameAndType.length; i += 2) {
            Map<String, String> candidate = new LinkedHashMap<>();
            candidate.put("name", nameAndType[i]);
            candidate.put("type", nameAndType[i + 1]);
            candidate.put("number", String.format("%02d", candidates.size() + 1));
            candidates.add(candidate);
        }
        return candidates;
    }
}
