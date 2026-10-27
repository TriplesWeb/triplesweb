package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.*;
import com.example.triplesweb.web.TriplesMvcExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(TriplesController.class)
@AutoConfigureMockMvc(addFilters=false)
@Import({MustacheConfig.class,TriplesMvcExceptionHandler.class})
class ArtistPerformanceTest {
 @Autowired MockMvc mvc;
 @MockitoBean ArtistRepository artists;
 @MockitoBean ArtistAdminService admin;
 @MockitoBean AlbumService albums;
 @MockitoBean UnitService units;
 @MockitoBean MemberSpotlightService spotlight;
 private List<Artist> givenArtists(){
  List<Artist> list=new ArrayList<>();
  for(long i=1;i<=24;i++){Artist a=new Artist();a.setArtistId(i);a.setName("멤버 "+i);a.setEngName("member"+i);a.setColor("#8decd9");a.setProfileImage("https://image.test/portrait-"+i+".jpg");a.setSoloContent("[{\"title\":\"테스트 영상\",\"url\":\"https://youtu.be/abcdefghijk\"}]");list.add(a);}
  when(artists.findAllWithUnits()).thenReturn(list);when(artists.findById(1L)).thenReturn(Optional.of(list.get(0)));
  GroupIntroDto group=mock(GroupIntroDto.class);when(group.getTitle()).thenReturn("트리플에스");when(group.getBody()).thenReturn("그룹 소개");when(group.getImageUrl()).thenReturn("https://image.test/group.jpg");when(group.getTabImageUrl()).thenReturn("https://image.test/group.jpg");when(admin.getGroupIntro()).thenReturn(group);when(admin.listUnitOptions()).thenReturn(List.of());return list;
 }
 @Test void initialGroupViewDoesNotReadAnyMemberFeeds() throws Exception{
  givenArtists();var result=mvc.perform(get("/triples/artist")).andExpect(status().isOk())
   .andExpect(content().string(containsString("data-artist-feed-url")))
   .andExpect(content().string(not(containsString("class=\"member-activities")))).andReturn();
  verifyNoInteractions(spotlight);
  Files.createDirectories(Path.of("build/perf-pages"));Files.writeString(Path.of("build/perf-pages/artist.html"),result.getResponse().getContentAsString());
 }
 @Test void requestedFeedOnlyLoadsOneArtistAndUnknownIdIs404() throws Exception{
  var list=givenArtists();mvc.perform(get("/triples/artist/1/feeds")).andExpect(status().isOk()).andExpect(content().string(containsString("member-activities")));
  verify(spotlight).attach(List.of(list.get(0)));verify(artists,never()).findAllWithUnits();
  mvc.perform(get("/triples/artist/999/feeds")).andExpect(status().isNotFound());
 }
}
