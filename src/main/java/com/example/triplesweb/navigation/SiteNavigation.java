package com.example.triplesweb.navigation;

import java.util.List;

/** 헤더와 전체 메뉴가 함께 사용하는 정적 메뉴. 요청마다 DB를 조회하지 않는다. */
public final class SiteNavigation {
    private SiteNavigation() {}

    public record Link(String label, String href) {}
    public record Item(String id, String label, String href, List<Link> children) {
        public boolean isNested() { return !children.isEmpty(); }
    }
    public record Section(String id, String label, List<Item> items, List<Link> more) {
        public boolean isHasMore() { return !more.isEmpty(); }
    }

    private static Item link(String label, String href) {
        return new Item("", label, href, List.of());
    }

    public static final List<Section> PRIMARY = List.of(
        new Section("about", "About 트리플에스", List.of(
            link("개요", "/triples/overview"), link("멤버", "/triples/members"),
            link("앨범", "/triples/album"), link("유닛", "/triples/unit"),
            link("케미스토리", "/triples/chemistry")
        ), List.of(new Link("그룹·멤버 통합 소개", "/triples/artist"), new Link("케미스토리 (English)", "/triples/chemistry-en"))),
        new Section("activities", "스케줄·활동", List.of(
            link("일정", "/schedule"), link("투표·노동", "/activities/voting"),
            link("공연·이벤트", "/cosmo/event"), link("굿즈리스트", "/shop"),
            link("정보·기사", "/article/info")
        ), List.of()),
        new Section("cosmo", "코스모", List.of(
            link("오브젝트", "/cosmo/objekt"),
            new Item("market", "교환/거래/분철", "", List.of(new Link("교환", "/article/trade"),
                new Link("거래", "/article/sell"), new Link("분철", "/cosmo/group-orders"))),
            link("그래비티", "/cosmo/gravity"), link("코스모 업데이트 현황", "/cosmo/update")
        ), List.of(new Link("내 오브젝트 검색", "/cosmo/collection"), new Link("전송 내역", "/cosmo/transfers"),
            new Link("찜한 오브젝트", "/cosmo/objekt/favorites"))),
        new Section("media", "미디어", List.of(
            link("사진 아카이브", "/archive/image_archive"), link("영상 아카이브", "/archive/video_archive"),
            link("유튜브 및 공식 미디어", "/media"), link("트리플에스 드라이브", "/media/drive")
        ), List.of(new Link("소셜 미디어", "/sns")))
    );

    public static final Section COMMUNITY = new Section("community", "커뮤니티", List.of(
        link("공지사항", "/article/notice"), link("건의게시판 (비밀글)", "/community/suggestions")
    ), List.of(new Link("자유게시판", "/article/free"), new Link("채팅게시판", "/article/chat"),
        new Link("인기글", "/popular")));
}
