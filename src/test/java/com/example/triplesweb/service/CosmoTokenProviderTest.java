package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclEntryPermission;
import java.util.EnumSet;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CosmoTokenProviderTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    static String jwt(long seconds) {
        return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"exp\":" + (Instant.now().getEpochSecond() + seconds) + "}").getBytes()) + ".test";
    }

    @Test void validTokenDoesNotCallRefresh() {
        String access = jwt(3600);
        var provider = provider(RestClient.create(), access, "", "");
        assertThat(provider.accessToken()).isEqualTo(access);
    }

    @Test void refreshEncryptsPayloadPersistsRotationAndSurvivesRestart() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String refresh = jwt(7200), freshAccess = jwt(3600), freshRefresh = jwt(14400);
        String reply = mapper.writeValueAsString(Map.of("credentials", Map.of(
                "accessToken", freshAccess, "refreshToken", freshRefresh)));
        server.expect(requestTo("https://cosmo.test/bff/v3/users/refresh-access-token"))
                .andExpect(header("x-cosmo-encrypted", "1"))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(request -> {
                    try {
                        String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                        assertThat(mapper.readTree(CosmoTokenProvider.decrypt(body, key)).path("refreshToken").asText()).isEqualTo(refresh);
                    } catch (Exception e) { throw new AssertionError(e); }
                })
                .andRespond(withSuccess(CosmoTokenProvider.encrypt(reply, key), MediaType.TEXT_PLAIN)
                        .header("x-cosmo-encrypted", "1"));
        var provider = provider(builder.build(), jwt(-1), refresh, key);
        // Concurrent callers must redeem a rotating refresh token only once.
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> calls = List.of(provider::accessToken, provider::accessToken,
                    provider::accessToken, provider::accessToken);
            for (var result : pool.invokeAll(calls)) assertThat(result.get()).isEqualTo(freshAccess);
        } finally { pool.shutdownNow(); }
        Path file = directory.resolve("credentials.json");
        assertThat(mapper.readTree(Files.readString(file)).path("refreshToken").asText()).isEqualTo(freshRefresh);
        assertPrivateFile(file);
        assertThat(provider(RestClient.create(), "", "", key).accessToken()).isEqualTo(freshAccess);
        server.verify();
    }

    @Test void firstLoginEncryptsPrivyTokenAndStoresAccountTokens() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String access = jwt(3600), refresh = jwt(7200);
        server.expect(requestTo("https://cosmo.test/bff/v3/users/login-by-privy"))
                .andExpect(header("x-cosmo-encrypted", "1"))
                .andExpect(request -> {
                    try {
                        String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                        assertThat(mapper.readTree(CosmoTokenProvider.decrypt(body, key)).path("token").asText()).isEqualTo("verified-privy-session");
                    } catch (Exception e) { throw new AssertionError(e); }
                })
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("credentials",
                        Map.of("accessToken", access, "refreshToken", refresh))), MediaType.APPLICATION_JSON));
        var provider = provider(builder.build(), "", "", key);
        assertThat(provider.isConfigured()).isFalse();
        provider.loginWithPrivy("verified-privy-session");
        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.accessToken()).isEqualTo(access);
        assertThat(provider(RestClient.create(), "", "", key).accessToken()).isEqualTo(access);
        assertPrivateFile(directory.resolve("credentials.json"));
        server.verify();
    }

    @Test void temporaryFileIsPrivateBeforeWritingCredentials() throws Exception {
        Path temporary = CosmoTokenProvider.createPrivateTempFile(directory);
        assertThat(Files.size(temporary)).isZero();
        assertPrivateFile(temporary);
    }

    @Test void replacingExistingCredentialsRetainsPrivatePermissionsAndCleansTemporaryFiles() throws Exception {
        Path file = directory.resolve("credentials.json");
        Files.writeString(file, "{}");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String access = jwt(3600), refresh = jwt(7200);
        server.expect(requestTo("https://cosmo.test/bff/v3/users/login-by-privy"))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("credentials",
                        Map.of("accessToken", access, "refreshToken", refresh))), MediaType.APPLICATION_JSON));
        provider(builder.build(), "", "", key).loginWithPrivy("verified-privy-session");
        assertPrivateFile(file);
        assertThat(mapper.readTree(Files.readString(file)).path("accessToken").asText()).isEqualTo(access);
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(file);
        }
        server.verify();
    }

    private static void assertPrivateFile(Path file) throws Exception {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        } else {
            var view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertThat(view).isNotNull();
            var expectedUser = file.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            assertThat(view.getAcl()).hasSize(1).allSatisfy(entry -> {
                assertThat(entry.type()).isEqualTo(AclEntryType.ALLOW);
                assertThat(entry.principal()).isEqualTo(expectedUser);
                assertThat(entry.permissions()).containsAll(EnumSet.of(
                        AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.DELETE));
            });
        }
    }

    @Test void refreshFailureDoesNotLeakResponseOrOverwriteCredentials() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://cosmo.test/bff/v3/users/refresh-access-token"))
                .andRespond(withUnauthorizedRequest().body("secret-upstream-body"));
        assertThatThrownBy(() -> provider(builder.build(), jwt(-1), jwt(3600), key).accessToken())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("401")
                .hasMessageNotContaining("secret-upstream-body");
        assertThat(directory.resolve("credentials.json")).doesNotExist();
        server.verify();
    }

    @Test void expiredRefreshRequiresLoginAndInvalidFileFailsClosed() throws Exception {
        assertThatThrownBy(() -> provider(RestClient.create(), jwt(-1), jwt(-1), key).accessToken())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("인증");
        Files.writeString(directory.resolve("credentials.json"), "broken-file");
        assertThatThrownBy(() -> provider(RestClient.create(), jwt(3600), "", key).accessToken())
                .hasMessageContaining("파일");
    }

    private CosmoTokenProvider provider(RestClient client, String access, String refresh, String key) {
        return new CosmoTokenProvider(client, mapper, "https://cosmo.test", access, refresh, key,
                directory.resolve("credentials.json").toString());
    }
}
