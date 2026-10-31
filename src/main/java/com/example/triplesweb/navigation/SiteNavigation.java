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
            link("그룹 입문", "/triples/overview"), link("멤버", "/triples/members"),
            link("앨범", "/triples/album"), link("유닛", "/triples/unit"),
            link("케미씃트리", "/triples/chemistry")
        ), List.of()),
        new Section("info", "정보", List.of(
            link("공연/이벤트", "/cosmo/event"), link("굿즈리스트", "/shop"),
            link("총공", "/activities/voting"), link("기사/그외", "/article/info")
        ), List.of()),
        new Section("cosmo", "코스모", List.of(
            link("오브젝트", "/cosmo/objekt"), link("내 컬렉션 검색", "/cosmo/collection"),
            link("교환/거래", "/article/trade"), link("그래비티", "/cosmo/gravity")
        ), List.of()),
        new Section("media", "미디어", List.of(
            link("사진", "/archive/image_archive"), link("영상", "/archive/video_archive"),
            link("공식 미디어", "/media")
        ), List.of()),
        new Section("schedule", "일정", List.of(), List.of(), "/schedule")
    );
}
