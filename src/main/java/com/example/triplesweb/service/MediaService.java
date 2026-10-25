package com.example.triplesweb.service;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.infra.YoutubeOembedClient;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.MediaRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.util.ISODuration;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
public class MediaService {
    private final MediaRepository mediaRepository;
    private final ArtistRepository artistRepository;
    private final YoutubeOembedClient oembed;
    private final YoutubeDataApiClient yt;

    @Transactional(readOnly = true)
    public List<MediaDto> pageByTab(String tab, int page, int size) {
        return mediaRepository.findByTabOrderBySortOrderAscIdAsc(tab, PageRequest.of(Math.max(0,page-1),size)).map(this::toDto).getContent();
    }
    @Transactional(readOnly = true)
    public Optional<MediaDto> findOne(MediaTab tab, String youtubeId) {
        return mediaRepository.findByTabAndYoutubeIdIgnoreCase(tab.getKey(),youtubeId).map(this::toDto);
    }
    @Transactional(readOnly = true)
    public List<MediaDto> related(MediaTab tab, String excludeId, int limit) {
        return mediaRepository.findByTabOrderBySortOrderAscIdAsc(tab.getKey()).stream().filter(v->!v.getYoutubeId().equalsIgnoreCase(excludeId)).limit(limit).map(this::toDto).toList();
    }
    @Transactional(readOnly = true)
    public List<MediaDto> allByTab(String tab) {
        return mediaRepository.findByTabOrderBySortOrderAscIdAsc(tab).stream().map(this::toDto).toList();
    }
    @Transactional(readOnly = true)
    public int totalByTab(String tab) { return (int) mediaRepository.countByTab(tab); }

    public void addMedia(String tab, MediaDto dto, boolean addFirst) {
        addMedia(tab,dto,addFirst,dto == null ? "" : dto.getArtistNames());
    }
    public void addMedia(String tab, MediaDto dto, boolean addFirst, String artistNames) {
        if(dto==null || dto.getYoutubeId()==null || dto.getYoutubeId().isBlank()) return;
        String ytId=dto.getYoutubeId().trim();
        mediaRepository.findByTabAndYoutubeIdIgnoreCase(tab,ytId).ifPresent(mediaRepository::delete);
        long sortOrder=addFirst ? mediaRepository.minSortOrder(tab)-1 : mediaRepository.maxSortOrder(tab)+1;
        Media entity=Media.builder().youtubeId(ytId).tab(tab)
                .mediaTitle(dto.getTitle()!=null?dto.getTitle():"")
                .mediaDur(dto.getDuration()!=null?dto.getDuration():"")
                .viewCount(dto.getViews()!=null?dto.getViews():0L)
                .description(truncateDescription(dto.getDescription()))
                .publishedOn(parseDate(dto.getPublishedOn()))
                .sortOrder(sortOrder).build();
        entity.setArtists(resolveArtists(artistNames));
        mediaRepository.save(entity);
    }
    public boolean remove(String tab,String youtubeId) {
        Optional<Media> existing=mediaRepository.findByTabAndYoutubeIdIgnoreCase(tab,youtubeId);
        if(existing.isEmpty()) return false;
        mediaRepository.delete(existing.get()); return true;
    }
    public Map<String,Object> makeYoutubeInfo(String urlOrId) {
        String id=YoutubeIdExtractor.extract(urlOrId);
        if(id==null||id.isBlank()) id=urlOrId.trim();
        if(id.length()!=11) { Map<String,Object> fallback=new LinkedHashMap<>(); fallback.put("youtubeId",urlOrId!=null?urlOrId:""); fallback.put("title","올바르지 않은 유튜브 ID 또는 링크입니다."); fallback.put("thumbnail",""); fallback.put("duration","03:00"); fallback.put("views",0L); return fallback; }
        Map<String,Object> res=new LinkedHashMap<>(); res.put("youtubeId",id); res.put("title","유튜브 영상 ("+id+")"); res.put("thumbnail","https://img.youtube.com/vi/"+id+"/hqdefault.jpg"); res.put("duration","03:00"); res.put("views",0L);
        try { var oe=oembed.fetch("https://www.youtube.com/watch?v="+id); if(oe!=null) { if(oe.getTitle()!=null&&!oe.getTitle().isBlank())res.put("title",oe.getTitle()); if(oe.getThumbnail_url()!=null&&!oe.getThumbnail_url().isBlank())res.put("thumbnail",oe.getThumbnail_url()); } } catch(Exception ignored) {}
        try { var vr=yt.fetchVideo(id); if(vr!=null&&vr.getItems()!=null&&vr.getItems().length>0) { var item=vr.getItems()[0]; if(item.getSnippet()!=null) { if(item.getSnippet().getTitle()!=null&&!item.getSnippet().getTitle().isBlank())res.put("title",item.getSnippet().getTitle()); if(item.getSnippet().getDescription()!=null)res.put("description",item.getSnippet().getDescription()); String pub=item.getSnippet().getPublishedAt(); if(pub!=null&&pub.length()>=10){ String day=pub.substring(0,10); res.put("publishedAt",day.replace("-",".")); res.put("publishedOn",day); } } if(item.getContentDetails()!=null&&item.getContentDetails().getDuration()!=null) { String d=ISODuration.toClock(item.getContentDetails().getDuration()); if(d!=null&&!d.isBlank())res.put("duration",d); } if(item.getStatistics()!=null&&item.getStatistics().getViewCount()!=null)res.put("views",item.getStatistics().getViewCount()); } } catch(Exception ignored) {}
        return res;
    }
    public void registerMedia(String tab,MediaDto form) { registerMedia(tab,form,form==null?"":form.getArtistNames()); }
    public void registerMedia(String tab,MediaDto form,String artistNames) {
        if(form==null||form.getYoutubeId()==null||form.getYoutubeId().isBlank()) return;
        String rawId=form.getYoutubeId().trim(); String ytId=YoutubeIdExtractor.extract(rawId); if(ytId==null||ytId.isBlank()) ytId="Unknown";
        String title=form.getTitle()==null?"":form.getTitle().trim(); String dur=form.getDuration()==null?"":form.getDuration().trim();
        long views=form.getViews()!=null?form.getViews():0L; String description=form.getDescription()==null?"":form.getDescription().trim();
        if(!ytId.isBlank()&&(title.isBlank()||dur.isBlank()||dur.equals("03:00"))) try {
            Map<String,Object> info=makeYoutubeInfo(ytId);
            if(title.isBlank()&&info.get("title")!=null)title=(String)info.get("title");
            if((dur.isBlank()||dur.equals("03:00"))&&info.get("duration")!=null)dur=(String)info.get("duration");
            if(views==0&&info.get("views")!=null)views=Long.parseLong(info.get("views").toString());
            if(description.isBlank()&&info.get("description")!=null)description=(String)info.get("description");
        } catch(Exception ignored) {}
        String publishedOn=form.getPublishedOn()==null?"":form.getPublishedOn().trim();
        if(publishedOn.isBlank()) try {
            Map<String,Object> info=makeYoutubeInfo(ytId);
            if(info.get("publishedOn")!=null) publishedOn=info.get("publishedOn").toString();
        } catch(Exception ignored) {}
        MediaDto dto=MediaDto.builder().youtubeId(ytId).title(title).duration(dur).views(views).viewCountFmt(NumberFormat.getInstance().format(views)).description(description).artistNames(artistNames==null?"":artistNames).publishedOn(publishedOn).build();
        addMedia(tab,dto,form.isAddFirst(),artistNames);
    }
    private Set<Artist> resolveArtists(String names) {
        Set<Artist> result = new LinkedHashSet<>();
        if (names == null || names.isBlank()) return result;
        for (String raw : names.split(",")) {
            String name = raw.trim();
            if (name.isBlank()) continue;
            artistRepository.findByName(name)
                    .or(() -> artistRepository.findByEngNameIgnoreCase(name))
                    .ifPresent(result::add);
        }
        return result;
    }

    /**
     * 아티스트별 미디어 목록을 구성한다.
     * 각 아티스트에게 정렬 순서대로 최대 limit개를 제공한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<MediaDto>> byArtistMap(int limit) {
        Map<Long, List<MediaDto>> result = new LinkedHashMap<>();

        if (limit <= 0) {
            return result;
        }

        List<Media> mediaList = mediaRepository.findAllWithArtists();

        NumberFormat numberFormat =
                NumberFormat.getIntegerInstance(Locale.KOREA);

        for (Media media : mediaList) {
            if (media.getArtists() == null || media.getArtists().isEmpty()) {
                continue;
            }

            Long views = media.getViewCount() == null
                    ? 0L
                    : media.getViewCount();

            MediaDto dto = MediaDto.builder()
                    .tab(media.getTab())
                    .youtubeId(media.getYoutubeId())
                    .title(media.getMediaTitle())
                    .views(views)
                    .viewCountFmt(numberFormat.format(views))
                    .duration(media.getMediaDur())
                    .description(media.getDescription())
                    .artistNames(media.getArtistNames())
                    .build();

            for (Artist artist : media.getArtists()) {
                Long artistId = artist.getArtistId();

                if (artistId == null) {
                    continue;
                }

                List<MediaDto> artistMedia =
                        result.computeIfAbsent(
                                artistId,
                                key -> new ArrayList<>()
                        );

                if (artistMedia.size() < limit) {
                    artistMedia.add(dto);
                }
            }
        }

        return result;
    }

    private MediaDto toDto(Media m) {
        long views=m.getViewCount()!=null?m.getViewCount():0L;
        return MediaDto.builder().tab(m.getTab()).youtubeId(m.getYoutubeId()).title(m.getMediaTitle()).duration(m.getMediaDur()).views(views).viewCountFmt(NumberFormat.getInstance().format(views)).description(m.getDescription()).artistNames(m.getArtistNames()).publishedOn(m.getPublishedOn()==null?"":m.getPublishedOn().toString()).build();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.length() >= 10 && value.charAt(4) == '.') value = value.substring(0, 10).replace('.', '-');
        try { return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value); }
        catch (DateTimeParseException ex) { return null; }
    }

    /** TEXT 컬럼 한도 안에서 저장 (유튜브 설명이 매우 긴 경우 대비) */
    private static String truncateDescription(String description) {
        if (description == null || description.isBlank()) return "";
        String trimmed = description.trim();
        final int max = 60_000;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}