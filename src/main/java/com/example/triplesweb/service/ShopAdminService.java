package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopAdminDto;
import com.example.triplesweb.dto.ShopSectionDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopSection;
import com.example.triplesweb.repository.EventRepository;
import com.example.triplesweb.repository.ShopRepository;
import com.example.triplesweb.repository.ShopSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopAdminService {

    private final ShopRepository shopRepository;
    private final ShopSectionRepository shopSectionRepository;
    private final EventRepository eventRepository;
    private final FileStorageService fileStorageService;
    private final ShopService shopService;

    @Transactional(readOnly = true)
    public ShopAdminDto get(long shopId) {
        Shop shop = shopRepository.findDetailById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("해당 굿즈리스트가 존재하지 않습니다. id=" + shopId));
        return toDto(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopSectionDto> listSections() {
        return shopSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShopAdminDto.EventOption> listEventOptions() {
        return eventRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        e -> e.getEventName() != null ? e.getEventName() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .map(e -> ShopAdminDto.EventOption.builder()
                        .eventId(e.getEventId())
                        .eventName(e.getEventName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public ShopSectionDto createSection(String name) {
        String trimmed = requireText(name, "구분명은 필수입니다.");
        shopSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
            throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
        });
        int nextOrder = shopSectionRepository.findAll().stream()
                .mapToInt(ShopSection::getSortOrder)
                .max()
                .orElse(-1) + 1;
        ShopSection section = new ShopSection(null, trimmed, nextOrder);
        ShopSectionDto dto = toSectionDto(shopSectionRepository.save(section));
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public ShopSectionDto updateSection(long sectionId, String name, Integer sortOrder) {
        ShopSection section = shopSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            shopSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
                if (!Objects.equals(other.getSectionId(), sectionId)) {
                    throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
                }
            });
            section.setName(trimmed);
        }
        if (sortOrder != null) {
            section.setSortOrder(sortOrder);
        }
        ShopSectionDto dto = toSectionDto(shopSectionRepository.save(section));
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public void deleteSection(long sectionId) {
        ShopSection section = shopSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        for (Shop shop : shopRepository.findAll()) {
            if (shop.getSection() != null && Objects.equals(shop.getSection().getSectionId(), sectionId)) {
                shop.setSection(null);
            }
        }
        shopSectionRepository.delete(section);
        shopService.invalidateListCache();
    }

    @Transactional
    public List<ShopSectionDto> moveSection(long sectionId, int direction) {
        List<ShopSection> sections = new ArrayList<>(
                shopSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc());
        int index = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (Objects.equals(sections.get(i).getSectionId(), sectionId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId);
        }
        int swapWith = index + (direction < 0 ? -1 : 1);
        if (swapWith < 0 || swapWith >= sections.size()) {
            return sections.stream().map(this::toSectionDto).collect(Collectors.toList());
        }
        ShopSection a = sections.get(index);
        ShopSection b = sections.get(swapWith);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        if (a.getSortOrder() == b.getSortOrder()) {
            a.setSortOrder(swapWith);
            b.setSortOrder(index);
        }
        shopSectionRepository.save(a);
        shopSectionRepository.save(b);
        List<ShopSectionDto> result = shopSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
        shopService.invalidateListCache();
        return result;
    }

    @Transactional
    public ShopAdminDto create(
            String title,
            Long eventId,
            String salesPeriod,
            Long sectionId,
            String mdList,
            MultipartFile image,
            String imageUrl
    ) {
        Shop shop = new Shop();
        applyFields(shop, null, title, eventId, salesPeriod, sectionId, mdList, image, imageUrl, false);
        ShopAdminDto dto = toDto(shopRepository.save(shop));
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public ShopAdminDto update(
            long shopId,
            String title,
            Long eventId,
            String salesPeriod,
            Long sectionId,
            String mdList,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        Shop shop = shopRepository.findDetailById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("해당 굿즈리스트가 존재하지 않습니다. id=" + shopId));
        applyFields(shop, shopId, title, eventId, salesPeriod, sectionId, mdList, image, imageUrl, clearImage);
        ShopAdminDto dto = toDto(shopRepository.save(shop));
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public void delete(long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("해당 굿즈리스트가 존재하지 않습니다. id=" + shopId));
        deleteManagedImage(shop.getCoverImage());
        shopRepository.delete(shop);
        shopService.invalidateListCache();
    }

    private void applyFields(
            Shop shop,
            Long shopId,
            String title,
            Long eventId,
            String salesPeriod,
            Long sectionId,
            String mdList,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        String cardTitle = requireText(title, "카드 이름은 필수입니다.");
        if (cardTitle.length() > 200) {
            throw new IllegalArgumentException("카드 이름은 200자 이하여야 합니다.");
        }

        Event event = null;
        if (eventId != null && eventId > 0) {
            event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 이벤트가 존재하지 않습니다. id=" + eventId));
            final String eventLabel = event.getEventName();
            shopRepository.findByEvent_EventId(eventId).ifPresent(other -> {
                if (!Objects.equals(other.getId(), shopId)) {
                    throw new IllegalArgumentException("이미 굿즈리스트에 등록된 공연입니다: " + eventLabel);
                }
            });
        }

        String period = requireText(salesPeriod, "판매 기간은 필수입니다.");
        shop.setTitle(cardTitle);
        shop.setEvent(event);
        shop.setSalesPeriod(period);
        shop.setMdList(normalizeMdList(mdList));

        if (sectionId != null && sectionId > 0) {
            ShopSection section = shopSectionRepository.findById(sectionId)
                    .orElseThrow(() -> new IllegalArgumentException("구분이 없습니다. id=" + sectionId));
            shop.setSection(section);
        } else {
            shop.setSection(null);
        }

        if (image != null && !image.isEmpty()) {
            try {
                String previous = shop.getCoverImage();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "shop");
                shop.setCoverImage(meta.getUrl());
                deleteManagedImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("커버 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedImage(shop.getCoverImage());
            shop.setCoverImage(null);
        } else if (StringUtils.hasText(imageUrl)) {
            String url = normalizeExternalImageUrl(imageUrl);
            String previous = shop.getCoverImage();
            shop.setCoverImage(url);
            if (!Objects.equals(previous, url)) {
                deleteManagedImage(previous);
            }
        }
    }

    private static String normalizeExternalImageUrl(String raw) {
        String url = raw.trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new IllegalArgumentException("이미지 URL은 http(s)로 시작해야 합니다.");
        }
        if (url.length() > 1000) {
            throw new IllegalArgumentException("이미지 URL이 너무 깁니다.");
        }
        return url;
    }

    private ShopAdminDto toDto(Shop shop) {
        ShopSection section = shop.getSection();
        Event event = shop.getEvent();
        return ShopAdminDto.builder()
                .id(shop.getId())
                .title(shop.getTitle())
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getEventName() : shop.getEventName())
                .salesPeriod(shop.getSalesPeriod())
                .coverImage(shop.getCoverImage())
                .mdList(shop.getMdList())
                .sectionId(section != null ? section.getSectionId() : null)
                .sectionName(section != null ? section.getName() : null)
                .build();
    }

    private static String normalizeMdList(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String normalized = java.util.Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
        return normalized.isEmpty() ? null : normalized;
    }

    private ShopSectionDto toSectionDto(ShopSection section) {
        return ShopSectionDto.builder()
                .sectionId(section.getSectionId())
                .name(section.getName())
                .sortOrder(section.getSortOrder())
                .build();
    }

    private void deleteManagedImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return;
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/shop/")) return;
        try {
            fileStorageService.deleteFile(imageUrl);
        } catch (RuntimeException ignored) {
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
