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

/** 설명 수정 때 고른 멤버·번호 선택. 같은 시즌·번호 카드에서 다음 수정에 복원한다. */
@Entity
@Table(name = "objekt_description_share_pref")
@Getter @Setter @NoArgsConstructor
public class ObjektDescriptionSharePref {
    @EmbeddedId private Key id;

    @Column(nullable = false, length = 1024)
    private String members = "";

    @Column(nullable = false, length = 2048)
    private String numbers = "";

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(nullable = false, length = 64) private String season;
        @Column(name = "collection_no", nullable = false, length = 32) private String collectionNo;
    }
}
