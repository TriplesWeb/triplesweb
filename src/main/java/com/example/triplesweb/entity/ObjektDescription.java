package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "objekt_description", uniqueConstraints = @UniqueConstraint(name = "uk_objekt_description_key", columnNames = "objekt_key"))
@Getter @Setter @NoArgsConstructor
public class ObjektDescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "objekt_key", nullable = false, length = 255, unique = true) private String objektKey;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @PrePersist @PreUpdate public void updateTimestamp() { updatedAt = LocalDateTime.now(); }
}