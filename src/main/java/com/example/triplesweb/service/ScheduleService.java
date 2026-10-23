package com.example.triplesweb.service;

import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    public Schedule save(String title, LocalDate start, LocalDate end) {
        return scheduleRepository.save(new Schedule(title, start, end));
    }
    public Schedule save(String title, LocalDate start, LocalDate end, String artistNames) {
        return scheduleRepository.save(new Schedule(title, start, end, artistNames));
    }
    public List<Schedule> findAll() { return scheduleRepository.findAll(); }
    public Schedule findById(String idStr) {
        Long id = Long.parseLong(idStr);
        return scheduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid schedule Id:" + id));
    }
    public Schedule update(String idStr, String title, LocalDate start, LocalDate end) {
        return update(idStr, title, start, end, "");
    }
    public Schedule update(String idStr, String title, LocalDate start, LocalDate end, String artistNames) {
        Long id = Long.parseLong(idStr);
        Schedule schedule = scheduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 스케줄이 없습니다. id=" + id));
        schedule.setTitle(title); schedule.setStart(start); schedule.setEnd(end); schedule.setArtistNames(artistNames);
        return scheduleRepository.save(schedule);
    }
    public void deleteById(Long id) { scheduleRepository.deleteById(id); }
}