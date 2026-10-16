package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Members;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ArticleServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 메인 홈 — 최근·인기 게시글과 일정 요약을 한 화면에 노출한다.
 * URL: {@code GET /}
 * 레이어: Optional — ArticleServiceImpl과 ScheduleRepository 혼용, 일정 조회 Service 통일 검토.
 */
@RequiredArgsConstructor
@Controller
@Slf4j
public class MainController {

    private final ArticleServiceImpl articleService;
    private final ScheduleRepository scheduleRepository;

    /** 이전 URL 호환: {@code /main} → {@code /} */
    @GetMapping("/main")
    public String redirectMain() {
        return "redirect:/";
    }

    @GetMapping("/")
    public String mainPage(Model model, HttpSession session, @RequestParam(defaultValue = "5") Integer limit) {
        Members loginMember = (Members) session.getAttribute("loginMember");
        if (loginMember != null) {
            model.addAttribute("userId", loginMember.getUserId());
            model.addAttribute("member", loginMember);

            Integer level = loginMember.getLevel();
            Integer points = loginMember.getPoints();
            model.addAttribute("level", level);
            model.addAttribute("points", points);
        }

        List<Schedule> scheduleList = scheduleRepository.findAll();

        List<ScheduleForm> scheduleForms = scheduleList.stream()
                .map(s -> new ScheduleForm(String.valueOf(s.getId()),
                        s.getTitle(),
                        s.getStart(),
                        s.getEnd() != null ? s.getEnd().plusDays(1) : null,
                        s.getEnd()))
                .collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String schedulesJson = "";
        try {
            schedulesJson = mapper.writeValueAsString(scheduleForms);
        } catch (Exception ignored) {}


        model.addAttribute("freePosts",   articleService.findRecentByCategory("free",   limit));
        model.addAttribute("noticePosts", articleService.findRecentByCategory("notice", limit));
        model.addAttribute("popularPosts",articleService.findPopularArticles(null, limit));
        model.addAttribute("schedulesJson", schedulesJson);

        return "main";
    }
}