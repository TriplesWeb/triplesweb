package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ScheduleController {

    @Autowired
    private ScheduleRepository scheduleRepository;

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/schedule")
    public String showSchedulePage(Model model, Authentication authentication) throws Exception {
        List<Schedule> schedules = scheduleService.findAll();

        List<ScheduleForm> dtoList = schedules.stream()
                .map(s -> new ScheduleForm(
                        String.valueOf(s.getId()),
                        s.getTitle(),
                        s.getStart(),
                        s.getEnd() != null ? s.getEnd().plusDays(1) : null,
                        s.getEnd()
                ))
                .toList();
        boolean isAdmin = authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

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

        return "schedule"; // schedule.mustache 뷰 반환
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/add")
    public String addScheduleForm() {
        return "schedule-add";  // 스케줄 추가 폼 뷰
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/schedule/add")
    public ResponseEntity<ScheduleForm> addSchedule(@RequestParam String title,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                                    Model model) {
        // 스케줄 저장 로직
        scheduleService.save(title, start, end);
        return ResponseEntity.ok().build();
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/edit/{id}")
    public String editScheduleForm(@PathVariable Long id, Model model) {
        Schedule schedule = scheduleService.findById(String.valueOf(id));
        model.addAttribute("schedule", schedule);
        return "schedule-edit";  // 수정 폼 뷰
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/edit/{id}")
    public ResponseEntity<?> editSchedule(@PathVariable Long id,
                               @RequestParam String title,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        scheduleService.update(String.valueOf(id), title, startDate, endDate);
        return ResponseEntity.ok().build();
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/delete/{id}")
    public String deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteById(id);
        return "redirect:/schedule";
    }

}
