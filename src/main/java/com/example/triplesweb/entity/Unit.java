package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 유닛(그룹/서브유닛) 엔티티.
 * 상위 {@link Unit}과 자기참조, {@link UnitMember}·{@link Album} 1:N.
 */
@Entity
@Table(name = "unit")
@Getter
@Setter
@ToString(exclude = {"unitMembers", "albums"})
@NoArgsConstructor
@AllArgsConstructor

public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long unitId;

    @Column(nullable = false)
    private String unitName;

    private String smallName;

    private String unitImage;

    @Transient
    public String getDisplayUnitImage() {
        return com.example.triplesweb.util.ImageUrls.orPlaceholder(unitImage);
    }

    @Column(nullable = false)
    private String formationDate;

    private String debutDate;

    /** 목록/탭에 노출할지 여부. false면 상위 컨테이너(예: MSNZ) 숨김 */
    @Column(nullable = false)
    private boolean visible = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_unit_id")
    private Unit parentUnit;

    // 하위 유닛 목록 (예: MSNZ 입장에서는 [Moon, Sun, Neptune, Zenith] 하위 유닛 리스트)
    @OneToMany(mappedBy = "parentUnit", cascade = CascadeType.ALL)
    private List<Unit> subUnits = new ArrayList<>();


    /** 소속 멤버는 항상 artistId(S순) 오름차순 */
    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("artist.artistId ASC")
    private List<UnitMember> unitMembers = new ArrayList<>();

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL)
    private List<Album> albums = new ArrayList<>();

    /** 목록 페이지용: 부모/자식 앨범까지 합친 표시 목록 (영속화하지 않음) */
    @Transient
    private List<Album> displayAlbums = new ArrayList<>();

    public Unit(Long unitId, String unitName, String smallName, String unitImage, String formationDate, String debutDate, Unit parentUnit) {
        this.unitId = unitId;
        this.unitName = unitName;
        this.smallName = smallName;
        this.unitImage = unitImage;
        this.formationDate = formationDate;
        this.debutDate = debutDate;
        this.parentUnit = parentUnit;
        this.visible = true;
        this.subUnits = new ArrayList<>();
        this.unitMembers = new ArrayList<>();
        this.albums = new ArrayList<>();
    }
}
