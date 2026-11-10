package com.example.triplesweb.controller;

import com.example.triplesweb.dto.CalendarDecorationDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CalendarDecorationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar-decorations")
@RequiredArgsConstructor
public class CalendarDecorationController {
    private final CalendarDecorationService service;
    private final AboutAdminAuth adminAuth;

    @GetMapping
    public List<CalendarDecorationDto> list() {
        return service.findAll();
    }

    @PostMapping
    public CalendarDecorationDto create(
            @RequestParam String title,
            @RequestParam String kind,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) String unitName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) MultipartFile image,
            HttpServletRequest request, HttpSession session) {
        adminAuth.requireAdmin(request, session);
        return service.create(title, kind, eventDate, unitName, description, color, image);
    }

    @PostMapping("/{id}")
    public CalendarDecorationDto update(
            @PathVariable long id,
            @RequestParam String title,
            @RequestParam String kind,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) String unitName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(defaultValue = "false") boolean clearImage,
            HttpServletRequest request, HttpSession session) {
        adminAuth.requireAdmin(request, session);
        return service.update(id, title, kind, eventDate, unitName, description, color, image, clearImage);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        adminAuth.requireAdmin(request, session);
        service.delete(id);
    }
}
