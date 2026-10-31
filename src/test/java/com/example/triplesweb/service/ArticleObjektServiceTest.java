package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.repository.ArticleObjektRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ArticleObjektServiceTest {

    @Test
    void validateTradeSidesRequiresHaveAndWant() {
        assertThat(ArticleObjektService.validateTradeSides(List.of(), List.of()))
                .isEqualTo("Have 오브젝트를 1개 이상 선택해주세요.");
        assertThat(ArticleObjektService.validateTradeSides(List.of("a"), List.of()))
                .isEqualTo("Want 오브젝트를 1개 이상 선택해주세요.");
        assertThat(ArticleObjektService.validateTradeSides(List.of(), List.of("b")))
                .isEqualTo("Have 오브젝트를 1개 이상 선택해주세요.");
        assertThat(ArticleObjektService.validateTradeSides(List.of("a"), List.of("a")))
                .isEqualTo("같은 오브젝트를 Have와 Want에 동시에 넣을 수 없습니다.");
        assertThat(ArticleObjektService.validateTradeSides(List.of("a"), List.of("b"))).isNull();
    }

    @Test
    void attachSelectedStoresSidesForTrade() {
        var repo = mock(ArticleObjektRepository.class);
        var catalog = mock(ObjektService.class);
        when(catalog.findCollections("triples")).thenReturn(List.of(
                Map.of("id", "have-id", "collectionId", "Have", "member", "JiYeon"),
                Map.of("id", "want-id", "collectionId", "Want", "member", "HaYeon")));
        var service = new ArticleObjektService(repo, catalog);
        Article article = new Article();
        article.setId(1L);
        service.attachSelected(article, List.of("have-id"), List.of("want-id"));
        ArgumentCaptor<ArticleObjekt> captor = ArgumentCaptor.forClass(ArticleObjekt.class);
        verify(repo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ArticleObjekt::getSide)
                .containsExactly(ArticleObjektService.SIDE_HAVE, ArticleObjektService.SIDE_WANT);
    }
}
