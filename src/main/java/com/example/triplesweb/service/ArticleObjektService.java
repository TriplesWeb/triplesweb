package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.repository.ArticleObjektRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleObjektService {
    public static final String SIDE_HAVE = "HAVE";
    public static final String SIDE_WANT = "WANT";
    public static final int MAX_LINKED = 20;

    private final ArticleObjektRepository repository;
    private final ObjektService objektService;

    /** Flat list (sell / group-orders): all stored as HAVE. */
    @Transactional
    public void attachSelected(Article article, List<String> selectedKeys) {
        attachSelected(article, selectedKeys, null);
    }

    @Transactional
    public void attachSelected(Article article, List<String> haveKeys, List<String> wantKeys) {
        if (article == null) return;
        List<Map<String, Object>> all = objektService.findCollections("triples");
        Set<String> stored = new LinkedHashSet<>();
        attachSide(article, haveKeys, SIDE_HAVE, all, stored);
        attachSide(article, wantKeys, SIDE_WANT, all, stored);
    }

    /** 기존 연결을 지우고 선택 목록으로 다시 저장한다. 빈 목록이면 연결만 제거한다. */
    @Transactional
    public void replaceSelected(Article article, List<String> selectedKeys) {
        replaceSelected(article, selectedKeys, null);
    }

    @Transactional
    public void replaceSelected(Article article, List<String> haveKeys, List<String> wantKeys) {
        if (article == null || article.getId() == null) return;
        // Hibernate는 flush 때 INSERT를 DELETE보다 먼저 실행한다.
        // 기존 행을 지운 SQL이 먼저 나가게 해야 uk_article_objekt 중복이 나지 않는다.
        repository.deleteByArticle_Id(article.getId());
        repository.flush();
        attachSelected(article, haveKeys, wantKeys);
    }

    @Transactional(readOnly = true)
    public List<ArticleObjekt> findForArticle(Long articleId) {
        return repository.findByArticle_IdOrderByIdAsc(articleId);
    }

    @Transactional(readOnly = true)
    public List<ArticleObjekt> findHaveForArticle(Long articleId) {
        return filterSide(findForArticle(articleId), SIDE_HAVE);
    }

    @Transactional(readOnly = true)
    public List<ArticleObjekt> findWantForArticle(Long articleId) {
        return filterSide(findForArticle(articleId), SIDE_WANT);
    }

    @Transactional
    public void deleteForArticle(Long articleId) {
        repository.deleteByArticle_Id(articleId);
    }

    /** Trade posts require at least one Have and one Want. */
    public static String validateTradeSides(List<String> haveKeys, List<String> wantKeys) {
        List<String> have = cleanKeys(haveKeys);
        List<String> want = cleanKeys(wantKeys);
        if (have.isEmpty()) return "Have 오브젝트를 1개 이상 선택해주세요.";
        if (want.isEmpty()) return "Want 오브젝트를 1개 이상 선택해주세요.";
        if (have.size() + want.size() > MAX_LINKED) {
            return "오브젝트는 최대 " + MAX_LINKED + "개까지 선택할 수 있습니다.";
        }
        Set<String> overlap = new LinkedHashSet<>(have);
        overlap.retainAll(new LinkedHashSet<>(want));
        if (!overlap.isEmpty()) return "같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.";
        return null;
    }

    public static List<String> cleanKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : keys) {
            if (raw == null || raw.isBlank()) continue;
            unique.add(raw.trim());
        }
        return new ArrayList<>(unique);
    }

    private void attachSide(Article article,
                            List<String> selectedKeys,
                            String side,
                            List<Map<String, Object>> all,
                            Set<String> stored) {
        if (selectedKeys == null || selectedKeys.isEmpty()) return;
        for (String raw : selectedKeys) {
            if (raw == null || raw.isBlank() || stored.size() >= MAX_LINKED) continue;
            String key = raw.trim();
            Map<String, Object> item = all.stream()
                    .filter(o -> key.equals(value(o, "id"))
                            || key.equals(value(o, "collectionId"))
                            || key.equals(value(o, "slug")))
                    .findFirst()
                    .orElse(null);
            if (item == null) continue;
            String objektKey = value(item, "id");
            if (objektKey.isBlank()) objektKey = key;
            if (!stored.add(objektKey)) continue;
            ArticleObjekt linked = new ArticleObjekt();
            linked.setArticle(article);
            linked.setObjektKey(objektKey);
            linked.setSide(side);
            linked.setCollectionId(value(item, "collectionId"));
            linked.setMemberName(fit(com.example.triplesweb.util.MemberPortraitOptions.displayMember(value(item, "member")), 100));
            linked.setThumbnailImage(value(item, "thumbnailImage"));
            linked.setFrontImage(value(item, "frontImage"));
            linked.setBackImage(value(item, "backImage"));
            linked.setSeason(value(item, "season"));
            linked.setObjektClass(value(item, "class"));
            linked.setCollectionNo(value(item, "collectionNo"));
            repository.save(linked);
        }
    }

    private static List<ArticleObjekt> filterSide(List<ArticleObjekt> links, String side) {
        List<ArticleObjekt> out = new ArrayList<>();
        for (ArticleObjekt link : links) {
            String value = link.getSide();
            if (value == null || value.isBlank()) value = SIDE_HAVE;
            if (side.equalsIgnoreCase(value)) out.add(link);
        }
        return out;
    }

    private static String value(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private static String fit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
