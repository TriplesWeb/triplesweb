package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.ScheduleCategory;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final ArtistRepository artistRepository;

    public Schedule save(String title, LocalDate start, LocalDate end) {
        return save(title, start, end, "");
    }

    public Schedule save(String title, LocalDate start, LocalDate end, String artistNames) {
        return save(title,start,end,artistNames,ScheduleCategory.AUTO);
    }

    public Schedule save(String title, LocalDate start, LocalDate end, String artistNames, ScheduleCategory category) {
        validate(title,start,end);
        Schedule schedule = new Schedule(title.trim(), start, end);
        schedule.setCategory(category==null?ScheduleCategory.AUTO:category);
        schedule.setArtists(resolveArtists(artistNames));
        return scheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<Schedule> findAll() {
        return scheduleRepository.findAllWithArtists();
    }

    @Transactional(readOnly = true)
    public Schedule findById(String idStr) {
        Long id = Long.parseLong(idStr);
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid schedule Id:" + id));
    }

    public Schedule update(String idStr, String title, LocalDate start, LocalDate end) {
        return update(idStr, title, start, end, "");
    }

    public Schedule update(String idStr, String title, LocalDate start, LocalDate end, String artistNames) {
        return update(idStr,title,start,end,artistNames,null);
    }

    public Schedule update(String idStr, String title, LocalDate start, LocalDate end, String artistNames, ScheduleCategory category) {
        validate(title,start,end);
        Long id = Long.parseLong(idStr);
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 스케줄이 없습니다. id=" + id));
        schedule.setTitle(title.trim());
        if(category!=null)schedule.setCategory(category);
        schedule.setStart(start);
        schedule.setEnd(end);
        schedule.setArtists(resolveArtists(artistNames));
        return scheduleRepository.save(schedule);
    }

    public void deleteById(Long id) {
        scheduleRepository.deleteById(id);
    }

    private static void validate(String title, LocalDate start, LocalDate end) {
        if(title==null||title.isBlank()||title.trim().length()>200)throw new IllegalArgumentException("제목은 1~200자로 입력해 주세요.");
        if(start==null)throw new IllegalArgumentException("시작 날짜를 입력해 주세요.");
        if(end!=null&&end.isBefore(start))throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 빠를 수 없습니다.");
    }

    private Set<Artist> resolveArtists(String names) {
        Set<Artist> result = new LinkedHashSet<>();
        if (names == null || names.isBlank()) return result;
        for (String raw : names.split(",")) {
            String name = raw.trim();
            if (name.isBlank()) continue;
            artistRepository.findByName(name)
                    .or(() -> artistRepository.findByEngNameIgnoreCase(name))
                    .ifPresent(result::add);
        }
        return result;
    }
}