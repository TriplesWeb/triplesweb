package com.example.triplesweb.service;

import com.example.triplesweb.dto.EventAdminDto;
import com.example.triplesweb.dto.EventSectionDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventSection;
import com.example.triplesweb.entity.EventStop;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.EventRepository;
import com.example.triplesweb.repository.EventSectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventAdminService {

    private final EventRepository eventRepository;
    private final EventSectionRepository eventSectionRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public EventAdminDto get(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("해당 이벤트가 존재하지 않습니다. id=" + eventId));
        if (event.getSection() != null) event.getSection().getName();
        if (event.getStops() != null) event.getStops().size();
        return toDto(event);
    }

    @Transactional(readOnly = true)
    public List<EventSectionDto> listSections() {
        return eventSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventSectionDto createSection(String name) {
        String trimmed = requireText(name, "구분명은 필수입니다.");
        eventSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
            throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
        });
        int nextOrder = eventSectionRepository.findAll().stream()
                .mapToInt(EventSection::getSortOrder)
                .max()
                .orElse(-1) + 1;
        EventSection section = new EventSection(null, trimmed, nextOrder);
        EventSectionDto dto = toSectionDto(eventSectionRepository.save(section));
        eventService.invalidateListCache();
        return dto;
    }

    @Transactional
    public EventSectionDto updateSection(long sectionId, String name, Integer sortOrder) {
        EventSection section = eventSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            eventSectionRepository.findByNameIgnoreCase(trimmed).ifPresent(other -> {
                if (!Objects.equals(other.getSectionId(), sectionId)) {
                    throw new IllegalArgumentException("이미 사용 중인 구분명입니다: " + trimmed);
                }
            });
            section.setName(trimmed);
        }
        if (sortOrder != null) {
            section.setSortOrder(sortOrder);
        }
        EventSectionDto dto = toSectionDto(eventSectionRepository.save(section));
        eventService.invalidateListCache();
        return dto;
    }

    @Transactional
    public void deleteSection(long sectionId) {
        EventSection section = eventSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 구분이 존재하지 않습니다. id=" + sectionId));
        for (Event event : eventRepository.findAll()) {
            if (event.getSection() != null && Objects.equals(event.getSection().getSectionId(), sectionId)) {
                event.setSection(null);
            }
        }
        eventSectionRepository.delete(section);
        eventService.invalidateListCache();
    }

    @Transactional
    public List<EventSectionDto> moveSection(long sectionId, int direction) {
        List<EventSection> sections = new ArrayList<>(
                eventSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc());
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
        EventSection a = sections.get(index);
        EventSection b = sections.get(swapWith);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        if (a.getSortOrder() == b.getSortOrder()) {
            a.setSortOrder(swapWith);
            b.setSortOrder(index);
        }
        eventSectionRepository.save(a);
        eventSectionRepository.save(b);
        List<EventSectionDto> result = eventSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc().stream()
                .map(this::toSectionDto)
                .collect(Collectors.toList());
        eventService.invalidateListCache();
        return result;
    }

    @Transactional
    public EventAdminDto create(
            String eventName,
            String eventInfo,
            Long sectionId,
            String stopsJson,
            MultipartFile image,
            String imageUrl
    ) {
        Event event = new Event();
        applyFields(event, null, eventName, eventInfo, sectionId, stopsJson, image, imageUrl, false);
        EventAdminDto dto = toDto(eventRepository.save(event));
        eventService.invalidateListCache();
        return dto;
    }

    @Transactional
    public EventAdminDto update(
            long eventId,
            String eventName,
            String eventInfo,
            Long sectionId,
            String stopsJson,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("해당 이벤트가 존재하지 않습니다. id=" + eventId));
        applyFields(event, eventId, eventName, eventInfo, sectionId, stopsJson, image, imageUrl, clearImage);
        EventAdminDto dto = toDto(eventRepository.save(event));
        eventService.invalidateListCache();
        return dto;
    }

    @Transactional
    public void delete(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("해당 이벤트가 존재하지 않습니다. id=" + eventId));
        deleteManagedImage(event.getEventImage());
        eventRepository.delete(event);
        eventService.invalidateListCache();
    }

    private void applyFields(
            Event event,
            Long eventId,
            String eventName,
            String eventInfo,
            Long sectionId,
            String stopsJson,
            MultipartFile image,
            String imageUrl,
            boolean clearImage
    ) {
        String name = requireText(eventName, "이벤트명은 필수입니다.");
        String info = eventInfo != null ? eventInfo.trim() : "";

        eventRepository.findByEventName(name).ifPresent(other -> {
            if (!Objects.equals(other.getEventId(), eventId)) {
                throw new IllegalArgumentException("이미 사용 중인 이벤트명입니다: " + name);
            }
        });

        event.setEventName(name);
        event.setEventInfo(info);

        if (sectionId != null && sectionId > 0) {
            EventSection section = eventSectionRepository.findById(sectionId)
                    .orElseThrow(() -> new IllegalArgumentException("구분이 없습니다. id=" + sectionId));
            event.setSection(section);
        } else {
            event.setSection(null);
        }

        replaceStops(event, parseStops(stopsJson));

        if (image != null && !image.isEmpty()) {
            try {
                String previous = event.getEventImage();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "event");
                event.setEventImage(meta.getUrl());
                deleteManagedImage(previous);
            } catch (IOException e) {
                throw new IllegalStateException("이벤트 이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedImage(event.getEventImage());
            event.setEventImage(null);
        } else if (StringUtils.hasText(imageUrl)) {
            String url = normalizeExternalImageUrl(imageUrl);
            String previous = event.getEventImage();
            event.setEventImage(url);
            if (!Objects.equals(previous, url)) {
                deleteManagedImage(previous);
            }
        }
    }

    private void replaceStops(Event event, List<EventAdminDto.StopItem> items) {
        if (event.getStops() == null) {
            event.setStops(new ArrayList<>());
        } else {
            event.getStops().clear();
        }
        int i = 0;
        for (EventAdminDto.StopItem item : items) {
            if (item == null || !StringUtils.hasText(item.getEventDate())) continue;
            EventStop stop = new EventStop();
            stop.setEvent(event);
            stop.setEventDate(item.getEventDate().trim());
            stop.setRegion(StringUtils.hasText(item.getRegion()) ? item.getRegion().trim() : null);
            stop.setVenue(StringUtils.hasText(item.getVenue()) ? item.getVenue().trim() : null);
            stop.setDetail(item.getDetail() != null ? item.getDetail().trim() : "");
            stop.setSetlist(normalizeListText(item.getSetlist()));
            stop.setMdList(normalizeListText(item.getMdList()));
            stop.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : i);
            event.getStops().add(stop);
            i++;
        }
        if (event.getStops().isEmpty()) {
            throw new IllegalArgumentException("지역 회차를 하나 이상 입력해 주세요.");
        }
    }

    private List<EventAdminDto.StopItem> parseStops(String stopsJson) {
        if (!StringUtils.hasText(stopsJson)) return List.of();
        try {
            return objectMapper.readValue(stopsJson, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("지역 회차 JSON이 올바르지 않습니다.");
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

    private EventAdminDto toDto(Event event) {
        EventSection section = event.getSection();
        List<EventAdminDto.StopItem> stops = new ArrayList<>();
        for (EventStop stop : event.getOrderedStops()) {
            stops.add(EventAdminDto.StopItem.builder()
                    .sortOrder(stop.getSortOrder())
                    .eventDate(stop.getEventDate())
                    .region(stop.getRegion())
                    .venue(stop.getVenue())
                    .detail(stop.getDetail())
                    .setlist(stop.getSetlist())
                    .mdList(stop.getMdList())
                    .build());
        }
        return EventAdminDto.builder()
                .eventId(event.getEventId())
                .eventName(event.getEventName())
                .eventImage(event.getEventImage())
                .eventInfo(event.getEventInfo())
                .sectionId(section != null ? section.getSectionId() : null)
                .sectionName(section != null ? section.getName() : null)
                .stops(stops)
                .build();
    }

    private EventSectionDto toSectionDto(EventSection section) {
        return EventSectionDto.builder()
                .sectionId(section.getSectionId())
                .name(section.getName())
                .sortOrder(section.getSortOrder())
                .build();
    }

    private void deleteManagedImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) return;
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/event/")) return;
        try {
            fileStorageService.deleteFile(imageUrl);
        } catch (RuntimeException ignored) {
        }
    }

    /** 줄바꿈 리스트 정규화. 빈 줄 제거, 양끝 공백 정리. */
    private static String normalizeListText(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String normalized = Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
