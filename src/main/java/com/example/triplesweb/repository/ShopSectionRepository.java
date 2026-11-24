package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ShopSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopSectionRepository extends JpaRepository<ShopSection, Long> {
    List<ShopSection> findAllByOrderBySortOrderAscSectionIdAsc();
}
