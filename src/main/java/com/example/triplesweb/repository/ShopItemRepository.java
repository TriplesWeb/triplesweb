package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쇼핑 상품({@link Shop}) JPA 저장소.
 * {@link #findByCategory(String)}로 카테고리별 상품 목록을 조회한다.
 */
public interface ShopItemRepository extends JpaRepository<Shop, Long> {
        List<Shop> findByCategory(String category);
    }

