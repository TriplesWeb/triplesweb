package com.example.triplesweb.dto;

import lombok.*;
import java.time.LocalDate;

@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor
public class ScheduleForm {
    private String id;
    private String title;
    private LocalDate start;
    private LocalDate end;
    private LocalDate rawEnd;
    private String artistNames;

    public ScheduleForm(String id, String title, LocalDate start, LocalDate end, LocalDate rawEnd) {
        this.id = id; this.title = title; this.start = start; this.end = end; this.rawEnd = rawEnd;
    }
    public String getEndStr() { return end != null ? end.toString() : ""; }
    public String getStartStr() { return start != null ? start.toString() : ""; }
}