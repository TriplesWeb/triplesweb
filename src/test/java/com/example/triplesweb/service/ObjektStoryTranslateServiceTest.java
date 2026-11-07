package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ObjektStoryTranslateServiceTest {
    @Test void looksKoreanWhenHangulDominates() {
        assertThat(ObjektStoryTranslateService.looksKorean("관리자 설명")).isTrue();
        assertThat(ObjektStoryTranslateService.looksKorean("Part of tripleS")).isFalse();
        assertThat(ObjektStoryTranslateService.looksKorean("인형 키링의 일부")).isTrue();
    }

    @Test void koreanKeepsNamesAndCachesSentenceTranslation() {
        var builder = RestClient.builder().baseUrl("https://translate.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/get")))
                .andRespond(withSuccess("{\"responseData\":{\"translatedText\":\"[[T0]] 공식 캐릭터 [[T1]] 인형 키링의 일부\"}}", MediaType.APPLICATION_JSON));
        var service = new ObjektStoryTranslateService(builder.build(), true);
        var story = new ApolloObjektMetadataClient.Story(
                "cream02-kotone-356a",
                "Part of tripleS Official Character byteS Plush Keyring",
                "tripleS Official Character byteS",
                "",
                "merch",
                "<LOVE&POP> pt.1",
                "");
        var first = service.korean(story);
        var second = service.korean(story);
        assertThat(first.description()).isEqualTo("tripleS 공식 캐릭터 byteS 인형 키링의 일부");
        assertThat(first.eventName()).isEqualTo("tripleS Official Character byteS");
        assertThat(first.eraName()).isEqualTo("<LOVE&POP> pt.1");
        assertThat(second.description()).isEqualTo(first.description());
        server.verify();
    }

    @Test void keepsEnglishWhenTranslateFailsOrAlreadyKorean() {
        var builder = RestClient.builder().baseUrl("https://translate.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/get")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        var service = new ObjektStoryTranslateService(builder.build(), true);
        assertThat(service.sentence("Physical objekts")).isEqualTo("Physical objekts");
        assertThat(service.sentence("관리자 한글 설명")).isEqualTo("관리자 한글 설명");
        assertThat(new ObjektStoryTranslateService(RestClient.create(), false).sentence("Hello")).isEqualTo("Hello");
        server.verify();
    }
}
