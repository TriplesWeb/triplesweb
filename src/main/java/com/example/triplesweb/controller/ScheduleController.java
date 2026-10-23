package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArtistTagDto;
import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;

@Controller
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;
    private final ArtistRepository artistRepository;

    public ScheduleController(
            ScheduleRepository scheduleRepository,
            ScheduleService scheduleService,
            ArtistRepository artistRepository
    ) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleService = scheduleService;
        this.artistRepository = artistRepository;
    }

    @GetMapping("/schedule")
    public String showSchedulePage(Model model) throws Exception { // [주석] Authentication 파라미터 제거
        List<Schedule> schedules = scheduleService.findAll();

        List<ScheduleForm> dtoList = schedules.stream().map(this::toScheduleForm).toList();

        model.addAttribute("artistOptions", artistRepository.findAll().stream()
                .sorted(Comparator.comparing(Artist::getArtistId))
                .map(a -> ArtistTagDto.builder()
                        .artistId(a.getArtistId())
                        .name(a.getName())
                        .engName(a.getEngName())
                        .build())
                .toList());

        // [주석] 로그인 기능 삭제로 인해 관리자 여부를 항상 true로 설정하여 캘린더 관리 버튼 노출
        boolean isAdmin = true;

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String schedulesJson = objectMapper.writeValueAsString(dtoList);

        model.addAttribute("schedules", dtoList);
        model.addAttribute("schedulesJson", schedulesJson);
        model.addAttribute("isAdmin", isAdmin);
        return "schedule";
    }

    @GetMapping("/schedule/{id}")
    public String getScheduleDetail(@PathVariable Long id, Model model) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 스케줄 ID: " + id));

        model.addAttribute("schedule", schedule);
        return "schedule";
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 삭제
    @GetMapping("/add")
    public String addScheduleForm() {
        return "schedule-add";
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 삭제
    @PostMapping("/schedule/add")
    public ResponseEntity<ScheduleForm> addSchedule(@RequestParam String title,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                                    @RequestParam(required = false) List<Long> artistIds) {
        Schedule saved = scheduleService.save(title, start, end, artistIds);
        return ResponseEntity.ok(toScheduleForm(saved));
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 삭제
    @GetMapping("/edit/{id}")
    public String editScheduleForm(@PathVariable Long id, Model model) {
        Schedule schedule = scheduleService.findById(String.valueOf(id));
        model.addAttribute("schedule", schedule);
        return "schedule-edit";
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 삭제
    @PostMapping("/edit/{id}")
    public ResponseEntity<ScheduleForm> editSchedule(@PathVariable Long id,
                                          @RequestParam String title,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                          @RequestParam(required = false) List<Long> artistIds) {
        Schedule updated = scheduleService.update(String.valueOf(id), title, startDate, endDate, artistIds);
        return ResponseEntity.ok(toScheduleForm(updated));
    }

    private ScheduleForm toScheduleForm(Schedule schedule) {
        ScheduleForm form = new ScheduleForm(
                String.valueOf(schedule.getId()),
                schedule.getTitle(),
                schedule.getStart(),
                schedule.getEnd() != null ? schedule.getEnd().plusDays(1) : null,
                schedule.getEnd()
        );
        form.setArtists(schedule.getArtists() == null ? List.of() : schedule.getArtists().stream()
                .sorted(Comparator.comparing(Artist::getArtistId))
                .map(a -> ArtistTagDto.builder()
                        .artistId(a.getArtistId())
                        .name(a.getName())
                        .engName(a.getEngName())
                        .build())
                .toList());
        return form;
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 삭제
    @PostMapping("/delete/{id}")
    public String deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteById(id);
        return "redirect:/schedule";
    }
}