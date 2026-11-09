package com.example.triplesweb.service;

import com.example.triplesweb.dto.EventListCard;
import com.example.triplesweb.dto.EventSectionGroupDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventSection;
import com.example.triplesweb.repository.EventRepository;
import com.example.triplesweb.repository.EventSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 목록·상세 조회. 목록은 구분(섹션)별로 묶고 미배정은 「기타」.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private static final long LIST_CACHE_TTL_MS = 120_000L;

    private final EventRepository eventRepository;
    private final EventSectionRepository eventSectionRepository;

    private volatile List<EventSectionGroupDto> listCache;
    private volatile long listCacheExpiresAt;

    @Transactional(readOnly = true)
    public List<EventSectionGroupDto> listGroupedBySection() {
        long now = System.currentTimeMillis();
        List<EventSectionGroupDto> cached = listCache;
        if (cached != null && now < listCacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (listCache != null && System.currentTimeMillis() < listCacheExpiresAt) {
                return listCache;
            }
            List<EventSectionGroupDto> fresh = buildGroupedBySection();
            listCache = fresh;
            listCacheExpiresAt = System.currentTimeMillis() + LIST_CACHE_TTL_MS;
            return fresh;
        }
    }

    /** 관리자 저장·삭제 후 목록 캐시를 바로 비운다. */
    public void invalidateListCache() {
        listCache = null;
        listCacheExpiresAt = 0L;
    }

    private List<EventSectionGroupDto> buildGroupedBySection() {
        List<EventSection> sections = eventSectionRepository.findAllByOrderBySortOrderAscSectionIdAsc();
        List<Event> loaded = eventRepository.findAllWithSectionAndStops();
        List<EventListCard> events = loaded.stream()
                .sorted(Comparator
                        .comparing(EventListCard::sortKey, Comparator.reverseOrder())
                        .thenComparing(e -> e.getEventId() != null ? e.getEventId() : 0L, Comparator.reverseOrder()))
                .map(EventListCard::new)
                .toList();

        Map<Long, EventSectionGroupDto> byId = new LinkedHashMap<>();
        for (EventSection section : sections) {
            byId.put(section.getSectionId(), EventSectionGroupDto.builder()
                    .sectionId(section.getSectionId())
                    .name(section.getName())
                    .other(false)
                    .tabKey("event-section-" + section.getSectionId())
                    .events(new ArrayList<>())
                    .build());
        }

        List<EventListCard> unassigned = new ArrayList<>();
        for (EventListCard event : events) {
            Long sectionId = event.getSectionId();
            if (sectionId != null && byId.containsKey(sectionId)) {
                byId.get(sectionId).getEvents().add(event);
            } else {
                unassigned.add(event);
            }
        }

        List<EventSectionGroupDto> groups = new ArrayList<>();
        for (EventSectionGroupDto group : byId.values()) {
            if (!group.getEvents().isEmpty()) {
                groups.add(group);
            }
        }
        if (!unassigned.isEmpty()) {
            groups.add(EventSectionGroupDto.builder()
                    .sectionId(null)
                    .name("기타")
                    .other(true)
                    .tabKey("event-section-other")
                    .events(unassigned)
                    .build());
        }
        if (groups.isEmpty() && !events.isEmpty()) {
            groups.add(EventSectionGroupDto.builder()
                    .name("기타")
                    .other(true)
                    .tabKey("event-section-other")
                    .events(events)
                    .build());
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public Event getDetail(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("해당 이벤트가 존재하지 않습니다. id=" + eventId));
        if (event.getSection() != null) {
            event.getSection().getName();
        }
        if (event.getStops() != null) {
            event.getStops().size();
        }
        return event;
    }
}
