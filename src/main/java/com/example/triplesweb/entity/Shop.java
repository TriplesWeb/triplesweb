package com.example.triplesweb.entity;

import com.example.triplesweb.util.ImageUrls;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

/**
 * 공연 MD 리스트 카드. {@link Event} 1:1, {@link ShopSection} N:1.
 * MD 본문(공지 사진·상품)은 연결된 이벤트의 {@code event_stop.md_list}가 단일 소스다.
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ShopSection section;

    /** 판매 기간 표시 (예: 2024.03.01~2024.03.15) */
    @Column(name = "sales_period", nullable = false, length = 80)
    private String salesPeriod;

    /** 목록 커버. 비우면 이벤트 포스터를 쓴다. */
    @Column(name = "cover_image", length = 1000)
    private String coverImage;

    @Transient
    public String getEventName() {
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
