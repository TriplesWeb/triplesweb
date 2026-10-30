package com.example.triplesweb.controller;

import com.samskivert.mustache.Mustache;
import com.example.triplesweb.navigation.SiteNavigation;
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
        model.put("siteNavigation", SiteNavigation.PRIMARY); model.put("siteCommunity", SiteNavigation.COMMUNITY);
        model.putAll(post); model.put("categoryName","교환"); model.put("member",false);
        model.put("articleList",List.of(post)); model.put("popularArticles",List.of(post)); model.put("article",Map.of("id",1,"title",longText,"content",rich,"userId",longText));
        model.put("item",Map.of("id",1,"name",longText,"price",1000,"description",rich));
        model.put("items",List.of(Map.of("id",1,"name",longText,"price",1000,"imageUrl","/images/no-image.png")));
        model.put("articleForm",Map.of("title",longText,"content",rich));
        model.put("objekt",Map.of("frontImage","","backImage","","thumbnailImage","","collectionId",longText,"member","HaYeon","season","Cream02","class","Special","collectionNo","367Z"));
        model.put("seasonOptions",List.of("Atom01","Binary01","Cream02"));
        model.put("seasonChoices",List.of(Map.of("value","Atom01","label","Atom01"),Map.of("value","Binary01","label","Binary01"),Map.of("value","Cream02","label","Cream02")));
        model.put("objektSeasonOptions",List.of("Atom01","Binary01","Cream02"));
        model.put("showObjektPicker",true);
        model.put("objektKey","uuid"); model.put("memberUrl","/cosmo/objekt?member=HaYeon");model.put("lookupUrl","/lookup");model.put("favoriteDesired",true);
        model.put("isOverviewTab",true);model.put("hasProfile",true);model.put("hasCollection",true);model.put("hasMemberProgress",true);model.put("hasStats",true);
        model.put("nickname","MobileFan");model.put("requestedNickname","MobileFan");model.put("ownedCount",3);model.put("uniqueCount",2);model.put("memberCount",2);
        model.put("overviewUrl","/cosmo/profile/MobileFan?tab=overview");model.put("membersTabUrl","/cosmo/profile/MobileFan?tab=members");model.put("transfersUrl","/cosmo/transfers?nickname=MobileFan");
        model.put("memberProgress",List.of(orbit("HaYeon",50,1,2),orbit("Jiyeon",0,0,2),orbit("SeoYeon",100,2,2)));
        model.put("seasonStats",List.of(orbit("Cream02",50,1,2),orbit("Binary01",0,0,2)));
        model.put("classStats",List.of(orbit("Special",50,1,2),orbit("First",100,2,2)));
        int count=0;
        try(var files=Files.walk(root)) {
            for(Path path:files.filter(p->p.toString().endsWith(".mustache")).toList()) {
                String source=Files.readString(path);
                if(!source.contains("<html"))continue;
                assertThat(source).containsAnyOf("{{>layout/favicon}}","{{>layout/triples-head}}","{{>layout/site-head}}");
                String result=compiler.compile(source).execute(model);
                assertThat(result).contains("/css/site-mobile.css", "name=\"viewport\"");
                String name=root.relativize(path).toString().replace('/', '_');
                Files.writeString(output.resolve(name+".html"),result);count++;
            }
        }
        for (String category : List.of("trade", "sell", "group-orders")) {
            var board = new HashMap<>(model);
            board.put("category", category); board.put("hideRecommendations", "group-orders".equals(category));
            board.put("categoryName", Map.of("trade","교환","sell","거래","group-orders","분철").get(category));
            board.put("articleList", List.of(new HashMap<>(post)));
            Files.writeString(output.resolve("board-" + category + ".html"),
                compiler.compile(Files.readString(root.resolve("article/article.mustache"))).execute(board));
        }
        assertThat(count).isGreaterThanOrEqualTo(30);
        var auth=new HashMap<>(model);auth.put("member",Map.of("userId",longText,"level",1,"points",100));
        String header=compiler.compile(Files.readString(root.resolve("layout/header.mustache"))).execute(auth);
        Files.writeString(output.resolve("header-auth.html"),"<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><link rel='stylesheet' href='/css/gravity.css'><link rel='stylesheet' href='/css/site-mobile.css'><link rel='stylesheet' href='/css/site-header.css'></head><body>"+header+"<main><h1>모바일 메뉴</h1><input aria-label='검색' placeholder='검색'></main></body></html>");
        Files.writeString(output.resolve("header-bare.html"),"<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><link rel='stylesheet' href='/css/site-mobile.css'><link rel='stylesheet' href='/css/site-header.css'></head><body>"+header+"<main><h1>스타일 체계가 다른 화면의 메뉴</h1><input aria-label='검색' placeholder='검색'></main></body></html>");
    }
    private static Map<String,Object> orbit(String name,int percent,int owned,int total) {
        return Map.of("member",name,"label",name,"percent",percent,"owned",owned,"total",total,"copies",owned,"hasTotal",true,"color","#8decd9","progressUrl","/cosmo/profile/MobileFan?tab=members","isEmpty",owned==0);
    }
}
