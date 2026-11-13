package com.example.triplesweb.controller;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektFavoriteRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.ObjektDescriptionEditor;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.ObjektFavoriteService;
import com.example.triplesweb.service.ObjektMarketDataService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import com.example.triplesweb.util.ObjektLinks;
import com.example.triplesweb.util.ObjektMotionMedia;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final ObjektDescriptionEditor descriptionEditor;
    private final AboutAdminAuth aboutAdminAuth;
    private final ObjektFavoriteService favorites;
    private final ObjektMarketDataService marketData;
    private final ArtistSnapshotService artistSnapshot;

    @GetMapping("/member")
    public String memberObjekts(@RequestParam String name,
                                @RequestParam(defaultValue = "0") int page,
                                Model model) {
        /* Legacy member pages now lead into the shared catalog with the member selected. */
        return "redirect:/cosmo/objekt?group=members&member=" + ObjektLinks.encode(name);
    }

    @GetMapping(value = "/detail/{key}", produces = MediaType.TEXT_HTML_VALUE)
    public String detail(@PathVariable String key, HttpServletRequest request, HttpSession session, Model model) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return "redirect:/cosmo/objekt";
        return "redirect:/cosmo/objekt?objekt=" + ObjektLinks.encode(ObjektLinks.key(item));
    }

    /** 목록 모달용 상세 JSON. */
    @GetMapping(value = "/detail/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> detailJson(@PathVariable String key,
                                          HttpServletRequest request,
                                          HttpSession session) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "오브젝트를 찾을 수 없습니다.");
        }
        String front = value(item, "frontImage", "thumbnailImage");
        String back = value(item, "backImage");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", value(item, "id"));
        body.put("objektKey", ObjektLinks.key(item));
        body.put("collectionId", value(item, "collectionId", "slug"));
        body.put("member", value(item, "member", "memberName"));
        body.put("season", value(item, "season"));
        body.put("class", value(item, "class"));
        body.put("collectionNo", value(item, "collectionNo", "collectionNumber"));
        body.put("thumbnailImage", value(item, "thumbnailImage", "frontImage"));
        body.put("frontImage", front);
        body.put("backImage", back);
        body.put("slug", value(item, "slug"));
        body.put("artist", value(item, "artist"));
        body.put("frontMedia", ObjektMotionMedia.resolve(item));
        body.put("hasBack", !back.isBlank() && !back.equals(front));
        body.put("description", description(item, key));
        boolean admin = aboutAdminAuth.isAdmin(request, session);
        body.putAll(descriptionEditor.view(item, admin));
        body.put("isFavorite", favorites.contains(session.getId(), item));
        body.put("favoriteCount", favorites.count(session.getId()));
        body.put("isAdmin", admin);
        body.put("detailUrl", ObjektLinks.detail(item));
        body.put("memberUrl", ObjektLinks.member(item));
        String member = value(item, "member", "memberName");
        body.put("memberColor", MemberPortraitOptions.artistColor(member, artistSnapshot.findAll()));
        body.put("tradeUrl", ObjektLinks.compose(item, "trade"));
        body.put("sellUrl", ObjektLinks.compose(item, "sell"));
        body.put("descriptionUrl", ObjektLinks.detail(item) + "/description");
        body.put("lookupUrl", ObjektLinks.detail(item) + "/lookup");
        body.put("issuedUrl", ObjektLinks.detail(item) + "/issued");
        Long mint = marketData.mintCount(item);
        if (mint != null) {
            body.put("mintCount", mint);
            body.put("mintCountKnown", true);
        }
        return body;
    }

    @GetMapping("/detail/{key}/issued")
    @ResponseBody
    public Map<String,Object> issued(@PathVariable String key,@RequestParam(defaultValue="0") int offset) {
        Map<String,Object> item=findObjekt(key);
        if(item==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return marketData.issuedSupply(item,Math.max(0,offset),40);
    }

    @GetMapping("/supplies")
    @ResponseBody
    public Map<String,Object> supplies(@RequestParam(name="key") List<String> keys) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:keys.stream().distinct().limit(24).toList()) {
            Map<String,Object> item=findObjekt(key);
            if(item!=null)result.put(key,marketData.issuedSupply(item,0,1));
        }
        return result;
    }

    @PostMapping(value = "/detail/{key}/description", produces = MediaType.TEXT_HTML_VALUE)
    public String saveDescription(@PathVariable String key,
                                  @RequestParam(required = false) String description,
                                  @RequestParam(required = false) String scope,
                                  @RequestParam(required = false) List<String> members,
                                  @RequestParam(required = false) List<String> numbers,
                                  HttpServletRequest request,
                                  HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        Map<String, Object> item = findObjekt(key);
        if (item == null) return "redirect:/cosmo/objekt";
        if (description != null && description.length() > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        descriptionEditor.save(item, description, scope, members, numbers);
        return "redirect:" + ObjektLinks.detail(item);
    }

    @PostMapping(value = "/detail/{key}/description", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDescriptionJson(@PathVariable String key,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) List<String> members,
            @RequestParam(required = false) List<String> numbers,
            HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        if (description.length() > 10000) return ResponseEntity.badRequest().body(Map.of("message", "설명은 10,000자까지 입력할 수 있습니다."));
        String saved = descriptionEditor.save(item, description, scope, members, numbers);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", saved);
        // 저장 응답은 메타만 빠르게 맞추고, 공유 옵션은 다음 수정 진입 때 다시 읽는다.
        body.putAll(descriptionEditor.view(item, false));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }

    @GetMapping("/detail/{key}/lookup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> serialLookup(@PathVariable String key, @RequestParam long serial,
            @RequestParam(defaultValue = "0") int page) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        if (serial < 1 || page < 0) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(marketData.serialLookup(item, serial, page));
        } catch (RuntimeException error) {
            return ResponseEntity.status(503).cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of("message", "조회하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }

    private Optional<ObjektDescription> descriptionEntity(Map<String, Object> item, String requested) {
        return ObjektDescriptionKeys.readKeys(item, requested).stream()
                .map(descriptionRepository::findByObjektKey)
                .flatMap(Optional::stream)
                .findFirst();
    }
    private String description(Map<String, Object> item, String key) {
        return descriptionEntity(item, key).map(ObjektDescription::getDescription).orElse("");
    }

    @PostMapping(value = "/detail/{key}/favorite", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleFavoriteJson(@PathVariable String key, HttpSession session) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        boolean desired = !favorites.contains(session.getId(), item);
        setFavorite(session.getId(), item, desired);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(favoriteState(session.getId(), item, key));
    }

    @PostMapping(value = "/detail/{key}/favorite", produces = MediaType.TEXT_HTML_VALUE)
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
                .body(favoriteState(session.getId(), item, key));
    }

    private Map<String, Object> favoriteState(String visitor, Map<String, Object> item, String key) {
        boolean on = favorites.contains(visitor, item);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("favorite", on);
        body.put("isFavorite", on);
        body.put("favoriteCount", favorites.count(visitor));
        body.put("objektKey", key);
        return body;
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
            Map<String, Object> result = marketData.owner(item, serial);
            // Send only the fields rendered by the public owner lookup.
            Map<String, Object> publicResult = new LinkedHashMap<>();
            for (String field : List.of("status", "serial", "nickname")) {
                if (result.containsKey(field)) publicResult.put(field, result.get(field));
            }
            String nickname = String.valueOf(result.getOrDefault("nickname", ""));
            if (!nickname.isBlank()) publicResult.put("profileUrl", "/cosmo/profile/" + encodePath(nickname));
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(publicResult);
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("message", "소유자 정보를 검증하지 못했습니다. 잠시 후 다시 조회해 주세요."));
        }
    }

    @GetMapping("/detail/{key}/transfers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transfers(@PathVariable String key,
            @RequestParam long serial, @RequestParam(defaultValue = "0") int page) {
        Map<String, Object> item = findObjekt(key);
        if (item == null) return ResponseEntity.notFound().build();
        if (serial < 1 || page < 0) return ResponseEntity.badRequest()
                .body(Map.of("message", "시리얼과 페이지를 확인해 주세요."));
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(marketData.transfers(item, serial, page));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of("message", "전송 내역을 불러오지 못했습니다. 잠시 후 다시 조회해 주세요."));
        }
    }

    @GetMapping("/favorites")
    public String favorites(HttpSession session, Model model) {
        model.addAttribute("favorites", favoriteRepository.findByVisitorKeyOrderByCreatedAtDesc(session.getId()));
        return "cosmo/objekt_favorites";
    }

    private Map<String, Object> findObjekt(String key) {
        return objektService.findByKey(key);
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

