package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

/** 아카이브 자료 출처. 관리자가 추가·수정·삭제한다. */
@Entity
@Table(name = "archive_source")
@Getter
@Setter
@NoArgsConstructor
public class ArchiveSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** archive.source 에 저장하는 키. 생성 후 변경하지 않는다. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(nullable = false, length = 7)
    private String color = "#00f5d4";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean visible = true;

    public String getSlug() {
        return code == null ? "" : code.toLowerCase(Locale.ROOT);
    }
}
