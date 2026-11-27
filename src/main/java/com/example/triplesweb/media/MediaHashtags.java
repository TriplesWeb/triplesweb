package com.example.triplesweb.media;

import com.example.triplesweb.util.MemberPortraitOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제목·설명에서 트리플에스 언급·해시태그·핸들을 찾는다.
 * 외부 영상은 검토한 발행 채널과 공식 아티스트 협업 출처를 구분한다.
 */
public final class MediaHashtags {
    private static final Pattern TRIPLES_WORD = Pattern.compile("(?i)(^|[^A-Z0-9])TRIPLE\\s*S([^A-Z0-9]|$)");
    private static final Pattern TAG = Pattern.compile("[#@]([\\p{L}\\p{N}_]+)");
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final Pattern FAN_UPLOAD = Pattern.compile(
            "(?i)팬\\s*(?:계정|채널|튜브|편집)|\\bfan[\\s_-]*(?:account|channel|page)\\b|"
                    + "color[\\s-]*coded\\s+lyrics|\\bfmv\\b|댄스\\s*커버|dance\\s+cover|가사\\s*영상");
    private static final Pattern OTHER_KPOP = Pattern.compile(
            "(?i)(?<![A-Z0-9])("
                    + "NCT\\s*WISH|NCT\\s*127|NCT\\s*DREAM|NCT|WAYV|"
                    + "AESPA|BLACKPINK|BTS|SEVENTEEN|STRAY\\s*KIDS|ENHYPEN|"
                    + "NEWJEANS|LE\\s*SSERAFIM|LESSERAFIM|G-?IDLE|GIDLE|"
                    + "ITZY|TWICE|RED\\s*VELVET|EXO|RIIZE|BOYNEXTDOOR|"
                    + "ZEROBASEONE|ZB1|ILLIT|KISS\\s*OF\\s*LIFE|BABYMONSTER|"
                    + "NMIXX|IVE|TXT|ATEEZ|THE\\s*BOYZ|TREASURE|IZ\\*?ONE|"
                    + "ALPHA\\s*DRIVE\\s*ONE|ALPHADRIVEONE|ALD1|"
                    + "HEARTS2HEARTS|IZNA|KICKFLIP|CORTIS|AHOF|IDNTT|"
                    + "CLOSE\\s*YOUR\\s*EYES|MEOVV|UNIS|KEP1ER|STAYC|FROMIS_?9|"
                    + "FIFTY\\s*FIFTY|TWS|P1HARMONY|CRAVITY|EVNNE|NEXZ|"
                    + "XG|ARTMS|LOOSSEMBLE|VIVIZ|BILLLIE|RESCENE|KIIIKIII|"
                    + "ALLDAY\\s*PROJECT|GOT7|MONSTA\\s*X|SHINEE|MAMAMOO|"
                    + "APINK|OH\\s*MY\\s*GIRL|WJSN|GFRIEND|I\\.O\\.I|WANNA\\s*ONE"
                    + ")(?![A-Z0-9])"
                    + "|엔시티\\s*위시|엔시티|에스파|아이브|블랙핑크|방탄소년단|세븐틴|"
                    + "스트레이키즈|엔하이픈|뉴진스|르세라핌|있지|트와이스|레드벨벳|"
                    + "엑소|라이즈|보이넥스트도어|제로베이스원|아일릿|베이비몬스터|"
                    + "엔믹스|에이티즈|더보이즈|알파드라이브원|하츠투하츠|이즈나|"
                    + "킥플립|미야오|유니스|케플러|스테이씨|프로미스나인");

    private static final Set<String> REVIEWED_ORIGINAL_CHANNELS = Set.of(
            "UCHD1jo5RhijLfx5-0Ehe_cg", // &TEAM
            "UC6vdeY4HiiAIBsb4JpYex3Q", // 13년 차 신인 김채연 KimChaeYeon
            "UCqq-ovGE01ErlXakPihhKDA", // 1theK Originals - 원더케이 오리지널
            "UCOB_HGqE2t0XcACau0JxGdg", // 710house
            "UCHYMuI_gpM-iA3RfgGZ28yw", // 갤럭시키즈(galaxykids)
            "UCSSkHIU1-nL_FeCjeZ_Xtvg", // 노빠꾸탁재훈
            "UCpBlDSUyOpP1WqWVJxZpGLg", // 멜론 Melon
            "UCZFglJIUsaCPRE_3yzr8zzg", // 볼빨간사춘기 BOL4
            "UCuz_Y5rkcRd6kTGqLO0VgxA", // 스튜디오 잼플릭스 - STUDIO JAMFLIX
            "UCah-d4pYWd5UT6xFbUZgFtg", // 스튜디오 훜 : STUDIO HOOK
            "UCiwQRG2sCcfjKkgxMEdJGPg", // 엠뚜루마뚜루 : MBC 공식 종합 채널
            "UC1Rz7a_DWtsE9cQwSyElE0Q", // 인생84
            "UCG1yCJ0i5iAH1lixElahYUw", // 코빨간배춘기 [배성재의 텐]
            "UC1rQlcqyynQ-_XLRZSAaREQ", // 플레이다 Playda
            "UCwoSFXX8kraikB-WClx2pVQ", // AHOF
            "UCyc9sUCxELTDK9vELO5Fzeg", // Billlie
            "UChhKBlh_wvspTh5n4mL0b5g", // BOYNEXTDOOR
            "UCJEER74X9kBenMT_x9iK9Mw", // FIFTY FIFTY Official
            "UCXbRURMKT3H_w8dT-DWLIxA", // fromis_9
            "UCwOSPKpkwK4ZN0NJZ7rWpIQ", // Geenius
            "UC0novDS2VRX59Trmbf83I3g", // H1-KEY
            "UCgiJf4Qcigccr9lAXAA9hFQ", // HITGS (힛지스)
            "UCritGVo7pLJLUS8wEu32vow", // i-dle (아이들)
            "UCEpFoWeCMCo5z3EvWaz6hQQ", // ILLIT
            "UCMmcz1ybSoAD-GUlDx_7H2A", // IRISÉ
            "UCF3UEVPt5zZTlBVJwDG317g", // JTBC Voyage
            "UCHzW_7Mf8G0JxP4b0LoAd7A", // K-Culture NOW by 연합뉴스
            "UC8whlOg70m2Yr3qSMjUhh0g", // Kep1er
            "UCmq9jjpHA69PwAwlA0DwOsg", // KickFlip
            "UCvEEeBssb4XxIfWWIB8IjMw", // KISS OF LIFE
            "UC8PPjyqCQJtNfEwHBcq_uxw", // LNGSHOT
            "UCe52oeb7Xv_KaJsEzcKXJJg", // MBCkpop
            "UCKNZsAeQXpvI-Mpoc0ZKhsA", // Mhz 므흐즈: MBC RADIO
            "UCtfyAiqf095_0_ux8ruwGfA", // MUSINSA TV
            "UCX9VHpN62jkqCCntHxymPOA", // NewShowBiz完全娛樂
            "UCRZVxIpIbf6iLTU0e5MLyHA", // Nick Kosir
            "UChXC6ok0LaZIpf-50SIFf0Q", // Official ARTMS
            "UCRmm8763aqO0CoeLCA2jNSA", // ootb STUDIO
            "UCdVD0MsYecQaIE5Ru-pOIQQ", // RIIZE
            "UCcTq_itv43G05dGVydRTJew", // ROTHY - 로시
            "UC-we351yXoKcODFHiKrC84Q", // SAY MY NAME
            "UCfkXDY7vwkcJ8ddFGz8KusA", // SEVENTEEN
            "UCEIi7zFR_wE23jFncVtd6-A", // STUDIO CHOOM [스튜디오 춤]
            "UC7eHynJ83ypIf_586YR5lqQ", // SWING ENTERTAINMENT
            "UCkJ1rbOrsyPfBuHNfnLPm-Q", // THE BOYZ
            "UC7i6r7XiJO5i9H_rgIeZTSA", // THE FACT MUSIC AWARDS (TMA)
            "UC8C6QOPDVYwmuaSBZksefdg", // TWS
            "UCHWhn9xw7x8NPPnV6eWQDQA", // XLOV
            "UCkrDEGNM3mqZXHk2MfnOjMw", // YENA(최예나)
            "UCSAp0Yl9S0Zq5uDqE6im_XQ" // ZEROBASEONE
    );

    private static final Set<String> OFFICIAL_COLLABORATION_CHANNELS = Set.of(
            "UCHD1jo5RhijLfx5-0Ehe_cg", // &TEAM
            "UCZFglJIUsaCPRE_3yzr8zzg", // 볼빨간사춘기 BOL4
            "UCwoSFXX8kraikB-WClx2pVQ", // AHOF
            "UCyc9sUCxELTDK9vELO5Fzeg", // Billlie
            "UChhKBlh_wvspTh5n4mL0b5g", // BOYNEXTDOOR
            "UCJEER74X9kBenMT_x9iK9Mw", // FIFTY FIFTY Official
            "UCXbRURMKT3H_w8dT-DWLIxA", // fromis_9
            "UCwOSPKpkwK4ZN0NJZ7rWpIQ", // Geenius
            "UC0novDS2VRX59Trmbf83I3g", // H1-KEY
            "UCgiJf4Qcigccr9lAXAA9hFQ", // HITGS (힛지스)
            "UCritGVo7pLJLUS8wEu32vow", // i-dle (아이들)
            "UCEpFoWeCMCo5z3EvWaz6hQQ", // ILLIT
            "UCMmcz1ybSoAD-GUlDx_7H2A", // IRISÉ
            "UC8whlOg70m2Yr3qSMjUhh0g", // Kep1er
            "UCmq9jjpHA69PwAwlA0DwOsg", // KickFlip
            "UCvEEeBssb4XxIfWWIB8IjMw", // KISS OF LIFE
            "UC8PPjyqCQJtNfEwHBcq_uxw", // LNGSHOT
            "UChXC6ok0LaZIpf-50SIFf0Q", // Official ARTMS
            "UCdVD0MsYecQaIE5Ru-pOIQQ", // RIIZE
            "UCcTq_itv43G05dGVydRTJew", // ROTHY - 로시
            "UC-we351yXoKcODFHiKrC84Q", // SAY MY NAME
            "UCfkXDY7vwkcJ8ddFGz8KusA", // SEVENTEEN
            "UCkJ1rbOrsyPfBuHNfnLPm-Q", // THE BOYZ
            "UC8C6QOPDVYwmuaSBZksefdg", // TWS
            "UCHWhn9xw7x8NPPnV6eWQDQA", // XLOV
            "UCkrDEGNM3mqZXHk2MfnOjMw", // YENA(최예나)
            "UCSAp0Yl9S0Zq5uDqE6im_XQ", // ZEROBASEONE
            "UCQn1FqrR2OCjSe6Nl4GlVHw", // Raon
            "UC9s_jNCjHuRoY-1euceHLFQ", // 키코(Kik5o)
            "UCIn1p2ZbhOo8fyZp1S60uGg", // 백지영 Baek Z Young
            "UCFrAB5I66LgzOi15TF4PO6A", // 문별이다 moonbyul2da
            "UC1RjCqJ8nlQq-kx7mXYhPWg", // 김재환 KIM JAE HWAN
            "UCQ6Br7m6vP61FZvjv4lwR5w", // 私立恵比寿中学
            "UCik5vfvdgiicyXJ9O1PYnmQ", // 소란 / SORAN
            "UC8whmo5_lTEbugyP6JHG_wA", // 이젤 EJel
            "UCKx8jjUaHduLcyPAQVsHMpg", // 조현아의 평범한 목요일 밤
            "UCMKXs7_6MhbceHRPbPjuMzg", // 프림로즈 PRIMROSE
            "UCC4wzWbk_zdQNZstCwcfFLA", // ALPHA DRIVE ONE
            "UCoe0BXYv01VCt0JRhX1zSug", // AND2BLE
            "UCvhVqA_5HnM7M5X6OcZlMbg", // CHUU Official
            "UCPo532dDHpbKVhJyJseLVbw", // CLOSE YOUR EYES
            "UCEz-AFAg3EUKsxraad1puQA", // CUTIE STREET
            "UCdKe_awfdHMV3O13pmA2heQ", // DKZ
            "UCijULR2sXLutCRBtW3_WEfA", // Dreamcatcher official
            "UCKHl-9B-aa4AK5ZR9XLOg8A", // EPEX
            "UCfP7KJRAHghtG_Sn0aLi_DQ", // HEART OF WOMAN
            "UC4Jxwum4U7lwpgdyPX0NvvA", // IDID
            "UCnQRdSArF-aqtycrmMKFQAg", // ifeye 이프아이
            "UCDhM2k2Cua-JdobAh5moMFg", // ITZY
            "UCfbYNlxgLuKJXQZEhkMmaCQ", // izna (이즈나)
            "UCaHwGJXFb28TekZMf7iFg5Q", // KiiiKiii
            "UCeQlMQ-4cedE7SNmF5JsdsA", // LIGHTSUM 라잇썸 (Official YouTube Channel)
            "UCOOO_pMsNM_Q7dd0LG26-Tw", // MODYSSEY
            "UC-qYkzKFdekoEniRu_FS3zg", // OH MY GIRL
            "UCWTYf4xModfNsyq2NUYjF5g", // ONEUS
            "UCe-XzQhLaq4zS_mPXzuc_2Q", // P1Harmony
            "UCor8nQnEdMs4eBcU-uVBQ8g", // PURPLE KISS
            "UCdgVLvuLUIpokhrbpd5GdUQ", // Queenz Eye
            "UCgD0APk2x9uBlLM0UsmhQjw", // QWER
            "UCk9GmdlDTBfgGRb7vXeRMoQ", // Red Velvet
            "UCDbLcCDRjmKrNtuq6O62lng", // Rocket Punch - 로켓펀치
            "UCg-IjXpsbCaEUcGbFM-XcKw", // Samuel Official
            "UCod5V2dpnpJLklGvVOv5FcQ", // STAYC
            "UCvc55IOOCI165H3raqcjjrg", // TRENDZ(트렌드지)
            "UC-ZHt5Zgadfx-B1CM63Lqew", // WayV
            "UC5rp2rDRSpyKNj-QLytC-tw", // Weeekly 위클리
            "UCNeLbWtlJzTsavMC70zY0dQ", // WOOAH 우아
            "UCTmx5ntaaBTrzThDw_exaMg", // X:IN
            "UCPtC0MXSW40Qq7RkhsCXjUQ", // xikers
            "UC38ULbpGgiA7t6i-lbLu9VQ" // きゅるりんってしてみて
    );

    public static Set<String> officialCollaborationChannels() {
        return OFFICIAL_COLLABORATION_CHANNELS;
    }

    public static boolean hasTripleSGroupHashtag(String title, String description) {
        Set<String> hashtags = exactHashtags(title, description);
        return hashtags.contains("triples") || hashtags.contains("트리플에스");
    }

    private static final Set<String> REVIEWED_FAN_CHANNELS = Set.of(
            "UCkECc47iN5okSKWtJkPeHmA", // 30초 인물소개
            "UCNudC2C7RuymoUJ37fE-pag", // 걸그룹갤러리
            "UC6WktRdWK3ui4ZZEsmRtajw", // 고음충전k
            "UCoFhwPtrmxmhUhFaqLJZ90w", // 그냥취미활동
            "UC84G3UgA0hr84VJ_T_f1vrA", // 나 잘한다해짜나
            "UCohUYoSIu_96MyZfuAPaL9w", // 네가
            "UCX5EYpJt-cJWTpU2yXbDdAA", // 도리
            "UCGDVtJReiiTaff_V3tZyRNw", // 매혹의 향수
            "UCf5WKpIBAdxHYIlwzIKWvUw", // 무슨 말이 필요해
            "UCW-yKB8JwCUyWnQZt8Lvdyg", // 비어굿맨
            "UCA78q21_ylajHgElLPg44MA", // 쇼츠공장
            "UC4At8cQcBX2rnej3nex4n2A", // 숏나웃김
            "UCuqFFZafd0f3PdOmL7_zFXw", // 슬랴+
            "UCPCcfrlZBvtas5rQLlHEx0Q", // 아이소
            "UCV3lN32Z96I3tE4CaSf_BqA", // 아쮸
            "UCMR0G7zXSO7fcYLlvDexTOw", // 연예뉴스찾아요
            "UCKai3URoZ3kKGXSLGKHWIfA", // 예능 뭐볼래
            "UCZhz9XV3FoGP3zocrWM8fqw", // 예능판다
            "UCpBSZ4PMmfdN4foAjW9g0xQ", // 잠깐만아이돌
            "UCfs9UQJvLXRfUCGKOAuWozg", // 찾았다 내 엔돌핀
            "UCmcQQ5a28v9MdNGVZFebsHQ", // 케이팝 읽어주는 남녀
            "UC5EvMz_XliMULpqQHkjnzgQ", // 해달특파원
            "UCHztIyDNlLlZ6JA85a6BdCw", // Chaereve
            "UCPhSAx1cU7-8ifr3b8o8M5A", // Chef Moka 👩‍🍳
            "UCrTCkJ8377E7JhsMeyLXtKA", // DaftTaengk
            "UCb8iIFUB-QH7WQBVkQQYQBw", // Dew Asia
            "UCn21qwlzBQp2EcuHscxouWQ", // Greeming *
            "UCuiurKTtUHfu_VWbMBq4k9g", // Heavenlys Dolls
            "UC70Hwe3nc_0I09_hgrYJsSg", // K-POP 날것이 좋아
            "UCNalkIvfx6Rls4IKd3qy_Sg", // K-POP ZONE
            "UCHTPX4k1blzI8FPN7j6jjSQ", // kpop up on
            "UCpqM6amdEngQaQbc8954Jbg" // MangoNim
    );

    private static final Pattern PRODUCER_CREDIT = Pattern.compile(
            "(?iu)공식\\s*(?:유튜브|채널|계정)|official\\s+(?:youtube\\s+)?channel|기획\\s*[·・/&]\\s*제작\\s*©");

    private MediaHashtags() {}

    public static List<String> tripleSHashtagQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("#트리플에스|#tripleS");
        for (String name : MemberPortraitOptions.koreanOrder()) {
            String query = MemberPortraitOptions.memberAliases(name).stream()
                    .filter(alias -> alias.matches("[가-힣]+"))
                    .map(alias -> "#" + alias)
                    .collect(java.util.stream.Collectors.joining("|"));
            queries.add(query);
        }
        return queries;
    }

    public static List<String> taggedTripleSMembers(String title, String description) {
        Set<String> names = new LinkedHashSet<>();
        for (String tag : exactHashtags(title, description)) {
            int index = MemberPortraitOptions.memberIndex(tag);
            if (index < 24 && !tag.matches("s[0-9]+")) names.add(MemberPortraitOptions.koreanOrder().get(index));
        }
        return List.copyOf(names);
    }

    public static boolean hasTripleSDiscoveryTag(String title, String description, String channelTitle, String channelDescription) {
        Set<String> hashtags = exactHashtags(title, description);
        if (hashtags.contains("triples") || hashtags.contains("트리플에스")) return true;
        List<String> members = taggedTripleSMembers(title, description);
        if (members.isEmpty()) return false;
        return mentionsTripleS(safe(title), safe(description)) || mentionsTripleS(safe(channelTitle), safe(channelDescription))
                || members.stream().anyMatch(name -> name.length() >= 3 && safe(channelTitle).contains(name));
    }

    public static boolean hasDiscoveryHashtag(String title, String description) {
        Set<String> hashtags = exactHashtags(title, description);
        return hashtags.contains("triples") || hashtags.contains("트리플에스") || !taggedTripleSMembers(title, description).isEmpty();
    }

    public static boolean reviewedFanChannel(String channelId) {
        return channelId != null && REVIEWED_FAN_CHANNELS.contains(channelId);
    }

    public static boolean hasPublisherEvidence(String channelId, String channelDescription, String videoDescription) {
        return channelId != null && (REVIEWED_ORIGINAL_CHANNELS.contains(channelId)
                || PRODUCER_CREDIT.matcher(safe(channelDescription) + " " + safe(videoDescription)).find());
    }

    public static boolean looksFanUpload(String title, String description, String channelTitle, String channelDescription) {
        return looksFanMade(title, null) || looksFanMade(channelTitle, channelDescription)
                || FAN_UPLOAD.matcher(safe(title) + " " + safe(channelTitle)).find()
                || FAN_UPLOAD.matcher(safe(channelDescription)).find()
                || exactHashtags(description, null).stream()
                    .anyMatch(tag -> tag.matches("fan_?(?:made|edit)|팬메이드|팬에디트|unofficial"));
    }

    private static Set<String> exactHashtags(String title, String description) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(safe(title) + " " + safe(description));
        while (matcher.find()) result.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC);
    }

    public static boolean mentionsTripleS(String title, String description) {
        return mentions(title) || mentions(description);
    }

    public static boolean mentions(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.contains("트리플에스")) return true;
        String compact = text.toUpperCase(Locale.ROOT).replace(" ", "");
        if (compact.contains("#TRIPLES") || compact.contains("@TRIPLES")) return true;
        return TRIPLES_WORD.matcher(text.toUpperCase(Locale.ROOT)).find();
    }

    public static boolean mentionsGroupOutsideTags(String title) {
        return title != null && mentions(TAG.matcher(title).replaceAll(" "));
    }

    public static boolean looksFanMade(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        String compact = text.replace(" ", "").replace("-", "");
        return text.contains("팬메이드")
                || compact.contains("fanmade")
                || text.contains("팬에디트")
                || compact.contains("fanedit")
                || compact.contains("unofficial")
                || text.contains("비공식");
    }

    /**
     * 지정 방송국·official이 아닌 업로더. 탭과 무관하게 제외한다.
     */
    public static boolean unknownUploader(
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (isAllowedChannel(channelId, channelTitle, allowedChannelIds, allowedHandles)) return false;
        return !channelNameEndsWithOfficial(channelTitle);
    }

    public static boolean looksOffChannelBroadcast(
            String title,
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        return unknownUploader(channelId, channelTitle, allowedChannelIds, allowedHandles);
    }

    /**
     * 다른 K-pop 가수가 제목의 주인공이거나, 트리플에스 언급 없이 다른 가수 콘텐츠·해시태그만 겹치는 경우.
     */
    public static boolean overlapsOtherKpopAct(String title, String description) {
        boolean titleOther = mentionsOtherKpopAct(title);
        if (titleOther && !mentions(title)) return true;
        if (!mentionsOtherKpopAct(title) && !mentionsOtherKpopAct(description)) return false;
        return !mentionsTripleS(title, description);
    }

    static boolean mentionsOtherKpopAct(String text) {
        if (text == null || text.isBlank()) return false;
        return OTHER_KPOP.matcher(text).find();
    }

    /**
     * 제목·설명의 #/@에 트리플에스·멤버가 아닌 다른 가수명이 있다.
     * 음방 프로그램 태그는 무시한다.
     */
    public static boolean taggedOtherKpopAct(String title, String description, Collection<String> memberNames) {
        for (String tag : tags(title, description)) {
            if (isBroadcastTag(tag) || mentions(tag) || matchesMember(tag, memberNames)) continue;
            if (mentionsOtherKpopAct(tag)) return true;
        }
        return false;
    }

    public static boolean hasGroupOrMemberTag(String title, String description, Collection<String> memberNames) {
        for (String tag : tags(title, description)) {
            if (mentions(tag) || matchesMember(tag, memberNames)) return true;
        }
        return false;
    }

    /**
     * 다른 가수 Official 계정이 트리플에스 또는 멤버 해시태그를 단 쇼츠.
     */
    public static boolean otherArtistOfficialShorts(
            String title,
            String description,
            String channelTitle,
            Collection<String> memberNames
    ) {
        if (!channelNameEndsWithOfficial(channelTitle)) return false;
        if (channelTitle != null && channelTitle.trim().matches("(?i)triple\\s*s\\s*official")) return false;
        if (!mentionsOtherKpopAct(channelTitle)) return false;
        if (looksFanMade(title, description)) return false;
        return hasGroupOrMemberTag(title, description, memberNames);
    }

    static List<String> tags(String title, String description) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectTags(values, title);
        collectTags(values, description);
        return new ArrayList<>(values);
    }

    private static void collectTags(Set<String> values, String text) {
        if (text == null || text.isBlank()) return;
        Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (tag != null && !tag.isBlank()) values.add(tag);
        }
    }

    private static boolean isBroadcastTag(String tag) {
        if (tag == null || tag.isBlank()) return false;
        String compact = compact(tag);
        return compact.contains("MCOUNTDOWN")
                || compact.contains("MUSICBANK")
                || compact.contains("INKIGAYO")
                || compact.contains("MUSICCORE")
                || compact.contains("SHOWCHAMPION")
                || compact.contains("THESHOW")
                || compact.contains("SIMPLYKPOP")
                || compact.contains("KPOP")
                || compact.contains("SHORTS")
                || compact.contains("YOUTUBE")
                || tag.contains("엠카운트다운")
                || tag.contains("뮤직뱅크")
                || tag.contains("인기가요")
                || tag.contains("음악중심")
                || tag.contains("쇼챔피언")
                || tag.contains("더쇼");
    }

    private static boolean matchesMember(String tag, Collection<String> memberNames) {
        if (tag == null || tag.isBlank() || memberNames == null) return false;
        String compactTag = compact(tag);
        String folded = tag.toLowerCase(Locale.ROOT);
        for (String name : memberNames) {
            if (name == null || name.isBlank()) continue;
            String needle = name.trim();
            if (needle.length() < 2) continue;
            if (folded.contains(needle.toLowerCase(Locale.ROOT))) return true;
            String compactName = compact(needle);
            if (compactName.length() >= 2 && compactTag.contains(compactName)) return true;
        }
        return false;
    }

    public static boolean channelNameEndsWithOfficial(String channelTitle) {
        if (channelTitle == null || channelTitle.isBlank()) return false;
        String trimmed = channelTitle.trim();
        return trimmed.matches("(?i).*official");
    }

    /**
     * 트리플에스를 언급하고, 지정 채널(1theK·SBS/MBC/KBS/Mnet·M2 등)·official 계정인 경우만 남긴다.
     * 아무 팬 계정의 음방 직캠이나 #tripleS 영상은 넣지 않는다.
     */
    public static boolean keepKnownSource(
            String title,
            String description,
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (!mentionsTripleS(title, description)) return false;
        if (looksFanMade(title, description)) return false;
        if (overlapsOtherKpopAct(title, description)) return false;
        if (isAllowedChannel(channelId, channelTitle, allowedChannelIds, allowedHandles)) return true;
        return channelNameEndsWithOfficial(channelTitle);
    }

    public static boolean isAllowedChannel(
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (channelId != null && !channelId.isBlank() && allowedChannelIds != null) {
            String id = channelId.trim();
            for (String allowed : allowedChannelIds) {
                if (allowed != null && allowed.equalsIgnoreCase(id)) return true;
            }
        }
        if (channelTitle == null || channelTitle.isBlank() || allowedHandles == null) return false;
        String titleKey = compact(channelTitle);
        if (titleKey.length() < 4) return false;
        for (String handle : allowedHandles) {
            String key = compact(handle);
            if (key.length() < 4) continue;
            if (titleKey.contains(key) || key.contains(titleKey)) return true;
        }
        return false;
    }

    static String compact(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
