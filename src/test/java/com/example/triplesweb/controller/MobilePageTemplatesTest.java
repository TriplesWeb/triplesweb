package com.example.triplesweb.controller;

import com.samskivert.mustache.Mustache;
import com.example.triplesweb.navigation.SiteNavigation;
import com.example.triplesweb.service.StaticAssetVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Render every public page family with deterministic, deliberately long content. */
class MobilePageTemplatesTest {
    @Test void sharedMobileAssetsReachAllPublicTemplatesAndRenderFixtures() throws Exception {
        Path root=Path.of("src/main/resources/templates"), output=Path.of("build/mobile-pages");
        Files.createDirectories(output);
        var compiler=Mustache.compiler().defaultValue("").withLoader(name -> new InputStreamReader(
                new ClassPathResource("templates/"+name+".mustache").getInputStream(), StandardCharsets.UTF_8));
        String longText="모바일에서도 읽을 수 있어야 하는 아주 긴 제목과 계정 이름 및 오브젝트 정보";
        var post=Map.<String,Object>of("id",1,"displayId",1,"title",longText,"userId",longText,"formattedDate","2026-10-27","likesCount",10,"category","trade");
        String rich="<p>"+"https://example.com/"+"longpath".repeat(30)+"</p><table style='width:1200px'><tr><td>가로로 긴 표</td></tr></table>";
        Map<String,Object> model=new HashMap<>();
        StaticAssetVersionService assets = new StaticAssetVersionService();
        model.put("asset", (Mustache.Lambda) (frag, out) -> out.write(assets.versionedUrl(frag.execute())));
        model.put("siteNavigation", SiteNavigation.PRIMARY);
        model.putAll(post); model.put("categoryName","교환"); model.put("member",false);
        model.put("articleList",List.of(post)); model.put("popularArticles",List.of(post)); model.put("article",Map.of("id",1,"title",longText,"content",rich,"userId",longText));
        model.put("item",Map.of("id",1,"name",longText,"price",1000,"description",rich));
        model.put("items",List.of(Map.of("id",1,"name",longText,"price",1000,"imageUrl","/images/no-image.png")));
        model.put("articleForm",Map.of("title",longText,"content",rich));
        model.put("objekt",Map.of("frontImage","","backImage","","thumbnailImage","","collectionId",longText,"member","HaYeon","season","Cream02","class","Special","collectionNo","367Z"));
        model.put("seasonOptions",List.of("Atom01","Binary01","Cream02"));
        model.put("seasonChoices",List.of(Map.of("value","Atom01","label","Atom01"),Map.of("value","Binary01","label","Binary01"),Map.of("value","Cream02","label","Cream02")));
        model.put("objektSeasonOptions",List.of("Atom01","Binary01","Cream02"));
        // 시즌 뱃지 브라우저 픽스처: Cream02 카드가 렌더되어야 objekt-season-schedule 검사가 통과한다.
        Map<String,Object> creamObjekt=new LinkedHashMap<>();
        creamObjekt.put("publicKey","hayeoncc367z");
        creamObjekt.put("seasonKey","Cream02");
        creamObjekt.put("classKey","special");
        creamObjekt.put("artist","triples");
        creamObjekt.put("collectionNo","367Z");
        creamObjekt.put("numberValue",367);
        creamObjekt.put("createdAt","2026-01-01");
        creamObjekt.put("member","HaYeon");
        creamObjekt.put("searchText","Cream02 HaYeon 367Z");
        creamObjekt.put("collectionId","Cream02 HaYeon 367Z");
        creamObjekt.put("thumbnailImage","/images/no-image.png");
        creamObjekt.put("frontImage","/images/no-image.png");
        creamObjekt.put("class","Special");
        creamObjekt.put("season","Cream02");
        creamObjekt.put("seasonColor","#FF8389");
        creamObjekt.put("memberHref","/cosmo/objekt/member?name=HaYeon");
        creamObjekt.put("mintCountKnown",false);
        model.put("objekts",List.of(creamObjekt));
        creamObjekt.put("cardTitle","HaYeon 367Z");
        model.put("cards",List.of(Map.ofEntries(
                Map.entry("serial","27"), Map.entry("hasSerial",true),
                Map.entry("collectionId","Cream02 HaYeon 367Z"), Map.entry("cardTitle","HaYeon 367Z"),
                Map.entry("member","HaYeon"), Map.entry("season","Cream02"), Map.entry("seasonColor","#FF8389"),
                Map.entry("objektClass","Double"), Map.entry("collectionNo","367Z"),
                Map.entry("hasImage",true), Map.entry("thumbnailImage","/images/no-image.png"),
                Map.entry("hasDetail",false))));
        model.put("hasCards",true);
        model.put("showObjektPicker",true);
        model.put("objektKey","uuid"); model.put("memberUrl","/cosmo/objekt?member=HaYeon");model.put("lookupUrl","/lookup");model.put("favoriteDesired",true);
        model.put("isOverviewTab",true);model.put("hasProfile",true);model.put("hasCollection",true);model.put("hasOverallProgress",true);model.put("hasStats",true);
        model.put("nickname","MobileFan");model.put("requestedNickname","MobileFan");model.put("ownedCount",3);model.put("uniqueCount",2);
        model.put("overallOwned",4);model.put("overallMissing",6);model.put("overallTotal",10);model.put("overallPercent",40);
        model.put("overviewUrl","/cosmo/profile/MobileFan?tab=overview");model.put("membersTabUrl","/cosmo/profile/MobileFan?tab=members");model.put("transfersUrl","/cosmo/transfers?nickname=MobileFan");
        model.put("seasonStats",List.of(orbit("Cream02",50,1,2),orbit("Binary01",0,0,2)));
        model.put("classStats",List.of(orbit("Special",50,1,2),orbit("First",100,2,2)));
        int count=0;
        try(var files=Files.walk(root)) {
            for(Path path:files.filter(p->p.toString().endsWith(".mustache")).toList()) {
                String source=Files.readString(path);
                if(!source.contains("<html") && !source.contains("{{>layout/head-open}}"))continue;
                assertThat(source).containsAnyOf(
                        "{{>layout/head-open}}","{{>layout/favicon}}","{{>layout/triples-head}}","{{>layout/site-head}}");
                Map<String,Object> pageModel=model;
                if("main.mustache".equals(root.relativize(path).toString().replace('\\','/'))){
                    pageModel=new HashMap<>(model);
                    pageModel.put("isHomePage", true);
                }
                String result=compiler.compile(source).execute(pageModel);
                assertThat(result).contains("/css/site-mobile.css", "name=\"viewport\"");
                String name=root.relativize(path).toString().replace('/', '_').replace('\\', '_');
                Files.writeString(output.resolve(name+".html"),result);count++;
            }
        }
        for (String category : List.of("trade", "sell", "group-orders")) {
            var board = new HashMap<>(model);
            board.put("category", category); board.put("hideRecommendations", true);
            board.put("isMarketBoard", !"group-orders".equals(category));
            board.put("isTrade", "trade".equals(category));
            board.put("isSell", "sell".equals(category));
            board.put("categoryName", Map.of("trade","교환","sell","거래","group-orders","분철").get(category));
            var row = new HashMap<>(post);
            row.put("hasMemberSwap", true); row.put("hasHaveLead", true); row.put("haveLeadName", "서연");
            row.put("haveLeadColor", "#8decd9"); row.put("showTradeSwap", "trade".equals(category));
            row.put("hasWantLead", "trade".equals(category)); row.put("wantLeadName", "코토네");
            row.put("wantLeadColor", "#ff8389");
            board.put("articleList", List.of(row));
            Files.writeString(output.resolve("board-" + category + ".html"),
                compiler.compile(Files.readString(root.resolve("article/article.mustache"))).execute(board));
        }
        assertThat(count).isGreaterThanOrEqualTo(30);
        var auth=new HashMap<>(model);auth.put("member",Map.of("userId",longText,"level",1,"points",100));
        String header=compiler.compile(Files.readString(root.resolve("layout/header.mustache"))).execute(auth);
        assertThat(header).doesNotContain("header-objekt-jump");
        var homeAuth=new HashMap<>(auth);homeAuth.put("isHomePage", true);
        String homeHeader=compiler.compile(Files.readString(root.resolve("layout/header.mustache"))).execute(homeAuth);
        assertThat(homeHeader).contains("header-objekt-jump").contains("href=\"/cosmo/objekt\"");
        Files.writeString(output.resolve("header-auth.html"),"<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><link rel='stylesheet' href='/css/gravity.css'><link rel='stylesheet' href='/css/site-mobile.css'><link rel='stylesheet' href='/css/site-header.css'></head><body>"+header+"<main><h1>모바일 메뉴</h1><input aria-label='검색' placeholder='검색'></main></body></html>");
        Files.writeString(output.resolve("header-bare.html"),"<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><link rel='stylesheet' href='/css/site-mobile.css'><link rel='stylesheet' href='/css/site-header.css'></head><body>"+header+"<main><h1>스타일 체계가 다른 화면의 메뉴</h1><input aria-label='검색' placeholder='검색'></main></body></html>");
        Files.writeString(output.resolve("header-home.html"),"<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><link rel='stylesheet' href='/css/gravity.css'><link rel='stylesheet' href='/css/site-mobile.css'><link rel='stylesheet' href='/css/site-header.css'></head><body class='main-page'>"+homeHeader+"<main><h1>홈</h1><input aria-label='검색' placeholder='검색'></main></body></html>");
        assertThat(header).contains("data-cosmo-id-open", "data-alerts-open", "사이트 전체 검색");
        var searchPage = new HashMap<>(model);
        searchPage.put("keyword", "323");
        searchPage.put("hasAnyResults", true);
        searchPage.put("hasMembers", true);
        searchPage.put("activeTab", "cosmo");
        searchPage.put("memberResults", List.of(Map.of(
                "name", "지연",
                "engName", "JiYeon",
                "profileImage", "/images/no-image.png",
                "profileUrl", "/triples/artist?tab=member-24")));
        Map<String,Object> aboutTab = new LinkedHashMap<>();
        aboutTab.put("id", "about"); aboutTab.put("label", "About 트리플에스");
        aboutTab.put("href", "/search?keyword=323&tab=about"); aboutTab.put("count", 1);
        aboutTab.put("hasHits", true); aboutTab.put("active", false); aboutTab.put("hasMore", true);
        aboutTab.put("moreHref", "/triples/members");
        aboutTab.put("hits", List.of(Map.of("label","서연","href","/triples/artist?tab=member-1","kind","멤버","snippet","SeoYeon")));
        Map<String,Object> cosmoTab = new LinkedHashMap<>();
        cosmoTab.put("id", "cosmo"); cosmoTab.put("label", "코스모");
        cosmoTab.put("href", "/search?keyword=323&tab=cosmo"); cosmoTab.put("count", 1);
        cosmoTab.put("hasHits", true); cosmoTab.put("active", true); cosmoTab.put("hasMore", true);
        cosmoTab.put("moreHref", "/cosmo/objekt?search=323");
        cosmoTab.put("hits", List.of(Map.of("label","HaYeon 323","href","/cosmo/objekt/detail/hayeoncc323","kind","오브젝트","snippet","Cream02")));
        searchPage.put("tabs", List.of(aboutTab, cosmoTab));
        Files.writeString(output.resolve("search-tabs.html"), compiler.compile(Files.readString(root.resolve("search.mustache"))).execute(searchPage));
    }
    private static Map<String,Object> orbit(String name,int percent,int owned,int total) {
        return Map.of("member",name,"label",name,"percent",percent,"owned",owned,"total",total,"copies",owned,"hasTotal",true,"color","#8decd9","progressUrl","/cosmo/profile/MobileFan?tab=members","isEmpty",owned==0);
    }
}
