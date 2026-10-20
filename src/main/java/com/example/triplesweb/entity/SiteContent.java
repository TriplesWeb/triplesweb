package com.example.triplesweb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 키-값 사이트 문구(About 그룹 소개 등).
 */
@Entity
@Table(name = "site_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SiteContent {

    @Id
    @Column(name = "content_key", length = 64)
    private String contentKey;

    @Lob
    @Column(name = "content_value", nullable = false, columnDefinition = "LONGTEXT")
    private String contentValue;
}
