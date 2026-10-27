package com.example.triplesweb.service;
import com.example.triplesweb.entity.*;
import com.example.triplesweb.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ArtistSpotlightBoundedTest {
 @Test void selectedArtistUsesBoundedQueriesInsteadOfGlobalArchiveMediaAndScheduleLoads(){
  var catalog=mock(ObjektService.class);var archives=mock(ArchiveRepository.class);var media=mock(MediaService.class);var schedules=mock(ScheduleRepository.class);
  var service=new MemberSpotlightService(catalog,archives,media,schedules);
  Artist a=new Artist();a.setArtistId(1L);a.setName("윤서연");a.setEngName("SeoYeon");
  when(catalog.findCollections("triples")).thenReturn(List.of());when(media.byArtist(1L,5)).thenReturn(List.of());
  service.attach(List.of(a));
  verify(archives,times(2)).findTop40ByFile_MediaTypeAndArtistNamesContainingIgnoreCaseOrderByIdDesc(any(),eq("윤서연"));
  verify(archives,never()).findTop2000ByFile_MediaTypeOrderByIdDesc(any());
  verify(media).byArtist(1L,5);verify(media,never()).byArtistMap(anyInt());
  verify(schedules).findTop5ByArtists_ArtistIdOrderByStartDescIdDesc(1L);verify(schedules,never()).findAllWithArtists();
  assertThat(a.getMemberFeeds()).hasSize(5);
 }
}
