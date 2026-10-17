package com.example.triplesweb.config;

import com.example.triplesweb.entity.*;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.UnitRepository;
import com.example.triplesweb.util.TrackInfo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DB에 앨범이 없을 때 아티스트·유닛·앨범·수록곡 시드 데이터를 한 번 적재한다.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ArtistRepository artistrepository;
    private final AlbumRepository albumrepository;
    private final UnitRepository unitrepository;

    @Override
    @Transactional // 지연 로딩 및 Cascade 연관관계 저장을 위해 트랜잭션 보장
    public void run(String... args) throws Exception {

        if (albumrepository.count() > 0) {
            return;
        }

        // 1. Artist (아티스트 8명) 생성 및 저장
        List<Artist> artistsInfo = List.of(
                new Artist(null, "윤서연", "seoyeon", "대한민국", "2003-08-06", "대전광역시 유성구", "2022-05-01", new ArrayList<>(), "/uploads/images/seoyeon.jpg", "#22aeff"),
                new Artist(null, "정혜린", "hyerin", "대한민국", "2007-04-12", "대구광역시 수성구", "2022-05-17", new ArrayList<>(), "/uploads/images/hyerin.jpg", "#9200ff"),
                new Artist(null, "이지우", "jiwoo", "대한민국", "2005-10-24", "서울특별시 강남구", "2022-06-07", new ArrayList<>(), "/uploads/images/jiwoo.jpg", "#fff800"),
                new Artist(null, "김채연", "chaeyeon", "대한민국", "2004-12-04", "서울특별시", "2022-06-29", new ArrayList<>(), "/uploads/images/chaeyeon.jpg", "#98f21d"),
                new Artist(null, "김유연", "yooyeon", "대한민국", "2001-02-09", "서울특별시 강남구", "2022-07-18", new ArrayList<>(), "/uploads/images/yooyeon.jpg", "#db0c74"),
                new Artist(null, "김수민", "soomin", "대한민국", "2007-10-03", "대구광역시 중구", "2022-08-11", new ArrayList<>(), "/uploads/images/soomin.jpg", "#fc83a4"),
                new Artist(null, "김나경", "nakyoung", "대한민국", "2002-10-13", "울산광역시 중구", "2022-08-25", new ArrayList<>(), "/uploads/images/nakyoung.jpg", "#6799a0"),
                new Artist(null, "공유빈", "yubin", "대한민국", "2005-02-03", "서울특별시 강남구", "2022-09-12", new ArrayList<>(), "/uploads/images/yubin.jpg", "#ffe3e2"),
                new Artist(null, "카에데", "kaede", "일본", "2005-12-20",  "도야마현 토나미시", "2022-11-14", new ArrayList<>(), "/uploads/images/kaede.jpg","#ffc935"),
                new Artist(null, "서다현", "dahyun", "대한민국", "2003-01-08", "부산광역시 수영구", "2022-12-12", new ArrayList<>(), "/uploads/images/dahyun.jpg","#ff9ad6"),
                new Artist(null, "코토네", "kotone", "일본", "2004-03-10", "도쿄도 코쿠분지시", "2003-01-05", new ArrayList<>(), "/uploads/images/kotone.jpg", "#ffde00"),
                new Artist(null, "곽연지", "yeonji", "대한민국", "2008-01-08", "경기도 광주시 초월읍", "2023-01-20", new ArrayList<>(), "/uploads/images/yeonji.jpg", "#5974ff"),
                new Artist(null, "니엔", "nien", "대만", "2003-06-02", "신베이시 신좡구", "2023-03-24", new ArrayList<>(), "/uploads/images/nien.jpg", "#ff953f"),
                new Artist(null, "박소현", "sohyun", "대한민국", "2002-10-13", "서울특별시 송파구", "2023-04-18", new ArrayList<>(), "/uploads/images/sohyun.jpg", "#1222b5"),
                new Artist(null, "신위", "xinyu", "중국", "2002-05-25", "베이징시 하이뎬구", "2023-07-04", new ArrayList<>(), "/uploads/images/xinyu.jpg", "#d51313"),
                new Artist(null, "마유", "mayu", "일본", "2002-05-12", "군마현 호지오카시", "2023-07-24", new ArrayList<>(), "/uploads/images/mayu.jpg", "#fe8e76"),
                new Artist(null, "린", "lynn", "일본", "2006-04-12", "도쿄도 니시타마군", "2024-01-16", new ArrayList<>(), "/uploads/images/lynn.jpg", "#ac62b7"),
                new Artist(null, "주빈", "joobin", "대한민국", "2009-01-16", "서울특별시 성북구", "2024-01-17", new ArrayList<>(), "/uploads/images/joobin.jpg", "#b7f54c"),
                new Artist(null, "정하연", "hayeon", "대한민국", "2007-08-01", "경기도 안양시", "2024-01-22", new ArrayList<>(), "/uploads/images/hayeon.jpg", "#52d9bb"),
                new Artist(null, "박시온", "shion", "대한민국", "2006-04-03", "대전광역시 유성구", "2024-01-23", new ArrayList<>(), "/uploads/images/shion.jpg", "#ff428a"),
                new Artist(null, "김채원", "chaewon", "대한민국", "2007-05-02", "인천광역시 남동구", "2024-04-09", new ArrayList<>(), "/uploads/images/chaewon.jpg", "#c7a3e0"),
                new Artist(null, "설린", "sullin", "태국", "2006-11-30", "방콕", "2024-04-10", new ArrayList<>(), "/uploads/images/sullin.jpg","#7bba8d"),
                new Artist(null, "서아", "seoah", "대한민국", "2010-06-11", "광주광역시 북구", "2024-04-12", new ArrayList<>(), "/uploads/images/seoah.jpg", "#cff3ff"),
                new Artist(null, "지연", "jiyeon", "대한민국", "2004-02-13", "경기도 용인시", "2024-04-13", new ArrayList<>(),"/uploads/images/jiyeon.jpg","#ffab62")
        );

        for (Artist artist : artistsInfo) {
            if (!artistrepository.existsByEngName(artist.getEngName())) {
                artistrepository.save(artist);
            }
        }

        // DB에서 최신 저장된 아티스트 참조 가져오기
        Artist seoyeon = artistrepository.findByEngName("seoyeon").orElse(null);
        Artist hyerin = artistrepository.findByEngName("hyerin").orElse(null);
        Artist jiwoo = artistrepository.findByEngName("jiwoo").orElse(null);
        Artist chaeyeon = artistrepository.findByEngName("chaeyeon").orElse(null);
        Artist yooyeon = artistrepository.findByEngName("yooyeon").orElse(null);
        Artist soomin = artistrepository.findByEngName("soomin").orElse(null);
        Artist nakyoung = artistrepository.findByEngName("nakyoung").orElse(null);
        Artist yubin = artistrepository.findByEngName("yubin").orElse(null);
        Artist kaede = artistrepository.findByEngName("kaede").orElse(null);
        Artist dahyun = artistrepository.findByEngName("dahyun").orElse(null);
        Artist kotone = artistrepository.findByEngName("kotone").orElse(null);
        Artist yeonji = artistrepository.findByEngName("yeonji").orElse(null);
        Artist nien = artistrepository.findByEngName("nien").orElse(null);
        Artist sohyun = artistrepository.findByEngName("sohyun").orElse(null);
        Artist xinyu = artistrepository.findByEngName("xinyu").orElse(null);
        Artist mayu = artistrepository.findByEngName("mayu").orElse(null);
        Artist lynn = artistrepository.findByEngName("lynn").orElse(null);
        Artist joobin = artistrepository.findByEngName("joobin").orElse(null);
        Artist hayeon = artistrepository.findByEngName("hayeon").orElse(null);
        Artist shion = artistrepository.findByEngName("shion").orElse(null);
        Artist chaewon = artistrepository.findByEngName("chaewon").orElse(null);
        Artist sullin = artistrepository.findByEngName("sullin").orElse(null);
        Artist seoah = artistrepository.findByEngName("seoah").orElse(null);
        Artist jiyeon = artistrepository.findByEngName("jiyeon").orElse(null);

        // 2. Unit (유닛 4개) 생성 및 저장
            Unit aaa = new Unit(null, "ACID ANGEL OF ASIA", "aaa", "/uploads/images/aaa.jpg", "2022-09-25", "2022-10-28", null);
            Unit kre = new Unit(null, "+(KR)ystal Eyes", "kre", "/uploads/images/kre.jpg", "2022-09-25", "2023-05-04", null);
            Unit lovelution = new Unit(null, "LOVElution", "lovel", "/uploads/images/lovel.jpg", "2023-04-28", "2023-08-17", null);
            Unit evolution = new Unit(null, "EVOLution", "evol", "/uploads/images/evol.jpg", "2023-04-28", "2023-10-11", null);
            Unit aria = new Unit(null,"Aria", "aria", "/uploads/images/aria.jpg", "2023-11-24", "2024-01-15", null);
            Unit nxt = new Unit(null,"Nxt", "nxt", "/uploads/images/nxt.jpg", "2023-11-22", "2023-12-23", null);
            Unit glow = new Unit(null, "Glow", "glow", "/uploads/images/glow.jpg", "2024-04-01", "2024-06-21", null);
            Unit vv = new Unit(null, "Visionary Vision", "VV", "/uploads/images/vv.jpg", "2024-06-18", "2024-10-23", null);
            Unit msnz = new Unit (null, "MSNZ", "msnz", "/uploads/images/msnz.jpg", "2025-09-09", "2025-11-24", null);
            Unit moon = new Unit(null, "msnz Moon","moon","/uploads/images/moon.jpg", "2025-09-09", "2025-11-24", msnz);
            Unit sun = new Unit(null,"msnz Sun","sun","/uploads/images/sun.jpg","2025-09-09","2025-11-24", msnz);
            Unit neptune = new Unit(null,"msnz Neptune", "neptune","/uploads/images/neptune.jpg","2025-09-09","2025-11-24", msnz);
            Unit zenith = new Unit (null,"msnz Zenith","zenith","/uploads/images/zenith.jpg","2025-09-09","2025-11-24", msnz);

            msnz.getSubUnits().addAll(List.of(moon, sun, neptune, zenith));
            unitrepository.saveAll(List.of(aaa, kre, lovelution, evolution, aria, nxt, glow, vv, msnz, moon, sun, neptune, zenith));



            // 3. Artist ↔ Unit 매핑 (UnitMember 생성)
            addUnitMembers(aaa, List.of(hyerin, yooyeon, nakyoung, yubin));
            addUnitMembers(kre, List.of(seoyeon, jiwoo, chaeyeon, soomin));
            addUnitMembers(lovelution, List.of(seoyeon, hyerin, yubin, kaede, dahyun, nien, sohyun, xinyu));
            addUnitMembers(evolution, List.of(jiwoo, chaeyeon, yooyeon, soomin, nakyoung, kotone, yeonji, mayu));
            addUnitMembers(aria, List.of(jiwoo, chaeyeon, dahyun, kaede, nien));
            addUnitMembers(nxt, List.of(lynn, joobin, hayeon, shion));
            addUnitMembers(glow, List.of(chaewon, sullin, seoah, jiyeon));
            addUnitMembers(vv,  List.of(hyerin, yooyeon, nakyoung, yubin, kaede, kotone, yeonji, nien, sohyun, xinyu, lynn, jiyeon));
            addUnitMembers(moon, List.of(kaede, sohyun, lynn, shion, sullin, jiyeon));
            addUnitMembers(sun, List.of (hyerin, chaeyeon, yooyeon, xinyu, mayu, chaewon));
            addUnitMembers(neptune, List.of(seoyeon, nakyoung, dahyun, kotone, nien, seoah));
            addUnitMembers(zenith, List.of(jiwoo, soomin, yubin, yeonji, joobin, hayeon));
            unitrepository.saveAll(List.of(aaa, kre, lovelution, evolution, aria, nxt, glow, vv, msnz, moon, sun, neptune, zenith));


        // 4. Album (앨범 4개) 생성 및 해당 Unit 매핑 후 저장

            Album access = new Album(null, "ACCESS", "/uploads/images/access.jpg", "2022-10-28",
                            "트리플에스 aaa의 첫 앨범", new ArrayList<>(), aaa);
            Album assemble = new Album(null, "ASSEMBLE", "/uploads/images/assemble.jpg", "2023-02-13",
                    "트리플에스 완전체 첫 앨범", new ArrayList<>(), null);
            Album aesthetic = new Album(null, "AESTHETIC", "/uploads/images/aesthetic.jpg", "2023-05-04",
                            "트리플에스 크아의 첫 앨범", new ArrayList<>(), kre);
            Album lovelAlbum = new Album(null, "ↀ", "/uploads/images/ↀ.jpg", "2023-08-17",
                            "트리플에스 러블루션의 첫 앨범", new ArrayList<>(), lovelution);
            Album evolAlbum  = new Album(null, "⟡", "/uploads/images/⟡.jpg", "2023-10-11",
                            "트리플에스 에볼루션의 첫 앨범", new ArrayList<>(), evolution);
            Album sadness = new Album(null, "Structure of Sadness","/uploads/images/sadness.jpg","2024-01-15",
                    "트리플에스 아리아의 앨범",new ArrayList<>(), aria);
            Album vvAlbum = new Album(null, "PEFORMANTE", "/uploads/images/vv.jpg","2024-10-23",
                    "트리플에스 븨븨의 앨범", new ArrayList<>(), vv);
            Album nxtAlbum = new Album(null,"Just do it","/uploads/images/justdoit.jpg","2023-12-23",
                    "트리플에스 넥스트의 앨범", new ArrayList<>(), nxt);
            Album assemble24 = new Album(null,"ASSEMBLE24","/uploads/images/assemble24.jpg","2024-05-08",
                    "트리플에스 24명 완전체 앨범",new ArrayList<>(),null);
            Album glowAlbum = new Album(null,"Inner dance","/uploads/images/innerdance.jpg","2024-06-21",
                    "트리플에스 글로우의 앨범", new ArrayList<>(), glow);
            Album assemble25 = new Album(null,"ASSEMBLE25","/uploads/images/assemble25.jpg","2025-05-21",
                "트리플에스 24명 두번째 완전체 앨범",new ArrayList<>(),null);
            Album beyondBeauty = new Album(null, "Beyond Beauty", "/uploads/images/msnz.jpg", "2025-11-24",
                    "트리플에스 미소녀즈의 앨범", new ArrayList<>(), msnz);
            Album loveSide = new Album(null,"LOVE & POP Pt.1","/uploads/images/loveandpop1.jpg","2026-06-01",
                "트리플에스 2026년 첫 앨범", new ArrayList<>(),null);

            if (msnz != null) msnz.getAlbums().add(beyondBeauty);
            if (aaa != null) aaa.getAlbums().add(access);
            if (kre != null) kre.getAlbums().add(aesthetic);
            if (lovelution != null) lovelution.getAlbums().add(lovelAlbum);
            if (evolution != null) evolution.getAlbums().add(evolAlbum);
            if (aria != null) aria.getAlbums().add(sadness);
            if (vv != null) vv.getAlbums().add(vvAlbum);
            if (nxt != null) nxt.getAlbums().add(nxtAlbum);
            if (glow != null) glow.getAlbums().add(glowAlbum);

            addTracksToAlbum(access, TrackInfo.ACCESS_TRACK);
            addTracksToAlbum(aesthetic, TrackInfo.AESTHETIC_TRACK);
            addTracksToAlbum(lovelAlbum, TrackInfo.LOVELALBUM_TRACK);
            addTracksToAlbum(evolAlbum, TrackInfo.EVOLALBUM_TRACK);
            addTracksToAlbum(sadness, TrackInfo.ARIAALBUM_TRACK);
            addTracksToAlbum(vvAlbum, TrackInfo.VVALBUM_TRACK);
            addTracksToAlbum(nxtAlbum, TrackInfo.NXTALBUM_TRACK);
            addTracksToAlbum(glowAlbum, TrackInfo.GLOWALBUM_TRACK);
            addTracksToAlbum(beyondBeauty, TrackInfo.BEYONDBEAUTY_TRACK);
            addTracksToAlbum(assemble, TrackInfo.ASSEMBLE_TRACK);
            addTracksToAlbum(assemble24, TrackInfo.ASSEMBLE24_TRACK);
            addTracksToAlbum(assemble25, TrackInfo.ASSEMBLE25_TRACK);
            addTracksToAlbum(loveSide, TrackInfo.LOVESIDE_TRACK);

            albumrepository.saveAll(List.of(access, assemble, aesthetic, lovelAlbum, evolAlbum, sadness, vvAlbum, nxtAlbum, assemble24, glowAlbum, assemble25, beyondBeauty, loveSide));


    }

    private void addTracksToAlbum(Album album, TrackInfo trackList) {
        if (album == null || trackList == null) return;

        for (int i = 0; i < trackList.getTitle().size(); i++) {
            int trackNum = i + 1;
            String title = trackList.getTitle().get(i);
            String duration = trackList.getDuration().get(i);
            Boolean isTitle = trackList.getIsTitle().get(i);
            // Track 객체 생성 후 Album의 tracks 리스트에 추가
            Track track = new Track(null, trackNum, title, duration, isTitle, album);
            album.getTracks().add(track);
        }
    }


    // 유닛과 아티스트 다대다 연결 헬퍼 메소드
    private void addUnitMembers(Unit unit, List<Artist> artists) {
        if (unit == null) return;
        for (Artist artist : artists) {
            if (artist != null) {
                UnitMember um = new UnitMember(null, artist, unit);
                unit.getUnitMembers().add(um);
            }
        }
    }
}

