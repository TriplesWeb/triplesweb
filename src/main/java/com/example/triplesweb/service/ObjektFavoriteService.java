package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektFavorite;
import com.example.triplesweb.repository.ObjektFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ObjektFavoriteService {
    private final ObjektFavoriteRepository repository;

    public String key(Map<String, Object> item) {
        return value(item, "id", "slug", "collectionId");
    }

    public List<String> aliases(Map<String, Object> item) {
        return List.of("id", "slug", "collectionId").stream().map(item::get).filter(Objects::nonNull)
                .map(Object::toString).filter(s -> !s.isBlank()).distinct().toList();
    }

    public boolean contains(String visitor, Map<String, Object> item) {
        return repository.existsByVisitorKeyAndObjektKeyIn(visitor, aliases(item));
    }

    @Transactional
    public void set(String visitor, Map<String, Object> item, boolean desired) {
        if (!desired) {
            repository.deleteByVisitorKeyAndObjektKeyIn(visitor, aliases(item));
            return;
        }
        if (contains(visitor, item)) return;
        ObjektFavorite favorite = new ObjektFavorite();
        favorite.setVisitorKey(visitor);
        favorite.setObjektKey(key(item));
        favorite.setCollectionId(value(item, "collectionId", "slug"));
        favorite.setMemberName(value(item, "member", "memberName"));
        favorite.setThumbnailImage(value(item, "thumbnailImage"));
        favorite.setFrontImage(value(item, "frontImage"));
        favorite.setBackImage(value(item, "backImage"));
        favorite.setSeason(value(item, "season"));
        favorite.setObjektClass(value(item, "class"));
        favorite.setCollectionNo(value(item, "collectionNo", "collectionNumber"));
        repository.saveAndFlush(favorite);
    }

    @Transactional
    public void remove(String visitor, String key) {
        repository.deleteByVisitorKeyAndObjektKey(visitor, key);
    }

    private static String value(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return "";
    }
}
