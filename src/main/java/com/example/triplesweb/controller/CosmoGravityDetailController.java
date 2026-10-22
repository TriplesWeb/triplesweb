package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the detail page linked from /cosmo/gravity.
 * The metadata below mirrors the archive cards currently present in gravity.mustache.
 * It is presentation/archive data, not a live COSMO vote response.
 */
@Controller
public class CosmoGravityDetailController {

    private static final Map<String, Map<String, Object>> ARCHIVE = createArchive();

    private final GravityService gravityService;

    public CosmoGravityDetailController(GravityService gravityService) {
        this.gravityService = gravityService;
    }
    private static final Logger log =
            LoggerFactory.getLogger(CosmoGravityDetailController.class);

    @GetMapping("/cosmo/gravity/{id}")
    public String gravityDetail(
            @PathVariable String id,
            Model model
    ) {
        String normalizedId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);

        // Never load an arbitrary COSMO Gravity ID: only known tripleS archive IDs
        // or IDs returned by COSMO's tripleS-only Gravity list are allowed.
        if (normalizedId.matches("\\d+") && !isKnownTripleSGravity(normalizedId)) {
            model.addAttribute("archiveNotice",
                    "이 페이지는 tripleS 그래비티 전용입니다. tripleS로 확인되지 않은 그래비티는 표시하지 않습니다.");
            model.addAttribute("isKnownGravity", false);
            model.addAttribute("gravity", createDetail(
                    normalizedId,
                    "tripleS 그래비티를 찾을 수 없습니다",
                    "tripleS ONLY",
                    "조회 불가",
                    "tripleS 그래비티 목록에서 선택하거나, tripleS 소속이 확인된 Gravity ID를 사용해 주세요.",
                    "-",
                    "집계 미연결",
                    "다른 아티스트의 그래비티는 이 페이지에서 조회하지 않습니다.",
                    List.of()
            ));
            return "cosmo/gravity-detail";
        }

        // Numeric IDs are looked up live first. Static archive records remain a fallback
        // when no COSMO token is configured or the upstream API is unavailable.
        if (normalizedId.matches("\\d+") && gravityService.hasCosmoToken()) {
            try {
                JsonNode apiGravity = gravityService.fetchGravityDetail(Long.parseLong(normalizedId));
                // 1. COSMO API에서 실제로 받은 원본 응답
                log.info("========== COSMO Gravity 원본 응답 ==========");
                log.info("Gravity ID: {}", normalizedId);
                log.info("{}", apiGravity == null ? "응답이 null입니다." : apiGravity.toPrettyString());

// 2. Mustache에 전달할 형태로 변환
                Map<String, Object> liveDetail =
                        gravityService.buildGravityDetailView(apiGravity);

// 3. 변환된 Map 확인
                log.info("========== 변환된 gravity 데이터 ==========");
                log.info("{}", liveDetail);

// 4. 기존처럼 화면에 전달
                model.addAttribute("gravity", liveDetail);
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
                    "현재 연결된 상세 기록이 없습니다.",
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
     * Allow only tripleS archive IDs, explicitly verified tripleS mappings,
     * or Gravity IDs returned by COSMO's tripleS-only list.
     */
    private boolean isKnownTripleSGravity(String gravityId) {
        if (ARCHIVE.containsKey(gravityId)) {
            return true;
        }
        if (!gravityService.hasCosmoToken()) {
            return false;
        }
        try {
            return gravityService.fetchGravityList("tripleS").stream()
                    .anyMatch(item -> gravityId.equals(String.valueOf(item.get("id"))));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Map<String, Map<String, Object>> createArchive() {
        Map<String, Map<String, Object>> archive = new LinkedHashMap<>();

        archive.put("8", createDetail(
                "8", "ASSEMBLE24 완전체 타이틀곡 투표", "S1–S24 FULL UNIT", "최종 의결 완료",
                "ASSEMBLE24 정규 앨범 타이틀곡 선정을 위한 그래비티 기록입니다.",
                "2024.02.15 ~ 2024.02.20", "1,842,590 COMO",
                "저장된 아카이브 참고 정보입니다. 실제 후보별 득표량과 투표 수는 COSMO API에서 확인된 경우에만 표시됩니다.",
                candidates(
                        "Girls Never Die", "타이틀곡 후보",
                        "Midnight Flower", "타이틀곡 후보"
                )
        ));

        archive.put("7", createDetail(
                "7", "Visionary Vision (VV) 댄스 디멘션 리더 선출 Gravity", "CREAM01", "투표 완료",
                "Visionary Vision 퍼포먼스 유닛의 리더를 선정하는 그래비티 기록입니다.",
                "2023.11.02 ~ 2023.11.05", "714,120 COMO",
                "저장된 아카이브 후보 정보입니다. 실제 최종 득표량과 투표자별 집계는 COSMO API 응답으로 확인되지 않았습니다.",
                candidates(
                        "S12 곽연지", "리더 후보",
                        "S1 윤서연", "리더 후보"
                )
        ));

        archive.put("6", createDetail(
                "6", "Aria(아리아) 발라드 디멘션 5인 멤버 선정 Gravity", "BINARY01", "투표 완료",
                "아리아 발라드 디멘션 멤버 구성을 위한 그래비티 기록입니다.",
                "2023.10.12 ~ 2023.10.16", "852,940 COMO",
                "저장된 아카이브 요약입니다. 멤버별 실제 득표량은 확인된 COSMO API 응답이 없어 표시하지 않습니다.",
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
                "저장된 아카이브 설명과 수치입니다. 세부 선택 항목과 결과 분포는 COSMO API 응답으로 확인되지 않았습니다.",
                candidates(
                        "LOVElution", "디멘션", "EVOLution", "디멘션"
                )
        ));

        archive.put("1", createDetail(
                "1", "AAA vs +(KR)ystal Eyes", "1ST HISTORIC GRAVITY", "의결 채택",
                "tripleS 최초의 유닛 결성 그래비티 기록입니다.",
                "2022.09.21 ~ 2022.09.25", "481,200 COMO",
                "저장된 아카이브 요약입니다. 후보별 실제 득표량은 확인된 COSMO API 응답이 없어 표시하지 않습니다.",
                candidates(
                        "Acid Angel from Asia", "유닛", "+(KR)ystal Eyes", "유닛"
                )
        ));

        archive.put("fandom", createDetail(
                "FANDOM", "WAV (웨이브) 공식 팬덤명 확정", "OFFICIAL FANDOM", "의결 채택",
                "tripleS 공식 팬덤명을 정한 이벤트 그래비티 기록입니다.",
                "2022.12.01 ~ 2022.12.06", "312,890 COMO",
                "저장된 아카이브 요약입니다. 세부 투표 결과는 확인된 COSMO API 데이터가 없어 표시하지 않습니다.",
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
