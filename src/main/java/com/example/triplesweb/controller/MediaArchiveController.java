package com.example.triplesweb.controller;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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

/**
 * 유튜브 탭별(MV·SIGNAL 등) 미디어 아카이브 목록·상세·관리자 등록·삭제.
 * URL: {@code /media}, {@code /media/{tab}/{youtubeId}}, {@code /media_new}, …
 * 레이어: OK — MediaService 사용.
 */
@Controller
@RequiredArgsConstructor
public class MediaArchiveController {
    private final MediaService media;

    @GetMapping("/media")
    public String mediaPage (
            @RequestParam(name = "tab", defaultValue = "mv") String tab,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication auth) throws Exception {
        tab = normalizeTab(tab);
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 100);

        boolean isAdmin = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentTab", tab);
        model.addAttribute("isMV", "mv".equals(tab));
        model.addAttribute("isSIGNAL", "signal".equals(tab));
        model.addAttribute("isBehind", "behind".equals(tab));
        model.addAttribute("isSecret", "secret".equals(tab));
        model.addAttribute("isContents", "contents".equals(tab));

        var videos = media.pageByTab(tab, page, size);
        int total  = media.totalByTab(tab);
        int totalPages = (int)Math.ceil(total / (double)size);

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

        List<Map<String,Object>> pages = new ArrayList<>();
        int start = Math.max(1, page - 3);
        int end   = Math.min(Math.max(totalPages, 1), start + 6);
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
        model.addAttribute("related", media.related(t, youtubeId, 8)); // 같은 탭의 다른 영상
        return "archive/media_detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/media_new")
    public String newMediaForm(@RequestParam(defaultValue = "mv") String tab,
                               Model model,
                               Authentication auth) {
        MediaDto form = MediaDto.builder().tab(MediaTab.from(tab).getKey()).addFirst(true).build();
        bindTabFlags(model, form.normalizedTab());
        model.addAttribute("form", form);
        return "archive/media_new";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/media_new")
    public String CreateNewMedia (
            @org.springframework.validation.annotation.Validated @ModelAttribute("form") MediaDto form,
            BindingResult binding,
            Model model,
            Authentication auth) throws Exception {

        String tab = form != null ? form.normalizedTab() : "mv";
        if (!isAdmin(auth)) return "redirect:/media?tab=" + tab + "&error=forbidden";

        String rawId = form != null ? form.getYoutubeId() : "";

        String ytId = YoutubeIdExtractor.extract(rawId);

        // 2. 만약 추출에 실패했거나 정확히 11자리가 아니라면 입력 폼에 에러를 바인딩합니다.
        if (ytId == null || ytId.isBlank() || !ytId.matches("^[a-zA-Z0-9_-]{11}$")) {
            binding.rejectValue("youtubeId", "invalid", "유효한 유튜브 ID 또는 URL을 입력하세요.");
        } else {
            // 추출 성공 시 폼 데이터의 youtubeId를 정제된 11자리 ID로 업데이트해 줍니다.
            form.setYoutubeId(ytId);
        }

        if (binding.hasErrors()) {
            bindTabFlags(model, tab);
            model.addAttribute("form", form);
            return "archive/media_new";
        }

        // 3. 안전하게 가공된 폼을 서비스단에 전달
        media.registerMedia(tab, form);

        return "redirect:/media?tab=" + tab;

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/media_delete")
    public String deleteMedia(@RequestParam String tab,
                              @RequestParam String youtubeId,
                              Authentication auth) throws Exception {
        String t = MediaTab.from(tab).getKey();
        if (!isAdmin(auth)) return "redirect:/media?tab=" + t + "&error=forbidden";
        media.remove(t, youtubeId);
        return "redirect:/media?tab=" + t;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void bindTabFlags(Model model, String t) {
        model.addAttribute("tab", t);
        model.addAttribute("tab_mv", "mv".equals(t));
        model.addAttribute("tab_signal", "signal".equals(t));
        model.addAttribute("tab_behind", "behind".equals(t));
        model.addAttribute("tab_secret", "secret".equals(t));
        model.addAttribute("tab_contents", "contents".equals(t));
    }

    private String blankToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }
}