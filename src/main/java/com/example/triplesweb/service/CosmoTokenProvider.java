package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.EnumSet;

/** Server-only COSMO credentials. JWT expiry is a refresh hint, not authentication. */
@Component
public class CosmoTokenProvider {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String encryptionKey;
    private final Path credentialsFile;
    private String accessToken;
    private String refreshToken;
    private boolean loaded;

    @org.springframework.beans.factory.annotation.Autowired
    public CosmoTokenProvider(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${gravity.cosmo-api-base-url:https://api.cosmo.fans}") String baseUrl,
            @Value("${gravity.cosmo-access-token:}") String accessToken,
            @Value("${gravity.cosmo-refresh-token:}") String refreshToken,
            @Value("${gravity.cosmo-key:}") String encryptionKey,
            @Value("${gravity.credentials-file:./data/cosmo/credentials.json}") String credentialsFile) {
        this(builder.clone().requestFactory(requestFactory()).build(), mapper, baseUrl,
                accessToken, refreshToken, encryptionKey, credentialsFile);
    }

    CosmoTokenProvider(RestClient client, ObjectMapper mapper, String baseUrl,
            String accessToken, String refreshToken, String encryptionKey, String credentialsFile) {
        this.client = client;
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.accessToken = accessToken.trim();
        this.refreshToken = refreshToken.trim();
        // Public protocol constant from the COSMO client; not an account credential.
        this.encryptionKey = encryptionKey.isBlank()
                ? "aUkaF3wWVltAI1QYVmxsZXfTV6L7mPTxA5+MMkLV9DE=" : encryptionKey.trim();
        this.credentialsFile = Path.of(credentialsFile).toAbsolutePath();
    }

    static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return factory;
    }

    public synchronized boolean isConfigured() {
        load();
        return !accessToken.isBlank() || !refreshToken.isBlank();
    }

    public synchronized String accessToken() {
        load();
        if (validUntil(accessToken, 60)) return accessToken;
        return refresh();
    }

    /** Retry one failed GET; concurrent callers reuse the first refreshed token. */
    public synchronized String afterUnauthorized(String rejected) {
        if (!accessToken.equals(rejected) && validUntil(accessToken, 0)) return accessToken;
        return refresh();
    }

    private void load() {
        if (loaded) return;
        if (Files.exists(credentialsFile)) {
            try {
                JsonNode credentials = mapper.readTree(Files.readString(credentialsFile));
                accessToken = credentials.path("accessToken").asText("");
                refreshToken = credentials.path("refreshToken").asText("");
            } catch (Exception e) {
                throw new IllegalStateException("COSMO 인증 파일을 읽을 수 없습니다. 서버 설정을 확인해 주세요.");
            }
        }
        loaded = true;
    }

    private String refresh() {
        if (!validUntil(refreshToken, 0)) {
            throw new IllegalStateException("COSMO 인증이 필요합니다. 관리자가 이메일로 다시 연결해야 합니다.");
        }
        exchangeCredentials("/bff/v3/users/refresh-access-token", Map.of("refreshToken", refreshToken));
        return accessToken;
    }

    /** Initial account credentials are obtained with a real, verified Privy session. */
    public synchronized void loginWithPrivy(String privyToken) {
        if (privyToken == null || privyToken.isBlank()) throw new IllegalArgumentException("Privy 인증이 필요합니다.");
        exchangeCredentials("/bff/v3/users/login-by-privy", Map.of("token", privyToken));
        loaded = true;
    }

    private void exchangeCredentials(String path, Map<String, String> payload) {
        try {
            String encrypted = encrypt(mapper.writeValueAsString(payload), encryptionKey);
            JsonNode response = client.post().uri(baseUrl + path)
                    .header("x-cosmo-encrypted", "1")
                    .header("User-Agent", "triplesweb/1.0")
                    .contentType(MediaType.TEXT_PLAIN).body(encrypted)
                    .exchange((request, reply) -> {
                        if (!reply.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("COSMO 인증 실패 (HTTP " + reply.getStatusCode().value() + "). 다시 인증해 주세요.");
                        }
                        String body = new String(reply.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return decodeResponse(body, "1".equals(reply.getHeaders().getFirst("x-cosmo-encrypted")));
                    });
            JsonNode credentials = response == null ? mapper.createObjectNode() : response.path("credentials");
            String nextAccess = credentials.path("accessToken").asText("");
            String nextRefresh = credentials.path("refreshToken").asText("");
            if (!validUntil(nextAccess, 0) || !validUntil(nextRefresh, 0)) {
                throw new IllegalStateException("COSMO 인증 응답이 올바르지 않습니다. 다시 인증해 주세요.");
            }
            accessToken = nextAccess;
            refreshToken = nextRefresh;
            persist();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Never propagate upstream response bodies, payloads or credentials.
            throw new IllegalStateException("COSMO 인증에 연결하지 못했습니다. 인증 설정과 연결을 확인해 주세요.");
        }
    }

    private void persist() {
        Path temporary = null;
        try {
            Files.createDirectories(credentialsFile.getParent());
            temporary = createPrivateTempFile(credentialsFile.getParent());
            Files.writeString(temporary, mapper.writeValueAsString(Map.of(
                    "accessToken", accessToken, "refreshToken", refreshToken)));
            Files.move(temporary, credentialsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new IllegalStateException("갱신된 COSMO 인증 파일을 저장하지 못했습니다. 서버 파일 권한을 확인해 주세요.");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            }
        }
    }

    /** Restrict access at creation, before any credential bytes are written. */
    static Path createPrivateTempFile(Path directory) throws IOException {
        var store = Files.getFileStore(directory);
        if (store.supportsFileAttributeView("posix")) {
            return Files.createTempFile(directory, ".credentials-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            // Resolve the process account rather than inheriting the parent directory's ACL.
            var user = directory.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            FileAttribute<List<AclEntry>> acl = new FileAttribute<>() {
                @Override public String name() { return "acl:acl"; }
                @Override public List<AclEntry> value() { return List.of(entry); }
            };
            return Files.createTempFile(directory, ".credentials-", ".tmp", acl);
        }
        throw new IOException("Credential storage requires POSIX permissions or an ACL-capable filesystem.");
    }

    JsonNode decodeResponse(String body, boolean encrypted) {
        try {
            return mapper.readTree(encrypted ? decrypt(body.trim(), encryptionKey) : body);
        } catch (Exception e) {
            throw new IllegalStateException("COSMO API 응답을 해석하지 못했습니다. 서버 설정을 확인해 주세요.");
        }
    }

    static boolean validUntil(String token, long marginSeconds) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
            return claims.path("exp").asLong(0) > Instant.now().getEpochSecond() + marginSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    static String encrypt(String value, String key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, iv);
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] payload = Arrays.copyOf(iv, iv.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(payload);
    }

    static String decrypt(String value, String key) throws Exception {
        byte[] payload = Base64.getDecoder().decode(value);
        if (payload.length < 32 || payload.length % 16 != 0) throw new IllegalArgumentException("Invalid encrypted response");
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, Arrays.copyOf(payload, 16));
        return new String(cipher.doFinal(Arrays.copyOfRange(payload, 16, payload.length)), StandardCharsets.UTF_8);
    }

    private static Cipher cipher(int mode, String key, byte[] iv) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(key);
        if (decoded.length != 32) throw new IllegalArgumentException("COSMO key must be 32 bytes");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(decoded, "AES"), new IvParameterSpec(iv));
        return cipher;
    }
}
