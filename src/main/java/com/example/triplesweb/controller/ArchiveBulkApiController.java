package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArchiveSourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class ArchiveBulkApiController {

    private final ArchiveService archiveService;
    private final ArchiveSourceService archiveSourceService;
    private final AboutAdminAuth aboutAdminAuth;

    @PostMapping("/bulk-source")
    public Map<String, Object> bulkUpdateSource(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request,
                                                HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        List<Long> ids = parseIds(body.get("ids"));
        String sourceRaw = stringVal(body.get("source"));
        try {
            String sourceCode = archiveSourceService.requireExisting(sourceRaw).getCode();
            int updated = archiveService.bulkUpdateSource(ids, sourceCode);
            return Map.of("updated", updated);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static List<Long> parseIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자료를 선택해 주세요.");
        }
        List<Long> ids = new ArrayList<>(list.size());
        for (Object item : list) {
            Long id = toLong(item);
            if (id == null || id <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자료를 선택해 주세요.");
            }
            ids.add(id);
        }
        return ids;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
