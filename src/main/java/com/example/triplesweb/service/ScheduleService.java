package com.example.triplesweb.service;

import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * 아티스트 일정({@link Schedule}) CRUD를 담당한다.
 * 제목·기간 저장, 전체/단건 조회, 수정·삭제를 제공한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ArtistRepository artistRepository;

    // 스케줄 저장 메서드
    public Schedule save(String title, LocalDate start, LocalDate end) {
        return save(title, start, end, List.of());
    }

    public Schedule save(String title, LocalDate start, LocalDate end, List<Long> artistIds) {
        Schedule schedule = new Schedule(title, start, end);
        schedule.setArtists(resolveArtists(artistIds));
        return scheduleRepository.save(schedule);
    }

    // 전체 스케줄 조회 메서드
    public List<Schedule> findAll() {
        return scheduleRepository.findAllWithArtists();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Schedule> findByArtist(Long artistId) {
        if (artistId == null || artistId <= 0) {
            return List.of();
        }
        return scheduleRepository.findByArtistId(artistId);
    }
    public com.example.triplesweb.entity.Schedule findById(String idStr) {
        Long id = Long.parseLong(idStr);
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid schedule Id:" + id));
    }
    public Schedule update(String idStr, String title, LocalDate start, LocalDate end) {
        return update(idStr, title, start, end, List.of());
    }

    public Schedule update(String idStr, String title, LocalDate start, LocalDate end, List<Long> artistIds) {
        Long id = Long.parseLong(idStr);
        Schedule schedule = scheduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 스케줄이 없습니다. id=" + id));
        schedule.setTitle(title);
        schedule.setStart(start);
        schedule.setEnd(end);
        schedule.setArtists(resolveArtists(artistIds));
        return scheduleRepository.save(schedule);
    }

    private java.util.Set<com.example.triplesweb.entity.Artist> resolveArtists(List<Long> artistIds) {
        List<Long> ids = artistIds == null
                ? List.of()
                : artistIds.stream().filter(Objects::nonNull).distinct().toList();
        var artists = artistRepository.findAllById(ids);
        if (artists.size() != ids.size()) {
            throw new IllegalArgumentException("선택한 아티스트 중 존재하지 않는 멤버가 있습니다.");
        }
        return new LinkedHashSet<>(artists);
    }
    public void deleteById(Long id) {
        scheduleRepository.deleteById(id);
    }
}
