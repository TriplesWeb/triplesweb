package com.example.triplesweb.service;

import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 아티스트 일정({@link Schedule}) CRUD를 담당한다.
 * 제목·기간 저장, 전체/단건 조회, 수정·삭제를 제공한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    // 스케줄 저장 메서드
    public Schedule save(String title, LocalDate start, LocalDate end) {
        Schedule schedule = new Schedule(title, start, end);
        return scheduleRepository.save(schedule);
    }

    // 전체 스케줄 조회 메서드
    public List<com.example.triplesweb.entity.Schedule> findAll() {
        return scheduleRepository.findAll();
    }
    public com.example.triplesweb.entity.Schedule findById(String idStr) {
        Long id = Long.parseLong(idStr);
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid schedule Id:" + id));
    }
    public Schedule update(String idStr, String title, LocalDate start, LocalDate end) {
        Long id = Long.parseLong(idStr);
        Schedule schedule = scheduleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 스케줄이 없습니다. id=" + id));
        schedule.setTitle(title);
        schedule.setStart(start);
        schedule.setEnd(end);
        return scheduleRepository.save(schedule);
    }
    public void deleteById(Long id) {
        scheduleRepository.deleteById(id);
    }
}
