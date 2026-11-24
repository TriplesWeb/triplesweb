package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.ScheduleCategory;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.SchedulePresentation;
import java.util.Map;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class ScheduleController {
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;
    private final ArtistRepository artistRepository;
    private final AboutAdminAuth aboutAdminAuth;
    private final ArtistSnapshotService artistSnapshot;
    private final ObjectMapper objectMapper;

    public ScheduleController(ScheduleService scheduleService,
                              ScheduleRepository scheduleRepository,
                              ArtistRepository artistRepository,
                              AboutAdminAuth aboutAdminAuth,
                              ArtistSnapshotService artistSnapshot,
                              ObjectMapper objectMapper) {
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.artistRepository = artistRepository;
        this.aboutAdminAuth = aboutAdminAuth;
        this.artistSnapshot = artistSnapshot;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/schedule")
    public String showSchedulePage(@RequestParam(value = "artist", required = false) String artist,
                                   @RequestParam(required = false) Long focus,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   HttpServletRequest request,
                                   HttpSession session,
                                   Model model) throws Exception {
        LocalDate requestedDate = focus == null ? date : scheduleRepository.findStartById(focus).orElse(date);
        LocalDate initialDate = requestedDate == null ? LocalDate.now() : requestedDate;
        LocalDate first = initialDate.withDayOfMonth(1);
        LocalDate start = first.minusDays(6);
        LocalDate end = first.plusMonths(1).plusDays(6);
        String selectedArtist = artist == null || artist.isBlank() ? null : artist;
        List<Artist> artists = artistSnapshot.findAll();
        List<ScheduleForm> dtos = scheduleService.findOverlapping(start, end, selectedArtist).stream()
                .map(schedule -> SchedulePresentation.form(schedule, artists)).toList();
        model.addAttribute("schedulesJson", objectMapper.writeValueAsString(dtos).replace("<", "\\u003c").replace("&", "\\u0026"));
        model.addAttribute("scheduleInitialDate", requestedDate == null ? "" : requestedDate.toString());
        model.addAttribute("scheduleInitialStart", start.toString());
        model.addAttribute("scheduleInitialEnd", end.toString());
        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("artistOptions", artists);
        model.addAttribute("scheduleCategories", SchedulePresentation.categories());
        // Mustache는 빈 문자열도 section을 렌더하므로, 필터 없을 때는 null로 둔다.
        model.addAttribute("selectedArtist", selectedArtist);
        return "schedule";
    }

    @GetMapping("/api/schedule/events")
    @ResponseBody
    public List<ScheduleForm> events(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                     @RequestParam(required = false) String artist) {
        if (!start.isBefore(end) || ChronoUnit.DAYS.between(start, end) > 62) {
            throw new IllegalArgumentException("일정 조회 범위는 1~62일이어야 합니다.");
        }
        List<Artist> artists = artistSnapshot.findAll();
        return scheduleService.findOverlapping(start, end, artist == null || artist.isBlank() ? null : artist).stream()
                .map(schedule -> SchedulePresentation.form(schedule, artists))
                .toList();
    }

    @GetMapping("/schedule/{id}")
    public String getScheduleDetail(@PathVariable Long id) {
        return scheduleFocus(id);
    }

    @GetMapping("/add")
    public String addScheduleForm(HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        return "redirect:/schedule?add=true";
    }

    @PostMapping("/schedule/add")
    public ResponseEntity<ScheduleForm> addSchedule(@RequestParam String title,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                                    @RequestParam(value = "artistNames", required = false) List<String> names,
                                                    @RequestParam(required = false) ScheduleCategory category,
                                                    HttpServletRequest request,
                                                    HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        Schedule saved=scheduleService.save(title,start,end,names==null?"":String.join(",",names),category);
        return ResponseEntity.ok(SchedulePresentation.form(saved,artistRepository.findAll()));
    }

    @GetMapping("/edit/{id}")
    public String editScheduleForm(@PathVariable Long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        return scheduleFocus(id) + "&edit=true";
    }

    @PostMapping("/edit/{id}")
    public ResponseEntity<?> editSchedule(@PathVariable Long id,
                                          @RequestParam String title,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                          @RequestParam(value = "artistNames", required = false) List<String> names,
                                          @RequestParam(required = false) ScheduleCategory category,
                                          HttpServletRequest request,
                                          HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        Schedule saved=scheduleService.update(String.valueOf(id),title,startDate,endDate,names==null?"":String.join(",",names),category);
        return ResponseEntity.ok(SchedulePresentation.form(saved,artistRepository.findAll()));
    }

    @PostMapping("/delete/{id}")
    public String deleteSchedule(@PathVariable Long id,
                                 HttpServletRequest request,
                                 HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        scheduleService.deleteById(id);
        return "redirect:/schedule";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalidSchedule(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message",error.getMessage()));
    }

    private String scheduleFocus(Long id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 일정이 없습니다.");
        }
        return "redirect:/schedule?focus=" + id;
    }
}
