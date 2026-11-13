package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArticleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
class SearchLatestRepositoryTest {
    @Autowired ArchiveRepository archives;
    @Autowired ArticleRepository articles;
    @Autowired EntityManager entityManager;

    @Test
    void archiveSearchUsesDateBeforeLimitAndPlacesMissingDatesLast() {
        archives.save(archive("WAV newest", MediaType.IMAGE, LocalDate.of(2026, 5, 1)));
        archives.save(archive("wav middle", MediaType.IMAGE, LocalDate.of(2026, 3, 1)));
        archives.save(archive("wav oldest", MediaType.IMAGE, LocalDate.of(2025, 1, 1)));
        archives.save(archive("wav missing", MediaType.IMAGE, null));
        archives.save(archive("wav wrong type", MediaType.VIDEO, LocalDate.of(2028, 1, 1)));
        archives.save(archive("other", MediaType.IMAGE, LocalDate.of(2029, 1, 1)));
        archives.save(archive("literal w%v", MediaType.IMAGE, LocalDate.of(2029, 1, 1)));
        ArchiveService service = new ArchiveService(archives, mock(FileStorageService.class));

        assertThat(service.searchLatestByType(MediaType.IMAGE, "WaV", 2))
                .extracting(Archive::getTitle).containsExactly("WAV newest", "wav middle");
        assertThat(service.searchLatestByType(MediaType.IMAGE, "wav", 8))
                .extracting(Archive::getTitle).containsExactly("WAV newest", "wav middle", "wav oldest", "wav missing");
        assertThat(service.searchLatestByType(MediaType.IMAGE, "w%v", 8))
                .extracting(Archive::getTitle).containsExactly("literal w%v");
    }

    @Test
    void articleSearchUsesDateBeforeLimitAndPlacesMissingDatesLast() {
        articles.save(article("WAV newest", "info", LocalDateTime.of(2026, 5, 1, 1, 0)));
        articles.save(article("wav middle", "info", LocalDateTime.of(2026, 3, 1, 1, 0)));
        articles.save(article("wav oldest", "info", LocalDateTime.of(2025, 1, 1, 1, 0)));
        Article missing = articles.save(article("wav missing", "info", LocalDateTime.of(2024, 1, 1, 1, 0)));
        articles.save(article("wav notice", "notice", LocalDateTime.of(2028, 1, 1, 1, 0)));
        articles.save(article("literal w%v", "info", LocalDateTime.of(2028, 1, 1, 1, 0)));
        entityManager.flush();
        entityManager.createNativeQuery("update article set formatted_created_at = null where id = :id")
                .setParameter("id", missing.getId()).executeUpdate();
        entityManager.clear();
        ArticleService service = new ArticleServiceImpl(articles, mock(ArticleFileService.class));

        assertThat(service.searchLatestByCategory("INFO", "WaV", 2))
                .extracting(Article::getTitle).containsExactly("WAV newest", "wav middle");
        assertThat(service.searchLatestByCategory("info", "wav", 8))
                .extracting(Article::getTitle).containsExactly("WAV newest", "wav middle", "wav oldest", "wav missing");
        assertThat(service.searchLatestByCategory("info", "w%v", 8))
                .extracting(Article::getTitle).containsExactly("literal w%v");
    }

    private static Archive archive(String title, MediaType type, LocalDate registeredOn) {
        SavedMeta file = new SavedMeta("original", "saved", "/uploads/example", 1L, "image", type);
        Archive archive = new Archive(title, file);
        archive.setRegisteredOn(registeredOn);
        return archive;
    }

    private static Article article(String title, String category, LocalDateTime createdAt) {
        Article article = new Article();
        article.setDisplayId(1L);
        article.setTitle(title);
        article.setCategory(category);
        article.setUserId("test");
        article.setContent("test");
        article.setFormattedCreatedAt(createdAt);
        return article;
    }
}
