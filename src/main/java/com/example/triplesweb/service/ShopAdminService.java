package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopAdminDto;
import com.example.triplesweb.dto.ShopSectionDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventSection;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopSection;
import com.example.triplesweb.repository.EventRepository;
import com.example.triplesweb.repository.EventSectionRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopAdminService {

    private final ShopRepository shopRepository;
    private final ShopSectionRepository shopSectionRepository;
    private final EventRepository eventRepository;
    private final EventSectionRepository eventSectionRepository;
    private final FileStorageService fileStorageService;
    private final ShopService shopService;

    @Transactional(readOnly = true)
    public ShopAdminDto get(long shopId) {
        Shop shop = shopRepository.findDetailById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("해당 굿즈리스트가 존재하지 않습니다. id=" + shopId));
        return toDto(shop);
    }

    /**
     * 공연 목록 구분을 샵 섹션에 이름으로 맞춘 뒤 반환한다.
     * 공연에만 있는 이름은 자동 추가하고, 샵 전용 구분은 그대로 둔다.
     */
    @Transactional
    public List<ShopSectionDto> listSections() {
        ensureEventSectionsMirrored();
        Set<String> eventNames = eventSectionNamesLower();
        return shopSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(section -> toSectionDto(section, eventNames))
                .collect(Collectors.toList());
    }

    /** 공연 구분 추가·개명 직후 샵 쪽 미러를 맞출 때 호출한다. */
    @Transactional
    public void ensureEventSectionsMirrored() {
        List<EventSection> eventSections = eventSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc();
        if (eventSections.isEmpty()) {
            return;
        }
        int nextOrder = shopSectionRepository.findAll().stream()
                .mapToInt(ShopSection::getSortOrder)
                .max()
                .orElse(-1) + 1;
        boolean created = false;
        for (EventSection eventSection : eventSections) {
            String name = eventSection.getName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            if (shopSectionRepository.findByNameIgnoreCase(trimmed).isPresent()) {
                continue;
            }
            shopSectionRepository.save(new ShopSection(null, trimmed, nextOrder++));
            created = true;
        }
        if (created) {
            shopService.invalidateListCache();
        }
    }

    /** 공연 구분명 변경 시 같은 이름의 샵 구분도 따라 바꾼다. */
    @Transactional
    public void renameMirroredSection(String oldName, String newName) {
        if (!StringUtils.hasText(oldName) || !StringUtils.hasText(newName)) {
            return;
        }
        String from = oldName.trim();
        String to = newName.trim();
        if (from.equalsIgnoreCase(to)) {
            return;
        }
        shopSectionRepository.findByNameIgnoreCase(from).ifPresent(section -> {
            if (shopSectionRepository.findByNameIgnoreCase(to).isPresent()) {
                return;
            }
            section.setName(to);
            shopSectionRepository.save(section);
            shopService.invalidateListCache();
        });
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
        ensureEventSectionsMirrored();
        String trimmed = requireText(name, "구분명은 필수입니다.");
        shopSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
            throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
        });
        int nextOrder = shopSectionRepository.findAll().stream()
                .mapToInt(ShopSection::getSortOrder)
                .max()
                .orElse(-1) + 1;
        ShopSection section = new ShopSection(null, trimmed, nextOrder);
        ShopSectionDto dto = toSectionDto(shopSectionRepository.save(section), eventSectionNamesLower());
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public ShopSectionDto updateSection(long sectionId, String name, Integer sortOrder) {
        ShopSection section = shopSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        Set<String> eventNames = eventSectionNamesLower();
        if (isFromEvent(section.getName(), eventNames) && StringUtils.hasText(name)
                && !section.getName().trim().equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("공연 페이지 구분은 공연 관리에서 이름을 바꿔 주세요.");
        }
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
        ShopSectionDto dto = toSectionDto(shopSectionRepository.save(section), eventNames);
        shopService.invalidateListCache();
        return dto;
    }

    @Transactional
    public void deleteSection(long sectionId) {
        ShopSection section = shopSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        if (isFromEvent(section.getName(), eventSectionNamesLower())) {
            throw new IllegalArgumentException("공연 페이지 구분은 여기서 삭제할 수 없습니다. 공연 관리에서 바꿔 주세요.");
        }
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
        ensureEventSectionsMirrored();
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
        Set<String> eventNames = eventSectionNamesLower();
        if (swapWith < 0 || swapWith >= sections.size()) {
            return sections.stream().map(s -> toSectionDto(s, eventNames)).collect(Collectors.toList());
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
                .map(s -> toSectionDto(s, eventNames))
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

    private ShopSectionDto toSectionDto(ShopSection section, Set<String> eventNamesLower) {
        return ShopSectionDto.builder()
                .sectionId(section.getSectionId())
                .name(section.getName())
                .sortOrder(section.getSortOrder())
                .fromEvent(isFromEvent(section.getName(), eventNamesLower))
                .build();
    }

    private Set<String> eventSectionNamesLower() {
        Set<String> names = new HashSet<>();
        for (EventSection section : eventSectionRepository.findAll()) {
            if (StringUtils.hasText(section.getName())) {
                names.add(section.getName().trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static boolean isFromEvent(String name, Set<String> eventNamesLower) {
        return StringUtils.hasText(name)
                && eventNamesLower.contains(name.trim().toLowerCase(Locale.ROOT));
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
