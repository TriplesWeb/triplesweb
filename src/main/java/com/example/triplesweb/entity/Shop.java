package com.example.triplesweb.entity;

import com.example.triplesweb.util.ImageUrls;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

/**
 * MD 굿즈리스트 카드. {@link Event}는 선택(1:0..1), {@link ShopSection} N:1.
 * 카드 이름은 {@code title}, MD 공지 사진은 {@code md_list}에서 관리한다.
 */
@Entity
@Table(name = "shop")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"event", "section"})
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 목록·상세에 쓰는 카드 이름. 공연과 무관한 MD도 등록할 수 있다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "event_id", unique = true)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ShopSection section;

    /** 판매 기간 표시 (예: 2024.03.01~2024.03.15) */
    @Column(name = "sales_period", nullable = false, length = 80)
    private String salesPeriod;

    /** 목록 커버. 비우면 연결 공연 포스터를 쓴다. */
    @Column(name = "cover_image", length = 1000)
    private String coverImage;

    /**
     * MD 공지. 줄바꿈 DSL.
     * <ul>
     *   <li>{@code # Official MD} — 구역(드롭다운) 제목</li>
     *   <li>{@code !/path.png} — 공지 사진</li>
     * </ul>
     */
    @Column(name = "md_list", columnDefinition = "LONGTEXT")
    private String mdList;

    /** 템플릿 호환용. 실제 표시 이름은 {@link #title}. */
    @Transient
    public String getEventName() {
        if (StringUtils.hasText(title)) {
            return title;
        }
        return event != null && event.getEventName() != null ? event.getEventName() : "";
    }

    @Transient
    public String getDisplayCoverImage() {
        if (StringUtils.hasText(coverImage)) {
            return ImageUrls.orPlaceholder(coverImage);
        }
        return event != null
                ? ImageUrls.orPlaceholder(event.getEventImage())
                : ImageUrls.PLACEHOLDER;
    }

    public boolean isHasSalesPeriod() {
        return StringUtils.hasText(salesPeriod);
    }
}
