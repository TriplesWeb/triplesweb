package com.example.triplesweb.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;

/** One-shot import without starting web routes, scheduled jobs or the full-chain indexer. */
public class CosmoObjektImportCommand {
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class, RestClientAutoConfiguration.class})
    @Import({CosmoTokenProvider.class, CosmoObjektSyncService.class, CosmoObjektSnapshotStore.class})
    static class ImportConfiguration {}
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ImportConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        try (var context = application.run(args)) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V35__cosmo_owned_objekt_snapshot.sql"))
                    .execute(context.getBean(DataSource.class));
            var result = context.getBean(CosmoObjektSyncService.class).sync();
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            Integer verified = jdbc.queryForObject("SELECT COUNT(*) FROM cosmo_owned_objekt", Integer.class);
            if (verified == null || verified < result.objekts()) throw new IllegalStateException("DB 저장 결과를 확인할 수 없습니다.");
            System.out.printf("COSMO DB import completed: collections=%d, objekts=%d, changed=%d, verifiedDbRows=%d%n",
                    result.collections(), result.objekts(), result.changed(), verified);
        }
    }
}
