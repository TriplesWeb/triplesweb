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
    private String category;
    private String categoryLabel;
    private String color;
    private String displayTitle;

    public ScheduleForm(String id, String title, LocalDate start, LocalDate end, LocalDate rawEnd) {
        this.id = id; this.title = title; this.start = start; this.end = end; this.rawEnd = rawEnd;
    }
    public ScheduleForm(String id, String title, LocalDate start, LocalDate end, LocalDate rawEnd, String artistNames) {
        this(id,title,start,end,rawEnd);this.artistNames=artistNames;
    }
    public String getEndStr() { return end != null ? end.toString() : ""; }
    public String getStartStr() { return start != null ? start.toString() : ""; }
}