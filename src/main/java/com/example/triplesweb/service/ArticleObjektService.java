package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.repository.ArticleObjektRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticleObjektService {
    private final ArticleObjektRepository repository;
    private final ObjektService objektService;

    @Transactional
    public void attachSelected(Article article, List<String> selectedKeys) {
        if (article == null || selectedKeys == null || selectedKeys.isEmpty()) return;
        List<Map<String, Object>> all = objektService.findCollections("triples");
        for (String key : selectedKeys.stream().filter(v -> v != null && !v.isBlank()).distinct().limit(20).toList()) {
            Map<String, Object> item = all.stream().filter(o -> key.equals(value(o, "id")) || key.equals(value(o, "collectionId")) || key.equals(value(o, "slug"))).findFirst().orElse(null);
            if (item == null) continue;
            ArticleObjekt linked = new ArticleObjekt();
            linked.setArticle(article); linked.setObjektKey(key);
            linked.setCollectionId(value(item, "collectionId")); linked.setMemberName(value(item, "member"));
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
        deleteForArticle(article.getId());
        attachSelected(article, selectedKeys);
    }

    @Transactional(readOnly = true) public List<ArticleObjekt> findForArticle(Long articleId) { return repository.findByArticle_IdOrderByIdAsc(articleId); }
    @Transactional public void deleteForArticle(Long articleId) { repository.deleteByArticle_Id(articleId); }
    private static String value(Map<String,Object> map, String key) { Object v=map.get(key); return v==null ? "" : v.toString(); }
}