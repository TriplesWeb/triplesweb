package com.example.triplesweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

/** 일정 등록·수정 폼 바인딩 및 캘린더 API(JSON) 입출력. */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleForm {
    private String id;
    private String title;
    private LocalDate start;
    private LocalDate end;
    private LocalDate rawEnd;

    public String getEndStr() {
        return end != null ? end.toString() : "";
    }

    public String getStartStr() {
        return start != null ? start.toString() : "";
    }
}
