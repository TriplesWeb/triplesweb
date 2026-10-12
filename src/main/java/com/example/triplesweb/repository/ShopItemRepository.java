package com.example.triplesweb.repository;

import com.example.triplesweb.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopItemRepository extends JpaRepository<Shop, Long> {
        List<Shop> findByCategory(String category);
    }

