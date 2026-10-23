package com.example.triplesweb.config;

import com.example.triplesweb.entity.*;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.UnitRepository;
import com.example.triplesweb.util.TrackInfo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DB에 앨범이 없을 때 아티스트·유닛·앨범·수록곡 시드 데이터를 한 번 적재한다.
 * {@link com.example.triplesweb.service.ChemistrySeedService}보다 먼저 실행되어야 한다.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ArtistRepository artistrepository;
    private final AlbumRepository albumrepository;
    private final UnitRepository unitrepository;


    @Transactional // 지연 로딩 및 Cascade 연관관계 저장을 위해 트랜잭션 보장
    public void run(ApplicationArguments args) throws Exception {

        if (albumrepository.count() > 0) {
            return;
        }

        // 1. Artist (아티스트 8명) 생성 및 저장
        List<Artist> artistsInfo = List.of(
                new Artist(null, "윤서연", "seoyeon", "대한민국", "2003-08-06", null, "대전광역시 유성구", "2022-05-01", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w68zHOYJBtMY32IkaXifE0L-lSc6XmhsCqSNKBh63W8hWwKvilDFHoxQTXbf-Pu__Oq3e7HuoWM0NqxN_7Ovg8TZ48z8WZRCQJ2zN4jHfgy14cJyb45M5hNEv1Cl2kSgjug1EZdCEdq9_4zGPXv9eto.webp", "#22aeff", null, null),
                new Artist(null, "정혜린", "hyerin", "대한민국", "2007-04-12", null, "대구광역시 수성구", "2022-05-17", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w4YLn5UqkiKFOOnuYVw2I3KLM7mw6JD_fzlDf3J8bmY3c5yoEYuP77Rd1xwxSWHmE_CjaElEKVSCLMgP4fZMFd4j0Qv7fuOKpb0J0GlPxzdv2BZEDalSN2_Qd46XiZhbeuoQw3rzBF7Iu7VxEYC39c0.webp", "#9200ff", null, null),
                new Artist(null, "이지우", "jiwoo", "대한민국", "2005-10-24", null, "서울특별시 강남구", "2022-06-07", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w9at-WkbWd07laZGmdFKk3zy6O2xqaurG_NNiDDY0LANnnH7P-M_Nk6NUVoPUbsW3SAhtMszCxf7JVCHAu9dL_00yVcQsL6QgEFIjcKD_JSJPVlMmSkn4VOQ_Fs0uF3_rxty-iw3pf6DL-wrCrG8Cao.webp", "#fff800", null, null),
                new Artist(null, "김채연", "chaeyeon", "대한민국", "2004-12-04", null, "서울특별시", "2022-06-29", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w8a_R9BZ0tioI-VDutV727DI3gCtnkAE7Da3HoqqatVMBJd9oD_6936lxo7z76RU4OOlE1KRUUzC2nYLA2IMrcaZd6EW7nyt9_AB5OJDtZxP76FfXe3iyaUjxMeKSJBH41mL7U8ltc1icvIKM5YhipE.webp", "#98f21d", null, null),
                new Artist(null, "김유연", "yooyeon", "대한민국", "2001-02-09", null, "서울특별시 강남구", "2022-07-18", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w6cw31IHS8Zx7k2pVIJirXi76msqUpTVJPA74N43-BY9XoDdE_e23u8NNkbWmc3vcgn9jSVN-6_BSwcV1757sEf4EQbnlBWQFM3Nqg8gYVP6MvtzzFt84kfDdvqCI3FCPW_TNk3FzcrKe9UD_ZOxSFo.webp", "#db0c74", null, null),
                new Artist(null, "김수민", "soomin", "대한민국", "2007-10-03", null, "대구광역시 중구", "2022-08-11", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w1IAjfvpC-dyADV7EZfMa8M3FYFHSkSE2nZz7Yd75vsMeQMbBWvq43EtInHt7dGh9Q1leQKtpjTLu02XksBbmH6iFjYlUDDyHuvUzrQA9M6OK9SVptkmJaPaW0A0R_fEpFDznOqz90WxQkyCvD-gIwY.webp", "#fc83a4", null, null),
                new Artist(null, "김나경", "nakyoung", "대한민국", "2002-10-13", null, "울산광역시 중구", "2022-08-25", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0wybDxIz9ovmFA4dtBgG-0vc7e8q7JbWZsRPDXJREsLhewbJxw_Lw9aFSJZkOGVBdmZ1KkXIpvaXJHJ1zWCu1Q8nggJSHpdGwxiIJS9jvAIKitliA73AoY4cBQ1FAwd6s_O6Ssk_Wfhpv9mRkRh02TUc.webp", "#6799a0", null, null),
                new Artist(null, "공유빈", "yubin", "대한민국", "2005-02-03", null, "서울특별시 강남구", "2022-09-12", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w2sQiT0DVB9D8kY8D2KYdYQaP0E7XLFwpz3M8VupdadnYPKmH0bIF23bT2oiS126tFnxzreOUXne4ZfQUu5hyrtiIFoP2SHhsQQ2RCGrtJEG_7SdffZwzCLOhWb-faQKyE-h6uUxMg0bpiD15S209zU.webp", "#ffe3e2", null, null),
                new Artist(null, "카에데", "kaede", "일본", "2005-12-20", null,  "도야마현 토나미시", "2022-11-14", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w2LtApvJQTCR6wrCQPiPkKp-t-4GUzDZkM3u8kvmMZqdhv7MaHI9GNTix17-7_4wOZMn-pCXzxlCIusPAyb7A2pYeRNhMGtVphZDVO3shpQl4yICjAcoB8MKleIdtPSrT5Lp_JqmN5PQUqrO1B82N_0.webp","#ffc935", null, null),
                new Artist(null, "서다현", "dahyun", "대한민국", "2003-01-08", null, "부산광역시 수영구", "2022-12-12", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w5Yd3O_oD_O_Vp69lFGda59hT6WcSuNPFbTYRS3h4y1I4Adl-NIOBQe0C1MLoXq_EucuMPiOE0cwFd9lW_QEFPc7fjGDitZZWHxOKYKZKRyu6FaR7olxdlTZprmEnA2j83lwBe6xUtAP-YKItleDoEA.webp","#ff9ad6", null, null),
                new Artist(null, "코토네", "kotone", "일본", "2004-03-10", null, "도쿄도 코쿠분지시", "2003-01-05", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w_vbWdVCTpvkvHyLJx1ytGETFE_7ClYnCND6VbTV6DAOYFvQbvF4J0RMKZTBVRwFUsjZX1TyTD2k4VakNegUQd8HMOePZLFTLTnTSokIFDcK5NHGX595wbbI7lA4apQI_t88lNaEmWyhgcWbGE6z-ow.webp", "#ffde00", null, null),
                new Artist(null, "곽연지", "yeonji", "대한민국", "2008-01-08", null, "경기도 광주시 초월읍", "2023-01-20", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0wzkpzlS0MMGfx2CBeOBOQCGtqphX6PVnpZNGQbhfTKMD35aykYKc23iSf6TCT8h0Fx14b_FtlCRQIh5KAdhb2X1UTS24yRsb9gbG86pcSNGfs_sDKyn_SZYa8P1bt8JZSj7iWqc2wYPVeE0Jv-KZAlg.webp", "#5974ff", null, null),
                new Artist(null, "니엔", "nien", "대만", "2003-06-02", null, "신베이시 신좡구", "2023-03-24", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0wzXBXT9IHJg8VQteBcasCJw77tovyqYSXK95PmzZg9OKNT7Kcmx7tcksRDc5hO55UBhO6KwHnrzQgt6Zn7Y5COu60kvZKuvSkdU1Sx8L3cTeEs1gXmk6BEptcvm2cyVchfnJ7KJZPmaU00rRR5FD7_4.webp", "#ff953f", null, null),
                new Artist(null, "박소현", "sohyun", "대한민국", "2002-10-13", null, "서울특별시 송파구", "2023-04-18", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w_jlOWL1r4Pmv5bqUzqJrkmFRkd3K0seeP5ZP1xTPb-Ebq7S-E4EmXCHBfRt-pF9EKupXg9yTYmdRiP2zzXrGDkVapHgvRdrKdgBju8IicTZQZxNQvYI2DjK28On2A-Pu5Y2YMPgBA_nEycy206yeRY.webp", "#1222b5", null, null),
                new Artist(null, "신위", "xinyu", "중국", "2002-05-25", null, "베이징시 하이뎬구", "2023-07-04", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w9QORF9VxtOV2RcE9HvGNuxsOzYEnOZsvshvWUuLuGMeADLwGY0XyQYCrNOBv8Pr-TgquFqlrPw_zy1KBdAFU-DmgTZAUSZcKeMR-eNQZfPn-1tgciKwsjVcaSAlX-coRExMTXOI-Zo-ukUasGLSu3A.webp", "#d51313", null, null),
                new Artist(null, "마유", "mayu", "일본", "2002-05-12", null, "군마현 호지오카시", "2023-07-24", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w664qjpwdKguDHjAYpmwd4qeZ1Q8GwLXZaHhv5iqIy14TqzTNYIyjZoFWqhSHz3i4GiHxojVj6UY3L_cweXtV4MWhP34A7SkKoPA3yjfx8oHyko0qggvUwScSGQA0nloIjzwbiAtvuNJzYn-2AIOrU0.webp", "#fe8e76", null, null),
                new Artist(null, "린", "lynn", "일본", "2006-04-12", null, "도쿄도 니시타마군", "2024-01-16", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w-ObUNdojWV-cW1DuFh1pxwZeC7OqVi_rIelETUk6MDt4N24N6gSU0RW6YUyWtzP2x0hj71CStvTQ5aZoh4_26vZOCehnJBm-TXXExxFGPppN4mRA4US99bC7N2wpE-9PyzGwk2AuRyXSlord35Xsa8.webp", "#ac62b7", null, null),
                new Artist(null, "주빈", "joobin", "대한민국", "2009-01-16", null, "서울특별시 성북구", "2024-01-17", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w20z42O9lu0PDdhdngjxdu7UYCMqvsO3VWtj9a8TuoedbymIt_l1XTzcRf6xnrdyvEAQan9HWCSBa8XVjB8fAEho8z-2MfbylNbJTv16W7rsmsCYPMz4fzMmdOmHpBH7Mj686dRYsqR2OZHB0CRJuYI.webp", "#b7f54c", null, null),
                new Artist(null, "정하연", "hayeon", "대한민국", "2007-08-01", null, "경기도 안양시", "2024-01-22", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0wxIyHZJ0p8Jwinb8XivgLrq1h-TiYlBDLXEVSbkpcweA6nXiWIAyA-yJpcTeTWf3ffPiYgn8BcYxYwcU7NxDUINdxlYKXOKeClKRs9BAEz0KOJ524mFX7het-BiHbJSemrAtzT4n8I_Z59ZyhK0Xar8.webp", "#52d9bb", null, null),
                new Artist(null, "박시온", "shion", "대한민국", "2006-04-03", null, "대전광역시 유성구", "2024-01-23", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w0nz1GYankyHr76uHk-bAM544rxDe0BBBpaYf7l73pVEAde25Rd0VvZnJK_FMWW_Uxtt897uu_iEiNWJve4F6cK55CYig4SYW0AjVtYhPRiEXrHEqWbiRTX83iv0DgKFeLCKo8HaBQMoVzjJ8dOTglI.webp", "#ff428a", null, null),
                new Artist(null, "김채원", "chaewon", "대한민국", "2007-05-02", null, "인천광역시 남동구", "2024-04-09", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w_NrQBWa0aClalB9OQSlqU8W8AVT_yx0HJPlXFxyd6cRu_ZAX0tLCBwJ4hXmmhKuKH--7pvLUsPtO0yAlgbqsRYGX5UhqFaTR3OjBdxe58R-w_v20zH1fMx-C_zA4lBSHSWnyZQMrVyUUzNF5KD2IrY.webp", "#c7a3e0", null, null),
                new Artist(null, "설린", "sullin", "태국", "2006-11-30", null, "방콕", "2024-04-10", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w3HySMLgOapHV-1jQ1CXXPHZMwO_vgaj95JiJQeWQb9GLE7qO-X2fUwk0KkYfvd0r_WwflAuO7MaydbvC68PYysl04TEDI5lmqPZLDLfY1aOhQHf5lKUrMvxW3Wq_0_WNATFmkJyVk3ZuXh2QJ8g140.webp","#7bba8d", null, null),
                new Artist(null, "서아", "seoah", "대한민국", "2010-06-11", null, "광주광역시 북구", "2024-04-12", new ArrayList<>(), "https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0w5bmAMaxBkcqdclJD604Rwm-I8Hv1gaQ0Gra_h6ErJawUAgutlc2l-Tw5kDT6Os3L15XFV6azRcekgXIe-Jp3F1aujkq9wyBdvKnbzEqKwz70E6EH4DBQQ17q5_XidqXNQ4CdntCeSMq1iPDUjlEgKs.webp", "#cff3ff", null, null),
                new Artist(null, "지연", "jiyeon", "대한민국", "2004-02-13", null, "경기도 용인시", "2024-04-13", new ArrayList<>(),"https://i.namu.wiki/i/Q69elsxcXHoCsi7CHMs0wxJLEBh4mV3LiHOJf93bTbszH9UyX6Jmj3HHr4NV2KezILQhDl-AMeycB-p5bXUB-7kUh6bYPBlnkbCHYIOX7uT4ERLjQZwb5HA4JX0l7qBWLss7ou98MruUj1NNSFhw8Gem8lMoWjiNRUpPq9BtAWY.webp","#ffab62", null, null)
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
            Unit acidEyes = new Unit(null, "Acid Eyes", "acideyes", "/uploads/images/acideyes.jpg", "2022-12-26", "2023-01-06", null);
            Unit msnz = new Unit (null, "MSNZ", "msnz", "/uploads/images/msnz.jpg", "2025-09-09", "2025-11-24", null);
            Unit moon = new Unit(null, "msnz Moon","moon","/uploads/images/moon.jpg", "2025-09-09", "2025-11-24", msnz);
            Unit sun = new Unit(null,"msnz Sun","sun","/uploads/images/sun.jpg","2025-09-09","2025-11-24", msnz);
            Unit neptune = new Unit(null,"msnz Neptune", "neptune","/uploads/images/neptune.jpg","2025-09-09","2025-11-24", msnz);
            Unit zenith = new Unit (null,"msnz Zenith","zenith","/uploads/images/zenith.jpg","2025-09-09","2025-11-24", msnz);
            Unit hatchi = new Unit(null, "∞!", "hatchi", "/uploads/images/hatchi.jpg", "2024-09-01", "2024-11-20", null);
            Unit alphie = new Unit(null, "Alphie", "alphie", "/uploads/images/alphie.jpg", "2025-07-02", "2025-09-03", null);

            msnz.getSubUnits().addAll(List.of(moon, sun, neptune, zenith));
            unitrepository.saveAll(List.of(aaa, kre, lovelution, evolution, aria, nxt, glow, vv, acidEyes, msnz, moon, sun, neptune, zenith, hatchi, alphie));



            // 3. Artist ↔ Unit 매핑 (UnitMember 생성)
            addUnitMembers(aaa, List.of(hyerin, yooyeon, nakyoung, yubin));
            addUnitMembers(kre, List.of(seoyeon, jiwoo, chaeyeon, soomin));
            addUnitMembers(acidEyes, List.of(hyerin, yooyeon, nakyoung, yubin, seoyeon, jiwoo, chaeyeon, soomin));
            addUnitMembers(lovelution, List.of(seoyeon, hyerin, yubin, kaede, dahyun, nien, sohyun, xinyu));
            addUnitMembers(evolution, List.of(jiwoo, chaeyeon, yooyeon, soomin, nakyoung, kotone, yeonji, mayu));
            addUnitMembers(aria, List.of(jiwoo, chaeyeon, dahyun, kaede, nien));
            addUnitMembers(nxt, List.of(lynn, joobin, hayeon, shion));
            addUnitMembers(glow, List.of(chaewon, sullin, seoah, jiyeon));
            addUnitMembers(vv,  List.of(hyerin, yooyeon, nakyoung, yubin, kaede, kotone, yeonji, nien, sohyun, xinyu, lynn, jiyeon));
            addUnitMembers(hatchi, List.of(jiwoo, chaeyeon, yooyeon, soomin, kotone, mayu, shion, chaewon));
            addUnitMembers(alphie, List.of(yooyeon, kotone, nien, lynn, hayeon, shion, chaewon, jiyeon));
            addUnitMembers(moon, List.of(kaede, sohyun, lynn, shion, sullin, jiyeon));
            addUnitMembers(sun, List.of (hyerin, chaeyeon, yooyeon, xinyu, mayu, chaewon));
            addUnitMembers(neptune, List.of(seoyeon, nakyoung, dahyun, kotone, nien, seoah));
            addUnitMembers(zenith, List.of(jiwoo, soomin, yubin, yeonji, joobin, hayeon));
            addUnitMembers(msnz, List.of(
                    kaede, sohyun, lynn, shion, sullin, jiyeon,
                    hyerin, chaeyeon, yooyeon, xinyu, mayu, chaewon,
                    seoyeon, nakyoung, dahyun, kotone, nien, seoah,
                    jiwoo, soomin, yubin, yeonji, joobin, hayeon
            ));
            unitrepository.saveAll(List.of(aaa, kre, lovelution, evolution, aria, nxt, glow, vv, acidEyes, msnz, moon, sun, neptune, zenith, hatchi, alphie));


        // 4. Album (앨범 4개) 생성 및 해당 Unit 매핑 후 저장

            Album access = new Album(null, "ACCESS", "/uploads/images/access.jpg", "2022-10-28",
                            "트리플에스 aaa의 첫 앨범", new ArrayList<>(), aaa, AlbumType.SUBUNIT);
            Album assemble = new Album(null, "ASSEMBLE", "/uploads/images/assemble.jpg", "2023-02-13",
                    "트리플에스 완전체 첫 앨범", new ArrayList<>(), null, AlbumType.FULL_10);
            Album aesthetic = new Album(null, "AESTHETIC", "/uploads/images/aesthetic.jpg", "2023-05-04",
                            "트리플에스 크아의 첫 앨범", new ArrayList<>(), kre, AlbumType.SUBUNIT);
            Album lovelAlbum = new Album(null, "ↀ", "/uploads/images/lovelution-album.jpg", "2023-08-17",
                            "트리플에스 러블루션의 첫 앨범", new ArrayList<>(), lovelution, AlbumType.SUBUNIT);
            Album evolAlbum  = new Album(null, "⟡", "/uploads/images/evolution-album.jpg", "2023-10-11",
                            "트리플에스 에볼루션의 첫 앨범", new ArrayList<>(), evolution, AlbumType.SUBUNIT);
            Album sadness = new Album(null, "Structure of Sadness","/uploads/images/sadness.jpg","2024-01-15",
                    "트리플에스 아리아의 앨범",new ArrayList<>(), aria, AlbumType.SUBUNIT);
            Album vvAlbum = new Album(null, "PEFORMANTE", "/uploads/images/vv.jpg","2024-10-23",
                    "트리플에스 븨븨의 앨범", new ArrayList<>(), vv, AlbumType.SUBUNIT);
            Album nxtAlbum = new Album(null,"Just do it","/uploads/images/justdoit.jpg","2023-12-23",
                    "트리플에스 넥스트의 앨범", new ArrayList<>(), nxt, AlbumType.SUBUNIT);
            Album assemble24 = new Album(null,"ASSEMBLE24","/uploads/images/assemble24.jpg","2024-05-08",
                    "트리플에스 24명 완전체 앨범",new ArrayList<>(),null, AlbumType.FULL_24);
            Album glowAlbum = new Album(null,"Inner dance","/uploads/images/innerdance.jpg","2024-06-21",
                    "트리플에스 글로우의 앨범", new ArrayList<>(), glow, AlbumType.SUBUNIT);
            Album assemble25 = new Album(null,"ASSEMBLE25","/uploads/images/assemble25.jpg","2025-05-21",
                "트리플에스 24명 두번째 완전체 앨범",new ArrayList<>(),null, AlbumType.FULL_24);
            Album beyondBeauty = new Album(null, "Beyond Beauty", "/uploads/images/msnz.jpg", "2025-11-24",
                    "트리플에스 미소녀즈의 앨범", new ArrayList<>(), msnz, AlbumType.SUBUNIT);
            Album loveSide = new Album(null,"LOVE & POP Pt.1","/uploads/images/loveandpop1.jpg","2026-06-01",
                "트리플에스 2026년 첫 앨범", new ArrayList<>(),null, AlbumType.FULL_24);

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

