package com.example.triplesweb.controller;

import com.example.triplesweb.dto.CalendarDecorationDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CalendarDecorationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/calendar-decorations")
@RequiredArgsConstructor
public class CalendarDecorationController {
    // bite 이미지 번호는 artistId가 아니라 고정된 멤버 순서로 매핑한다.
    private static final List<String> BITE_IMAGE_ORDER = List.of(
            "seoyeon", "hyerin", "jiwoo", "chaeyeon", "yooyeon", "soomin",
            "nakyoung", "yubin", "kaede", "dahyun", "kotone", "yeonji",
            "nien", "sohyun", "xinyu", "mayu", "lynn", "joobin",
            "hayeon", "shion", "chaewon", "sullin", "seoah", "jiyeon"
    );
    private static final Pattern BIRTH_DATE = Pattern.compile("(?:19|20)\\d{2}\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})");
    private final CalendarDecorationService service;
    private final ArtistRepository artistRepository;
    private final AboutAdminAuth adminAuth;

    @GetMapping
    public List<CalendarDecorationDto> list() {
        return service.findAll();
    }

    /** Artist.birthDate에서 월/일을 추출해 조회 범위에 맞는 매년 반복 생일을 반환한다. */
    @GetMapping("/birthdays")
    public List<Map<String, Object>> birthdays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 400) {
            throw new IllegalArgumentException("생일 조회 기간은 400일 이내여야 합니다.");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Artist artist : artistRepository.findAll()) {
            if (artist.getBirthDate() == null || artist.getName() == null || artist.getName().isBlank()) continue;
            Matcher matcher = BIRTH_DATE.matcher(artist.getBirthDate());
            if (!matcher.find()) continue;
            int month = Integer.parseInt(matcher.group(1));
            int day = Integer.parseInt(matcher.group(2));
            for (int year = start.getYear(); year <= end.getYear(); year++) {
                try {
                    LocalDate birthday = LocalDate.of(year, month, day);
                    if (birthday.isBefore(start) || birthday.isAfter(end)) continue;
                    Map<String, Object> birthdayEvent = new java.util.LinkedHashMap<>(Map.of(
                            "id", "birthday-" + artist.getArtistId() + "-" + year,
                            "title", artist.getName() + " 생일",
                            "start", birthday.toString(),
                            "color", artist.getColor() == null ? "#00f5d4" : artist.getColor(),
                            "categoryLabel", "생일",
                            "kind", "BIRTHDAY",
                            "artistName", artist.getName(),
                            "description", artist.getName() + "의 생일입니다."
                    ));
                    int biteImageNumber = BITE_IMAGE_ORDER.indexOf(artist.getEngName()) + 1;
                    if (biteImageNumber > 0) {
                        birthdayEvent.put("imageUrl", "/images/artist/bite/" + biteImageNumber + ".png");
                    }
                    result.add(birthdayEvent);
                } catch (DateTimeException ignored) {
                    // 윤년이 아닌 해의 2월 29일은 표시하지 않는다.
                }
            }
        }
        return result;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
