package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ObjektDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ObjektDescriptionRepository extends JpaRepository<ObjektDescription, Long> {
    Optional<ObjektDescription> findByObjektKey(String objektKey);
}