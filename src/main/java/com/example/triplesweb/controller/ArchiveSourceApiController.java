package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveSourceService;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/archive/sources")
@RequiredArgsConstructor
public class ArchiveSourceApiController {

    private final ArchiveSourceService archiveSourceService;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping
    public List<ArchiveSourceDto> list(@RequestParam(required = false) boolean all,
                                       HttpServletRequest request,
                                       HttpSession session) {
        if (all) {
            aboutAdminAuth.requireAdmin(request, session);
            return archiveSourceService.listAll();
        }
        return archiveSourceService.listVisible();
    }

    @PostMapping
    public ArchiveSourceDto create(@RequestBody Map<String, Object> body,
                                   HttpServletRequest request,
                                   HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return archiveSourceService.create(
                    stringVal(body, "code"),
                    stringVal(body, "label"),
                    stringVal(body, "color"),
                    intVal(body, "sortOrder"),
                    boolVal(body, "visible"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}")
    public ArchiveSourceDto update(@PathVariable long id,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest request,
                                   HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return archiveSourceService.update(
                    id,
                    stringVal(body, "label"),
                    stringVal(body, "color"),
                    intVal(body, "sortOrder"),
                    boolVal(body, "visible"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id:[0-9]+}")
    public Map<String, Object> delete(@PathVariable long id,
                                      HttpServletRequest request,
                                      HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            archiveSourceService.delete(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String stringVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 값이 올바르지 않습니다.");
        }
    }

    private static Boolean boolVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
