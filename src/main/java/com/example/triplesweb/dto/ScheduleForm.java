package com.example.triplesweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    private List<ArtistTagDto> artists = new ArrayList<>();

    /** 기존 폼 매핑 코드와의 호환성을 유지한다. */
    public ScheduleForm(String id, String title, LocalDate start, LocalDate end, LocalDate rawEnd) {
        this.id = id;
        this.title = title;
        this.start = start;
        this.end = end;
        this.rawEnd = rawEnd;
        this.artists = new ArrayList<>();
    }

    public String getEndStr() {
        return end != null ? end.toString() : "";
    }

    public String getStartStr() {
        return start != null ? start.toString() : "";
    }
}
