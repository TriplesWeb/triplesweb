package com.example.triplesweb.entity;

import com.example.triplesweb.util.ContentLinks;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 아티스트(멤버) 프로필 엔티티. {@link UnitMember}로 유닛 소속 1:N.
 */
@Entity
@Table(name = "artist")
@Getter
@Setter
@ToString (exclude = {"affiliatedUnit"})
@NoArgsConstructor
@AllArgsConstructor


public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long artistId;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String engName;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = false)
    private String birthDate;

    @Column(nullable = false)
    private String birthPlace;

    @Column(nullable = false)
    private String debutDate;

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnitMember> affiliatedUnit = new ArrayList<>();

    private String profileImage;

    @Column(nullable = false)
    private String color;

    /** 멤버 단독 컨텐츠 영상 링크 JSON. [{"title","url"}, ...] */
    @Column(columnDefinition = "TEXT")
    private String soloContent;

    /** 출연한 방송 영상 링크 JSON */
    @Column(columnDefinition = "TEXT")
    private String broadcastContent;

    @Transient
    public List<Map<String, String>> getSoloLinks() {
        return ContentLinks.parse(soloContent);
    }

    @Transient
    public boolean isHasSoloLinks() {
        return !getSoloLinks().isEmpty();
    }

    @Transient
    public List<Map<String, String>> getBroadcastLinks() {
        return ContentLinks.parse(broadcastContent);
    }

    @Transient
    public boolean isHasBroadcastLinks() {
        return !getBroadcastLinks().isEmpty();
    }
}
