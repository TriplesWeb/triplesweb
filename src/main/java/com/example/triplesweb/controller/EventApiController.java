package com.example.triplesweb.controller;

import com.example.triplesweb.dto.EventAdminDto;
import com.example.triplesweb.dto.EventSectionDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.EventAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventApiController {

    private final EventAdminService eventAdminService;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.status(request, session);
    }

    @GetMapping("/sections")
    public List<EventSectionDto> sections() {
        return eventAdminService.listSections();
    }

    @PostMapping("/sections")
    public EventSectionDto createSection(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return eventAdminService.createSection(body != null ? body.get("name") : null);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/sections/{id:[0-9]+}")
    public EventSectionDto updateSection(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            String name = body != null && body.get("name") != null ? String.valueOf(body.get("name")) : null;
            Integer sortOrder = null;
            if (body != null && body.get("sortOrder") instanceof Number n) {
                sortOrder = n.intValue();
            }
            return eventAdminService.updateSection(id, name, sortOrder);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/sections/{id:[0-9]+}/move")
    public List<EventSectionDto> moveSection(
            @PathVariable long id,
            @RequestParam int direction,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return eventAdminService.moveSection(id, direction);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/sections/{id:[0-9]+}")
    public Map<String, Object> deleteSection(
            @PathVariable long id,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            eventAdminService.deleteSection(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public EventAdminDto get(@PathVariable long id) {
        try {
            return eventAdminService.get(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public EventAdminDto create(
            @RequestParam String eventName,
            @RequestParam(required = false, defaultValue = "") String eventInfo,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "[]") String stopsJson,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return eventAdminService.create(eventName, eventInfo, sectionId, stopsJson, image, imageUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public EventAdminDto update(
            @PathVariable long id,
            @RequestParam String eventName,
            @RequestParam(required = false, defaultValue = "") String eventInfo,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "[]") String stopsJson,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return eventAdminService.update(
                    id, eventName, eventInfo, sectionId, stopsJson, image, imageUrl, clearImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id:[0-9]+}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            eventAdminService.delete(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
