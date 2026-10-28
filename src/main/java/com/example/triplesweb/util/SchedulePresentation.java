package com.example.triplesweb.util;

import com.example.triplesweb.dto.ScheduleForm;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.ScheduleCategory;
import java.util.*;
import java.util.regex.Pattern;

public final class SchedulePresentation {
    private static final Pattern COLOR=Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    private SchedulePresentation() {}
    public static ScheduleForm form(Schedule schedule, List<Artist> knownArtists) {
        ScheduleCategory stored=schedule.getCategory()==null?ScheduleCategory.AUTO:schedule.getCategory();
        ScheduleCategory category=stored.resolve(schedule.getTitle());
        String title=Objects.toString(schedule.getTitle(),"");
        String color=category.getColor();
        if(category==ScheduleCategory.ANNIVERSARY) {
            List<Artist> candidates=new ArrayList<>();
            if(schedule.getArtists()!=null)candidates.addAll(schedule.getArtists());
            if(candidates.isEmpty()) {
                String lower=title.toLowerCase(Locale.ROOT);
                for(Artist artist:knownArtists) {
                    if((artist.getName()!=null&&lower.contains(artist.getName().toLowerCase(Locale.ROOT)))
                            || (artist.getEngName()!=null&&!artist.getEngName().isBlank()&&lower.contains(artist.getEngName().toLowerCase(Locale.ROOT))))
                        candidates.add(artist);
                }
            }
            color=candidates.stream().sorted(Comparator.comparing(Artist::getArtistId,Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(Artist::getColor).filter(Objects::nonNull).filter(value->COLOR.matcher(value).matches())
                    .findFirst().orElse(category.getColor());
        }
        ScheduleForm result=new ScheduleForm(String.valueOf(schedule.getId()),title,schedule.getStart(),
                schedule.getEnd()!=null?schedule.getEnd().plusDays(1):null,schedule.getEnd());
        result.setArtistNames(schedule.getArtistNames());
        result.setCategory(stored.name());
        result.setCategoryLabel(category.getLabel());
        result.setColor(color);
        result.setDisplayTitle(category==ScheduleCategory.ANNIVERSARY&&!title.contains("🎂")?"🎂 "+title:title);
        return result;
    }
    public static List<Map<String,String>> categories() {
        return Arrays.stream(ScheduleCategory.values()).map(category->Map.of(
                "value",category.name(),"label",category.getLabel(),"color",category.getColor())).toList();
    }
}
