package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "objekt_description_scope")
@Getter @Setter @NoArgsConstructor
public class ObjektDescriptionScope {
    @EmbeddedId private Key id;

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(nullable = false, length = 64) private String season;
        @Column(name = "collection_no", nullable = false, length = 32) private String collectionNo;
    }
}
