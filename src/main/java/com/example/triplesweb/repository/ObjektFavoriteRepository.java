package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ObjektFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ObjektFavoriteRepository extends JpaRepository<ObjektFavorite, Long> {
    List<ObjektFavorite> findByVisitorKeyOrderByCreatedAtDesc(String visitorKey);
    Optional<ObjektFavorite> findByVisitorKeyAndObjektKey(String visitorKey, String objektKey);
    boolean existsByVisitorKeyAndObjektKey(String visitorKey, String objektKey);
    void deleteByVisitorKeyAndObjektKey(String visitorKey, String objektKey);
}