package com.example.triplesweb.entity;

import com.example.triplesweb.util.ContentLinks;
import com.example.triplesweb.dto.MediaDto;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 출생지 (출생 칸에 생년월일과 함께 표시). 출신지와 별개. */
    private String bornPlace;

    /** 출신지 (거주지/성장지). 비어 있으면 프로필에 표시하지 않음. */
    private String birthPlace;

    /** 본명. 비어 있으면 프로필에 표시하지 않음. */
    private String realName;

    /** MBTI. 비어 있으면 프로필에 표시하지 않음. */
    @Column(length = 16)
    private String mbti;

    @Column(nullable = false)
    private String debutDate;

    @Transient
    public boolean isHasBornPlace() {
        return bornPlace != null && !bornPlace.isBlank();
    }

    @Transient
    public boolean isHasBirthPlace() {
        return birthPlace != null && !birthPlace.isBlank();
    }

    @Transient
    public boolean isHasRealName() {
        return realName != null && !realName.isBlank();
    }

    @Transient
    public boolean isHasMbti() {
        return mbti != null && !mbti.isBlank();
    }

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnitMember> affiliatedUnit = new ArrayList<>();

    private String profileImage;

    /** 싸인(서명) 이미지. 비어 있으면 프로필에 표시하지 않음. */
    private String signatureImage;

    /** 프로필 이미지가 없을 때 쓰는 플레이스홀더 */
    public static final String DEFAULT_PROFILE_IMAGE = com.example.triplesweb.util.ImageUrls.PLACEHOLDER;

    @Transient
    public String getDisplayProfileImage() {
        return com.example.triplesweb.util.ImageUrls.orPlaceholder(profileImage);
    }

    @Transient
    public boolean isHasSignatureImage() {
        return signatureImage != null && !signatureImage.isBlank();
    }

    @Column(nullable = false)
    private String color;

    /** 멤버 단독 컨텐츠 영상 링크 JSON. [{"title","url"}, ...] */
    @Column(columnDefinition = "TEXT")
    private String soloContent;

    /** 출연한 방송 영상 링크 JSON */
    @Column(columnDefinition = "TEXT")
    private String broadcastContent;

    /** 프로필 페이지에서 렌더링할 연결 공식 미디어 목록(영속화하지 않음). */
    @Transient
    private List<MediaDto> mediaContents = new ArrayList<>();

    /** 멤버 탭에 붙는 오브젝트·아카이브·일정·공식 미디어 미리보기. */
    @Transient
    private List<Map<String, Object>> memberFeeds = List.of();

    /**
     * DataInitializer와 기존 호출부를 위한 호환 생성자(미디어 목록은 페이지에서 채운다).
     * main의 본명·MBTI·싸인 이미지 필드를 포함하며, {@code mediaContents}는 넣지 않는다.
     */
    public Artist(Long artistId, String name, String engName, String nationality, String birthDate,
                  String bornPlace, String birthPlace, String realName, String mbti, String debutDate,
                  List<UnitMember> affiliatedUnit, String profileImage, String signatureImage,
                  String color, String soloContent, String broadcastContent) {
        this.artistId = artistId;
        this.name = name;
        this.engName = engName;
        this.nationality = nationality;
        this.birthDate = birthDate;
        this.bornPlace = bornPlace;
        this.birthPlace = birthPlace;
        this.realName = realName;
        this.mbti = mbti;
        this.debutDate = debutDate;
        this.affiliatedUnit = affiliatedUnit != null ? affiliatedUnit : new ArrayList<>();
        this.profileImage = profileImage;
        this.signatureImage = signatureImage;
        this.color = color;
        this.soloContent = soloContent;
        this.broadcastContent = broadcastContent;
        this.mediaContents = new ArrayList<>();
    }

    /**
     * 프로필 표시용 소속 유닛. 같은 아티스트가 하위 유닛에도 속하면 상위(예: MSNZ)는 숨긴다.
     */
    @Transient
    public List<UnitMember> getDisplayAffiliatedUnit() {
        if (affiliatedUnit == null || affiliatedUnit.isEmpty()) {
            return List.of();
        }
        Set<Long> parentsWithChild = new HashSet<>();
        for (UnitMember link : affiliatedUnit) {
            Unit unit = link.getUnit();
            if (unit == null || unit.getParentUnit() == null || unit.getParentUnit().getUnitId() == null) {
                continue;
            }
            parentsWithChild.add(unit.getParentUnit().getUnitId());
        }
        List<UnitMember> display = new ArrayList<>();
        for (UnitMember link : affiliatedUnit) {
            Unit unit = link.getUnit();
            if (unit == null || unit.getUnitId() == null) continue;
            if (parentsWithChild.contains(unit.getUnitId())) continue;
            display.add(link);
        }
        return display;
    }

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

    @Transient
    public boolean isHasMediaContents() {
        return mediaContents != null && !mediaContents.isEmpty();
    }
}
