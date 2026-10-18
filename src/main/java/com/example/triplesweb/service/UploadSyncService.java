package com.example.triplesweb.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 공유 DB를 쓰는 로컬·서버 간 uploads 파일을 맞춘다.
 * 로컬 업로드 → 원격 push, 기동 시 원격 → 로컬 pull.
 * 서버(Docker)에서는 {@code upload.sync.enabled=false}로 끈다.
 */
@Service
@Slf4j
@Order(50)
public class UploadSyncService implements ApplicationRunner {

    private final boolean enabled;
    private final String host;
    private final String user;
    private final String remoteDir;
    private final Path sshKey;
    private final Path uploadRoot;
    private final boolean pullOnStartup;

    public UploadSyncService(
            @Value("${upload.sync.enabled:false}") boolean enabled,
            @Value("${upload.sync.host:}") String host,
            @Value("${upload.sync.user:pi}") String user,
            @Value("${upload.sync.remote-dir:/opt/stacks/triplesweb/uploads}") String remoteDir,
            @Value("${upload.sync.ssh-key:${user.home}/.ssh/pi_ed25519}") String sshKey,
            @Value("${file.upload-dir}") String uploadRoot,
            @Value("${upload.sync.pull-on-startup:true}") boolean pullOnStartup
    ) {
        this.enabled = enabled;
        this.host = host == null ? "" : host.trim();
        this.user = user;
        this.remoteDir = trimTrailingSlash(remoteDir);
        this.sshKey = Paths.get(sshKey).toAbsolutePath().normalize();
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.pullOnStartup = pullOnStartup;
    }

    @PostConstruct
    void logStatus() {
        if (!enabled) {
            log.info("Upload sync disabled");
            return;
        }
        if (!StringUtils.hasText(host)) {
            log.warn("Upload sync enabled but upload.sync.host is empty — sync skipped");
            return;
        }
        log.info("Upload sync enabled → {}@{}:{} (key={})", user, host, remoteDir, sshKey);
    }

    public boolean isActive() {
        return enabled && StringUtils.hasText(host) && Files.isRegularFile(sshKey);
    }

    /** 로컬에 저장된 상대 경로(uploads 루트 기준)를 원격으로 올린다. */
    public void pushRelative(String relativePath) {
        if (!isActive() || !StringUtils.hasText(relativePath)) return;
        String relative = normalizeRelative(relativePath);
        Path local = uploadRoot.resolve(relative).normalize();
        if (!local.startsWith(uploadRoot) || !Files.isRegularFile(local)) {
            return;
        }
        try {
            ensureRemoteParent(relative);
            rsyncToRemote(local, relative);
            Path sidecar = local.resolveSibling(local.getFileName().toString() + ".orig");
            if (Files.isRegularFile(sidecar)) {
                rsyncToRemote(sidecar, relative + ".orig");
            }
            chmodRemote(relative);
        } catch (Exception e) {
            log.warn("Upload sync push failed for {}: {}", relative, e.toString());
        }
    }

    /** 원격에서도 같은 상대 경로 파일을 지운다. */
    public void deleteRelative(String relativePath) {
        if (!isActive() || !StringUtils.hasText(relativePath)) return;
        String relative = normalizeRelative(relativePath);
        try {
            runSsh("rm -f "
                    + shellQuote(remoteDir + "/" + relative) + " "
                    + shellQuote(remoteDir + "/" + relative + ".orig"));
        } catch (Exception e) {
            log.warn("Upload sync delete failed for {}: {}", relative, e.toString());
        }
    }

    /** 원격 uploads → 로컬 (서버에서 올린 파일을 로컬에서도 보이게). */
    public void pullAll() {
        if (!isActive()) return;
        try {
            Files.createDirectories(uploadRoot);
            List<String> cmd = new ArrayList<>();
            cmd.add("rsync");
            cmd.add("-az");
            cmd.add("--no-owner");
            cmd.add("--no-group");
            cmd.add("--exclude");
            cmd.add(".DS_Store");
            cmd.add("-e");
            cmd.add(sshCommandString());
            cmd.add(user + "@" + host + ":" + remoteDir + "/");
            cmd.add(uploadRoot.toString() + "/");
            run(cmd, 600);
            log.info("Upload sync pull completed from {}@{}:{}", user, host, remoteDir);
        } catch (Exception e) {
            log.warn("Upload sync pull failed: {}", e.toString());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (pullOnStartup && isActive()) {
            pullAll();
        }
    }

    private void ensureRemoteParent(String relative) throws Exception {
        String parent = "";
        int idx = relative.lastIndexOf('/');
        if (idx > 0) {
            parent = relative.substring(0, idx);
        }
        String dir = parent.isEmpty() ? remoteDir : remoteDir + "/" + parent;
        runSsh("mkdir -p " + shellQuote(dir));
    }

    private void chmodRemote(String relative) throws Exception {
        runSsh("chmod a+rw "
                + shellQuote(remoteDir + "/" + relative)
                + " 2>/dev/null || true; "
                + "chmod a+rwX "
                + shellQuote(remoteDir + "/" + parentDir(relative))
                + " 2>/dev/null || true");
    }

    private void rsyncToRemote(Path localFile, String relative) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("rsync");
        cmd.add("-az");
        cmd.add("--no-owner");
        cmd.add("--no-group");
        cmd.add("-e");
        cmd.add(sshCommandString());
        cmd.add(localFile.toString());
        cmd.add(user + "@" + host + ":" + remoteDir + "/" + relative);
        run(cmd, 120);
    }

    private void runSsh(String remoteCommand) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.addAll(sshBaseArgs());
        cmd.add(user + "@" + host);
        cmd.add(remoteCommand);
        run(cmd, 60);
    }

    private List<String> sshBaseArgs() {
        return List.of(
                "-i", sshKey.toString(),
                "-o", "IdentitiesOnly=yes",
                "-o", "BatchMode=yes",
                "-o", "ConnectTimeout=10",
                "-o", "StrictHostKeyChecking=accept-new"
        );
    }

    private String sshCommandString() {
        return "ssh -i " + sshKey
                + " -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new";
    }

    private void run(List<String> cmd, long timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException("timed out: " + cmd.get(0));
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException(cmd.get(0) + " exit=" + p.exitValue() + " " + output);
        }
    }

    private static String normalizeRelative(String path) {
        String s = path.replace('\\', '/');
        if (s.startsWith("/uploads/")) s = s.substring("/uploads/".length());
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String parentDir(String relative) {
        int idx = relative.lastIndexOf('/');
        return idx > 0 ? relative.substring(0, idx) : ".";
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
