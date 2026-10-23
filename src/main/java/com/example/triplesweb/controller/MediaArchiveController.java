package com.example.triplesweb.controller;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MediaArchiveController {
    private final MediaService media;

    @GetMapping("/media")
    public String mediaPage(
            @RequestParam(name = "tab", defaultValue = "mv") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) throws Exception { // [주석] 로그인 기능 삭제로 Authentication auth 파라미터 제거
        tab = normalizeTab(tab);
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 100);

        // [주석] 로그인 및 관리자 체크 삭제 -> 항상 관리자 권한 허용
        boolean isAdmin = true;

        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentTab", tab);
        model.addAttribute("isMV", "mv".equals(tab));
        model.addAttribute("isSIGNAL", "signal".equals(tab));
        model.addAttribute("isBehind", "behind".equals(tab));
        model.addAttribute("isSecret", "secret".equals(tab));
        model.addAttribute("isContents", "contents".equals(tab));

        var videos = media.pageByTab(tab, page, size);
        int total = media.totalByTab(tab);
        int totalPages = (int) Math.ceil(total / (double) size);

        model.addAttribute("videos", videos);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", Math.max(totalPages, 1));

        boolean hasPrev = page > 1;
        boolean hasNext = page < totalPages;
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);

        model.addAttribute("prevPage", hasPrev ? page - 1 : 1);
        model.addAttribute("nextPage", hasNext ? page + 1 : totalPages);

        List<Map<String, Object>> pages = new ArrayList<>();
        int start = Math.max(1, page - 3);
        int end = Math.min(Math.max(totalPages, 1), start + 6);
        for (int i = start; i <= end; i++) {
            pages.add(Map.of("num", i, "active", i == page));
        }
        model.addAttribute("pages", pages);

        return "archive/media";
    }

    private String normalizeTab(String tab) {
        switch (tab.toLowerCase()) {
            case "mv": case "signal": case "behind": case "secret": case "contents":
                return tab.toLowerCase();
            default:
                return "mv";
        }
    }

    @GetMapping("/media/{tab}/{youtubeId}")
    public String mediaDetail(@PathVariable String tab,
                              @PathVariable String youtubeId,
                              Model model) throws IOException {

        MediaTab t = MediaTab.from(tab);
        MediaDto v = media.findOne(t, youtubeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상이 없습니다."));

        model.addAttribute("currentTab", t.getKey());
        model.addAttribute("tabNameKo", t.getKor());
        model.addAttribute("video", v);
        model.addAttribute("related", media.related(t, youtubeId, 8));
        return "archive/media_detail";
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 및 auth 파라미터 제거
    @GetMapping("/media_new")
    public String newMediaForm(@RequestParam(defaultValue = "mv") String tab,
                               Model model) {
        MediaDto form = MediaDto.builder().tab(MediaTab.from(tab).getKey()).addFirst(true).build();
        bindTabFlags(model, form.normalizedTab());
        model.addAttribute("form", form);
        return "archive/media_new";
    }

    // [주석] 관리자 권한 검증 코드 제거
    @PostMapping("/media_new")
    public String CreateNewMedia(
            @org.springframework.validation.annotation.Validated @ModelAttribute("form") MediaDto form,
            BindingResult binding,
            Model model) throws Exception {

        String tab = form != null ? form.normalizedTab() : "mv";
        String rawId = form != null ? form.getYoutubeId() : "";
        String ytId = YoutubeIdExtractor.extract(rawId);

        if (ytId == null || ytId.isBlank() || !ytId.matches("^[a-zA-Z0-9_-]{11}$")) {
            binding.rejectValue("youtubeId", "invalid", "유효한 유튜브 ID 또는 URL을 입력하세요.");
        } else {
            form.setYoutubeId(ytId);
        }

        if (binding.hasErrors()) {
            bindTabFlags(model, tab);
            model.addAttribute("form", form);
            return "archive/media_new";
        }

        try {
            media.registerMedia(tab, form);
        } catch (IllegalArgumentException ex) {
            bindTabFlags(model, tab);
            model.addAttribute("form", form);
            model.addAttribute("formError", ex.getMessage());
            return "archive/media_new";
        }
        return "redirect:/media?tab=" + tab;
    }

    // [주석] 관리자 권한 검증 코드 제거
    @PostMapping("/media_delete")
    public String deleteMedia(@RequestParam String tab,
                              @RequestParam String youtubeId) throws Exception {
        String t = MediaTab.from(tab).getKey();
        media.remove(t, youtubeId);
        return "redirect:/media?tab=" + t;
    }

    private void bindTabFlags(Model model, String t) {
        model.addAttribute("tab", t);
        model.addAttribute("tab_mv", "mv".equals(t));
        model.addAttribute("tab_signal", "signal".equals(t));
        model.addAttribute("tab_behind", "behind".equals(t));
        model.addAttribute("tab_secret", "secret".equals(t));
        model.addAttribute("tab_contents", "contents".equals(t));
        model.addAttribute("artistOptions", media.listArtistOptions());
    }
}