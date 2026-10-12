package com.example.triplesweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

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
