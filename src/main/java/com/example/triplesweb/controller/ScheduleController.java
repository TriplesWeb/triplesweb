package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.ScheduleCategory;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.SchedulePresentation;
import java.util.Map;
import java.util.Comparator;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
public class ScheduleController {
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;
    private final ArtistRepository artistRepository;
    private final AboutAdminAuth aboutAdminAuth;

    public ScheduleController(ScheduleService scheduleService,
                              ScheduleRepository scheduleRepository,
                              ArtistRepository artistRepository,
                              AboutAdminAuth aboutAdminAuth) {
        this.scheduleService = scheduleService;
        this.scheduleRepository = scheduleRepository;
        this.artistRepository = artistRepository;
        this.aboutAdminAuth = aboutAdminAuth;
    }

    @GetMapping("/schedule")
    public String showSchedulePage(@RequestParam(required = false) String artist,
                                   HttpServletRequest request,
                                   HttpSession session,
                                   Model model) throws Exception {
        List<Schedule> all = scheduleService.findAll();
        List<Schedule> schedules = artist == null || artist.isBlank()
                ? all
                : all.stream().filter(s -> splitNames(s.getArtistNames()).contains(artist)).toList();
        List<Artist> artists=artistRepository.findAll().stream().sorted(Comparator.comparing(Artist::getArtistId,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        List<ScheduleForm> dtos=schedules.stream().map(schedule->SchedulePresentation.form(schedule,artists)).toList();
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        model.addAttribute("schedules", dtos);
        model.addAttribute("schedulesJson", mapper.writeValueAsString(dtos).replace("<", "\\u003c").replace("&", "\\u0026"));
        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        model.addAttribute("artistOptions", artists);
        model.addAttribute("scheduleCategories", SchedulePresentation.categories());
        model.addAttribute("selectedArtist", artist == null ? "" : artist);
        return "schedule";
    }

    @GetMapping("/schedule/{id}")
    public String getScheduleDetail(@PathVariable Long id, Model model) {
        Schedule s = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 스케줄 ID: " + id));
        model.addAttribute("schedule", s);
        return "schedule";
    }

    @GetMapping("/add")
    public String addScheduleForm() {
        return "schedule-add";
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
    public String editScheduleForm(@PathVariable Long id, Model model) {
        model.addAttribute("schedule", scheduleService.findById(String.valueOf(id)));
        return "schedule-edit";
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

    private List<String> splitNames(String names) {
        if (names == null || names.isBlank()) return List.of();
        return Arrays.stream(names.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
}
