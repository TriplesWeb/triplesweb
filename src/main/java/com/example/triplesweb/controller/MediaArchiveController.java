package com.example.triplesweb.controller;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class MediaArchiveController {
    private final MediaService media;
    private final ArtistRepository artistRepository;
    private final ScheduleRepository scheduleRepository;

    @GetMapping("/media")
    public String mediaPage(@RequestParam(name="tab",defaultValue="mv") String tab,
                            @RequestParam(defaultValue="1") int page,
                            @RequestParam(defaultValue="10") int size,
                            @RequestParam(required=false) String artist,
                            Model model) {
        tab=normalizeTab(tab); page=Math.max(1,page); size=Math.min(Math.max(1,size),100);
        model.addAttribute("isAdmin",true);
        model.addAttribute("artistOptions",artistRepository.findAll());
        model.addAttribute("currentTab",tab);
        model.addAttribute("isMV","mv".equals(tab)); model.addAttribute("isSIGNAL","signal".equals(tab));
        model.addAttribute("isBehind","behind".equals(tab)); model.addAttribute("isSecret","secret".equals(tab));
        model.addAttribute("isContents","contents".equals(tab));
        List<MediaDto> videos; int total;
        if(artist!=null&&!artist.isBlank()) {
            List<MediaDto> filtered=media.allByTab(tab).stream().filter(v->splitNames(v.getArtistNames()).contains(artist)).toList();
            total=filtered.size(); int from=Math.min((page-1)*size,total); int to=Math.min(from+size,total); videos=filtered.subList(from,to);
        } else { videos=media.pageByTab(tab,page,size); total=media.totalByTab(tab); }
        model.addAttribute("selectedArtist",artist==null?"":artist);
        int totalPages=(int)Math.ceil(total/(double)size);
        model.addAttribute("videos",videos); model.addAttribute("total",total); model.addAttribute("page",page);
        model.addAttribute("totalPages",Math.max(totalPages,1));
        boolean hasPrev=page>1,hasNext=page<totalPages;
        model.addAttribute("hasPrev",hasPrev); model.addAttribute("hasNext",hasNext);
        model.addAttribute("prevPage",hasPrev?page-1:1); model.addAttribute("nextPage",hasNext?page+1:totalPages);
        List<Map<String,Object>> pages=new ArrayList<>(); int start=Math.max(1,page-3),end=Math.min(Math.max(totalPages,1),start+6);
        for(int i=start;i<=end;i++)pages.add(Map.of("num",i,"active",i==page));
        model.addAttribute("pages",pages);
        return "archive/media";
    }

    @GetMapping("/media/{tab}/{youtubeId}")
    public String mediaDetail(@PathVariable String tab,@PathVariable String youtubeId,Model model)throws IOException {
        MediaTab t=MediaTab.from(tab);
        MediaDto video=media.findOne(t,youtubeId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"영상이 없습니다."));
        model.addAttribute("currentTab",t.getKey()); model.addAttribute("tabNameKo",t.getKor()); model.addAttribute("video",video);
        model.addAttribute("related",media.related(t,youtubeId,8));
        List<String> names=splitNames(video.getArtistNames());
        List<Schedule> relatedSchedules=scheduleRepository.findAll().stream()
                .filter(s->!names.isEmpty()&&splitNames(s.getArtistNames()).stream().anyMatch(names::contains)).toList();
        model.addAttribute("relatedSchedules",relatedSchedules);
        model.addAttribute("taggedArtists",artistRepository.findAll().stream().filter(a->names.contains(a.getName())).toList());
        return "archive/media_detail";
    }

    @GetMapping("/media_new")
    public String newMediaForm(@RequestParam(defaultValue="mv")String tab,Model model) {
        MediaDto form=MediaDto.builder().tab(MediaTab.from(tab).getKey()).addFirst(true).build();
        bindTabFlags(model,form.normalizedTab()); model.addAttribute("artistOptions",artistRepository.findAll()); model.addAttribute("form",form);
        return "archive/media_new";
    }

    @PostMapping("/media_new")
    public String createNewMedia(@org.springframework.validation.annotation.Validated @ModelAttribute("form")MediaDto form,
                                 BindingResult binding,
                                 @RequestParam(value="artistNames",required=false)List<String> artistNames,
                                 Model model)throws Exception {
        String tab=form!=null?form.normalizedTab():"mv"; String rawId=form!=null?form.getYoutubeId():"";
        String ytId=YoutubeIdExtractor.extract(rawId);
        if(ytId==null||ytId.isBlank()||!ytId.matches("^[a-zA-Z0-9_-]{11}$"))binding.rejectValue("youtubeId","invalid","유효한 유튜브 ID 또는 URL을 입력하세요.");
        else form.setYoutubeId(ytId);
        if(binding.hasErrors()){bindTabFlags(model,tab);model.addAttribute("form",form);model.addAttribute("artistOptions",artistRepository.findAll());return "archive/media_new";}
        media.registerMedia(tab,form,artistNames==null?"":String.join(",",artistNames));
        return "redirect:/media?tab="+tab;
    }

    @PostMapping("/media_delete")
    public String deleteMedia(@RequestParam String tab,@RequestParam String youtubeId)throws Exception {
        String t=MediaTab.from(tab).getKey();media.remove(t,youtubeId);return "redirect:/media?tab="+t;
    }
    private List<String> splitNames(String names){if(names==null||names.isBlank())return List.of();return Arrays.stream(names.split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().toList();}
    private String normalizeTab(String tab){return switch(tab.toLowerCase()){case "mv","signal","behind","secret","contents"->tab.toLowerCase();default->"mv";};}
    private void bindTabFlags(Model model,String t){model.addAttribute("tab",t);model.addAttribute("tab_mv","mv".equals(t));model.addAttribute("tab_signal","signal".equals(t));model.addAttribute("tab_behind","behind".equals(t));model.addAttribute("tab_secret","secret".equals(t));model.addAttribute("tab_contents","contents".equals(t));}
}