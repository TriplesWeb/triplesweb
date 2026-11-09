package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ShopAdminDto;
import com.example.triplesweb.dto.ShopSectionDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ShopAdminService;
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
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopApiController {

    private final ShopAdminService shopAdminService;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.status(request, session);
    }

    @GetMapping("/sections")
    public List<ShopSectionDto> sections() {
        return shopAdminService.listSections();
    }

    @GetMapping("/events")
    public List<ShopAdminDto.EventOption> events() {
        return shopAdminService.listEventOptions();
    }

    @PostMapping("/sections")
    public ShopSectionDto createSection(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return shopAdminService.createSection(body != null ? body.get("name") : null);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/sections/{id:[0-9]+}")
    public ShopSectionDto updateSection(
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
            return shopAdminService.updateSection(id, name, sortOrder);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/sections/{id:[0-9]+}/move")
    public List<ShopSectionDto> moveSection(
            @PathVariable long id,
            @RequestParam int direction,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return shopAdminService.moveSection(id, direction);
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
            shopAdminService.deleteSection(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public ShopAdminDto get(@PathVariable long id) {
        try {
            return shopAdminService.get(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public ShopAdminDto create(
            @RequestParam String title,
            @RequestParam(required = false) Long eventId,
            @RequestParam String salesPeriod,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "") String mdList,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return shopAdminService.create(title, eventId, salesPeriod, sectionId, mdList, image, imageUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public ShopAdminDto update(
            @PathVariable long id,
            @RequestParam String title,
            @RequestParam(required = false) Long eventId,
            @RequestParam String salesPeriod,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "") String mdList,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return shopAdminService.update(id, title, eventId, salesPeriod, sectionId, mdList, image, imageUrl, clearImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id:[0-9]+}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            shopAdminService.delete(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
