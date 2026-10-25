package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.repository.ArticleObjektRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleObjektService {
    private final ArticleObjektRepository repository;
    private final ObjektService objektService;

    @Transactional
    public void attachSelected(Article article, List<String> selectedKeys) {
        if (article == null || selectedKeys == null || selectedKeys.isEmpty()) return;
        List<Map<String, Object>> all = objektService.findCollections("triples");
        Set<String> stored = new LinkedHashSet<>();
        for (String raw : selectedKeys) {
            if (raw == null || raw.isBlank() || stored.size() >= 20) continue;
            String key = raw.trim();
            Map<String, Object> item = all.stream().filter(o -> key.equals(value(o, "id")) || key.equals(value(o, "collectionId")) || key.equals(value(o, "slug"))).findFirst().orElse(null);
            if (item == null) continue;
            String objektKey = value(item, "id");
            if (objektKey.isBlank()) objektKey = key;
            if (!stored.add(objektKey)) continue;
            ArticleObjekt linked = new ArticleObjekt();
            linked.setArticle(article); linked.setObjektKey(objektKey);
            linked.setCollectionId(value(item, "collectionId"));
            linked.setMemberName(fit(com.example.triplesweb.util.MemberPortraitOptions.displayMember(value(item, "member")), 100));
            linked.setThumbnailImage(value(item, "thumbnailImage")); linked.setFrontImage(value(item, "frontImage"));
            linked.setBackImage(value(item, "backImage")); linked.setSeason(value(item, "season"));
            linked.setObjektClass(value(item, "class")); linked.setCollectionNo(value(item, "collectionNo"));
            repository.save(linked);
        }
    }

    /** 기존 연결을 지우고 선택 목록으로 다시 저장한다. 빈 목록이면 연결만 제거한다. */
    @Transactional
    public void replaceSelected(Article article, List<String> selectedKeys) {
        if (article == null || article.getId() == null) return;
        // Hibernate는 flush 때 INSERT를 DELETE보다 먼저 실행한다.
        // 기존 행을 지운 SQL이 먼저 나가게 해야 uk_article_objekt 중복이 나지 않는다.
        repository.deleteByArticle_Id(article.getId());
        repository.flush();
        attachSelected(article, selectedKeys);
    }

    @Transactional(readOnly = true) public List<ArticleObjekt> findForArticle(Long articleId) { return repository.findByArticle_IdOrderByIdAsc(articleId); }
    @Transactional public void deleteForArticle(Long articleId) { repository.deleteByArticle_Id(articleId); }
    private static String value(Map<String,Object> map, String key) { Object v=map.get(key); return v==null ? "" : v.toString(); }
    private static String fit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}