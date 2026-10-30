package com.example.triplesweb.navigation;

import java.util.List;

/** 헤더와 전체 메뉴가 함께 사용하는 정적 메뉴. 요청마다 DB를 조회하지 않는다. */
public final class SiteNavigation {
    private SiteNavigation() {}

    public record Link(String label, String href) {}
    public record Item(String id, String label, String href, List<Link> children, List<Link> sitemapOnly) {
        public Item(String id, String label, String href, List<Link> children) { this(id, label, href, children, List.of()); }
        public boolean isNested() { return !children.isEmpty(); }
        public boolean isLinked() { return !href.isBlank(); }
    }
    public record Section(String id, String label, List<Item> items, List<Link> more, String href) {
        public Section(String id, String label, List<Item> items, List<Link> more) { this(id, label, items, more, ""); }
        public boolean isDirect() { return !href.isEmpty(); }
        public boolean isHasMore() { return !more.isEmpty(); }
    }

    private static Item link(String label, String href) {
        return new Item("", label, href, List.of());
    }

    public static final List<Section> PRIMARY = List.of(
        new Section("about", "About 트리플에스", List.of(
            link("그룹 입문", "/triples/overview"), link("멤버 소개", "/triples/members"),
            link("발매 앨범", "/triples/album"), link("역대 유닛", "/triples/unit"),
            link("케미씃트리", "/triples/chemistry")
        ), List.of()),
        new Section("cosmo", "코스모", List.of(
            new Item("objekt", "오브젝트", "/cosmo/objekt", List.of(),
                List.of(new Link("찜한 오브젝트", "/cosmo/objekt/favorites"), new Link("전송 내역", "/cosmo/transfers"))),
            link("내 오브젝트 검색", "/cosmo/collection"),
            new Item("market", "교환/거래/분철", "", List.of(new Link("교환", "/article/trade"),
                new Link("거래", "/article/sell"), new Link("분철", "/article/group-orders"))),
            link("그래비티", "/cosmo/gravity"), link("코스모 업데이트 현황", "/cosmo/update")
        ), List.of()),
        new Section("info", "정보", List.of(
            link("역대 이벤트 및 행사", "/cosmo/event"), link("굿즈리스트", "/shop"),
            link("투표/노동/총공", "/activities/voting"), link("기사/그외 정보", "/article/info")
        ), List.of()),
        new Section("media", "미디어", List.of(
            link("사진 아카이브", "/archive/image_archive"), link("영상 아카이브", "/archive/video_archive"),
            new Item("", "유튜브 및 공식 미디어", "/media", List.of(),
                List.of(new Link("트리플에스 드라이브", "/media/drive")))
        ), List.of()),
        new Section("schedule", "일정", List.of(), List.of(), "/schedule")
    );

    public static final Section COMMUNITY = new Section("community", "커뮤니티", List.of(
        link("공지사항", "/article/notice"), link("건의게시판 (비밀글)", "/community/suggestions")
    ), List.of(new Link("자유게시판", "/article/free"), new Link("채팅게시판", "/article/chat"),
        new Link("인기글", "/popular")));
}
