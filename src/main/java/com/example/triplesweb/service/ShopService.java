package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopDetailDto;
import com.example.triplesweb.dto.ShopListCard;
import com.example.triplesweb.dto.ShopSectionGroupDto;
import com.example.triplesweb.entity.EventStop;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopSection;
import com.example.triplesweb.repository.ShopRepository;
import com.example.triplesweb.repository.ShopSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 굿즈리스트 목록·상세 조회. 목록은 구분(섹션)별로 묶고 미배정은 「기타」.
 * 상세 MD 사진은 연결된 이벤트의 {@code md_list} notice 이미지를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private static final long LIST_CACHE_TTL_MS = 120_000L;

    private final ShopRepository shopRepository;
    private final ShopSectionRepository shopSectionRepository;

    private volatile List<ShopSectionGroupDto> listCache;
    private volatile long listCacheExpiresAt;

    @Transactional(readOnly = true)
    public List<ShopSectionGroupDto> listGroupedBySection() {
        long now = System.currentTimeMillis();
        List<ShopSectionGroupDto> cached = listCache;
        if (cached != null && now < listCacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (listCache != null && System.currentTimeMillis() < listCacheExpiresAt) {
                return listCache;
            }
            List<ShopSectionGroupDto> fresh = buildGroupedBySection();
            listCache = fresh;
            listCacheExpiresAt = System.currentTimeMillis() + LIST_CACHE_TTL_MS;
            return fresh;
        }
    }

    /**
     * 관리자 저장·삭제·이벤트 MD 변경 후 목록 캐시를 비운다.
     * 트랜잭션 안에서는 커밋 후에 비워, 커밋 전 재조회가 옛 행을 다시 캐시하지 않게 한다.
     */
    public void invalidateListCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearListCache();
                }
            });
            return;
        }
        clearListCache();
    }

    private void clearListCache() {
        listCache = null;
        listCacheExpiresAt = 0L;
    }

    private List<ShopSectionGroupDto> buildGroupedBySection() {
        List<ShopSection> sections = shopSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc();
        List<ShopListCard> shops = shopRepository.findAllWithSectionAndEvent().stream()
                .sorted(Comparator
                        .comparing((Shop s) -> s.getSalesPeriod() != null ? s.getSalesPeriod() : "", Comparator.reverseOrder())
                        .thenComparing(s -> s.getId() != null ? s.getId() : 0L, Comparator.reverseOrder()))
                .map(ShopListCard::new)
                .toList();

        Map<Long, ShopSectionGroupDto> byId = new LinkedHashMap<>();
        for (ShopSection section : sections) {
            byId.put(section.getSectionId(), ShopSectionGroupDto.builder()
                    .sectionId(section.getSectionId())
                    .name(section.getName())
                    .other(false)
                    .tabKey("shop-section-" + section.getSectionId())
                    .shops(new ArrayList<>())
                    .build());
        }

        List<ShopListCard> unassigned = new ArrayList<>();
        for (ShopListCard shop : shops) {
            Long sectionId = shop.getSectionId();
            if (sectionId != null && byId.containsKey(sectionId)) {
                byId.get(sectionId).getShops().add(shop);
            } else {
                unassigned.add(shop);
            }
        }

        List<ShopSectionGroupDto> groups = new ArrayList<>();
        for (ShopSectionGroupDto group : byId.values()) {
            if (!group.getShops().isEmpty()) {
                groups.add(group);
            }
        }
        if (!unassigned.isEmpty()) {
            groups.add(ShopSectionGroupDto.builder()
                    .sectionId(null)
                    .name("기타")
                    .other(true)
                    .tabKey("shop-section-other")
                    .shops(unassigned)
                    .build());
        }
        if (groups.isEmpty() && !shops.isEmpty()) {
            groups.add(ShopSectionGroupDto.builder()
                    .name("기타")
                    .other(true)
                    .tabKey("shop-section-other")
                    .shops(shops)
                    .build());
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public ShopDetailDto getDetail(long shopId) {
        Shop shop = shopRepository.findDetailById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("해당 굿즈리스트가 존재하지 않습니다. id=" + shopId));
        return toDetail(shop);
    }

    static ShopDetailDto toDetail(Shop shop) {
        List<ShopDetailDto.MdRegion> regions = new ArrayList<>();
        if (shop.getEvent() != null) {
            for (EventStop stop : shop.getEvent().getMdGoodsStops()) {
                List<String> photos = collectNoticeImages(stop);
                if (photos.isEmpty()) continue;
                regions.add(ShopDetailDto.MdRegion.builder()
                        .label(stop.getGoodsLabel())
                        .eventDate(stop.getEventDate())
                        .venue(stop.getVenue())
                        .hasVenue(stop.isHasVenue())
                        .hasPhotos(true)
                        .photos(photos)
                        .build());
            }
        }
        return ShopDetailDto.builder()
                .id(shop.getId())
                .eventId(shop.getEvent() != null ? shop.getEvent().getEventId() : null)
                .eventName(shop.getEventName())
                .salesPeriod(shop.getSalesPeriod())
                .displayCoverImage(shop.getDisplayCoverImage())
                .hasSalesPeriod(shop.isHasSalesPeriod())
                .hasMdPhotos(!regions.isEmpty())
                .mdRegions(regions)
                .build();
    }

    /** 회차 MD 카테고리의 공지 이미지를 순서·중복 없이 모은다. */
    static List<String> collectNoticeImages(EventStop stop) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (EventStop.MdCategory category : stop.getMdCategories()) {
            if (category.getNoticeImages() == null) continue;
            for (String url : category.getNoticeImages()) {
                if (!StringUtils.hasText(url)) continue;
                String trimmed = url.trim();
                if (seen.add(trimmed)) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }
}
