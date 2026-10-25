package com.example.triplesweb.controller;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektFavoriteRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.ObjektFavoriteService;
import com.example.triplesweb.service.ObjektMarketDataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/cosmo/objekt")
@RequiredArgsConstructor
public class ObjektFeatureController {
    private static final int MEMBER_PAGE_SIZE = 20;
    private final ObjektService objektService;
    private final ObjektFavoriteRepository favoriteRepository;
    private final ObjektDescriptionRepository descriptionRepository;
    private final AboutAdminAuth aboutAdminAuth;
    private final ObjektFavoriteService favorites;
    private final ObjektMarketDataService marketData;

    @GetMapping("/member")
    public String memberObjekts(@RequestParam String name,
                                @RequestParam(defaultValue = "0") int page,
                                Model model) {
        List<Map<String, Object>> matches = objektService.findCollections("triples").stream()
                .filter(item -> normalize(value(item, "member", "memberName")).equals(normalize(name)))
                .sorted(Comparator.comparing(item -> value(item, "createdAt", "date"), Comparator.reverseOrder()))
                .toList();
        int total = matches.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) MEMBER_PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(safePage * MEMBER_PAGE_SIZE, total);
        int to = Math.min(from + MEMBER_PAGE_SIZE, total);
        model.addAttribute("memberName", name);
        model.addAttribute("objekts", matches.subList(from, to));
        model.addAttribute("objektCount", total);
        model.addAttribute("pageDisplay", safePage + 1);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPreviousPage", safePage > 0);
        model.addAttribute("hasNextPage", safePage + 1 < totalPages);
        model.addAttribute("previousPage", Math.max(0, safePage - 1));
        model.addAttribute("nextPage", Math.min(totalPages - 1, safePage + 1));
        model.addAttribute("encodedMemberName", URLEncoder.encode(name, StandardCharsets.UTF_8));
        return "cosmo/objekt_member";
    }

    @GetMapping("/detail/{key}")
    public String detail(@PathVariable String key, HttpServletRequest request, HttpSession session, Model model) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return "redirect:/cosmo/objekt";
        model.addAttribute("objekt", item);
        model.addAttribute("objektKey", key);
        boolean isFavorite = favorites.contains(session.getId(), item);
        model.addAttribute("isFavorite", isFavorite);
        model.addAttribute("favoriteDesired", !isFavorite);
        model.addAttribute("objektDescription", descriptionRepository.findByObjektKey(key)
                .map(ObjektDescription::getDescription).orElse(""));
        model.addAttribute("isAdmin", aboutAdminAuth.isAdmin(request, session));
        return "cosmo/objekt_detail";
    }

    @PostMapping("/detail/{key}/description")
    public String saveDescription(@PathVariable String key,
                                  @RequestParam(required = false) String description,
                                  HttpServletRequest request,
                                  HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        if (findObjekt(key) == null) return "redirect:/cosmo/objekt";
        ObjektDescription entity = descriptionRepository.findByObjektKey(key)
                .orElseGet(ObjektDescription::new);
        entity.setObjektKey(key);
        entity.setDescription(description == null ? "" : description.trim());
        descriptionRepository.save(entity);
        return "redirect:/cosmo/objekt/detail/" + encodePath(key);
    }

    @PostMapping("/detail/{key}/favorite")
    public String toggleFavorite(@PathVariable String key,
                                 @RequestParam(required = false) Boolean favorite, HttpSession session) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return "redirect:/cosmo/objekt";
        setFavorite(session.getId(), item, favorite == null ? !favorites.contains(session.getId(), item) : favorite);
        return "redirect:/cosmo/objekt/detail/" + encodePath(key);
    }

    @PutMapping("/detail/{key}/favorite")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setFavorite(@PathVariable String key,
            @RequestParam boolean favorite, HttpSession session) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        setFavorite(session.getId(), item, favorite);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("favorite", favorites.contains(session.getId(), item)));
    }

    private void setFavorite(String visitor, Map<String, Object> item, boolean desired) {
        try {
            favorites.set(visitor, item, desired);
        } catch (DataIntegrityViolationException e) {
            // Another request may have inserted the same canonical favorite first.
            if (!desired || !favorites.contains(visitor, item)) throw e;
        }
    }

    @PostMapping("/favorites/remove")
    public String removeFavorite(@RequestParam String key, HttpSession session) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) favorites.remove(session.getId(), key);
        else favorites.set(session.getId(), item, false);
        return "redirect:/cosmo/objekt/favorites";
    }

    @GetMapping("/detail/{key}/owner")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> owner(@PathVariable String key, @RequestParam long serial) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        if (serial < 1) return ResponseEntity.badRequest().body(Map.of("message", "시리얼은 1 이상의 정수로 입력해 주세요."));
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(marketData.owner(item, serial));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("message", "소유자 정보를 검증하지 못했습니다. 잠시 후 다시 조회해 주세요."));
        }
    }

    @GetMapping("/favorites")
    public String favorites(HttpSession session, Model model) {
        model.addAttribute("favorites", favoriteRepository.findByVisitorKeyOrderByCreatedAtDesc(session.getId()));
        return "cosmo/objekt_favorites";
    }

    private Map<String, Object> findObjekt(String key) {
        return objektService.findCollections("triples").stream()
                .filter(item -> key.equals(value(item, "id")) || key.equals(value(item, "collectionId"))
                        || key.equals(value(item, "slug")))
                .findFirst().orElse(null);
    }

    private static String value(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return "";
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
