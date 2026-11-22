package com.example.triplesweb.dto;

import java.time.LocalDate;

public record CalendarDecorationDto(
        Long id,
        String title,
        String kind,
        LocalDate eventDate,
        String unitName,
        String description,
        String imageUrl,
        String color
) {}
