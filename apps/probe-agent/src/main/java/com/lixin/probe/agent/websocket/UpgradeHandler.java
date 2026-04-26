package com.lixin.probe.agent.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

@Component
public class UpgradeHandler {

    private static final Logger log = LoggerFactory.getLogger(UpgradeHandler.class);

    @Value("${probe.agent.admin-url:http://localhost:8081}")
    private String adminUrl;

    @Value("${probe.agent.upgrade-dir:${java.io.tmpdir}/probe-upgrade}")
    private String upgradeDir;

    private MessageSender messageSender;

    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @SuppressWarnings("unchecked")
    public void handleUpgrade(Map<String, Object> payload) {
        String targetVersion = (String) payload.get("targetVersion");
        String downloadUrl = (String) payload.get("downloadUrl");
        String expectedChecksum = (String) payload.get("checksum");

        log.info("[Upgrade] Starting upgrade to version: {}", targetVersion);

        try {
            // 1. Download
            String fullUrl = adminUrl + downloadUrl;
            log.info("[Upgrade] Downloading from: {}", fullUrl);
            Path jarPath = downloadJar(fullUrl, targetVersion);

            // 2. Verify checksum
            String actualChecksum = sha256(jarPath);
            if (expectedChecksum != null && !expectedChecksum.equals(actualChecksum)) {
                log.error("[Upgrade] Checksum mismatch! expected={}, actual={}", expectedChecksum, actualChecksum);
                Files.deleteIfExists(jarPath);
                sendStatus("FAILED", targetVersion, "Checksum mismatch");
                return;
            }
            log.info("[Upgrade] Checksum verified: {}", actualChecksum);

            // 3. Stage upgrade
            Path currentJar = getCurrentJarPath();
            Path backupPath = currentJar.resolveSibling(currentJar.getFileName() + ".bak");
            Path upgradeTarget = currentJar.resolveSibling("probe-agent-" + targetVersion + ".jar");

            Files.copy(jarPath, upgradeTarget, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(jarPath);

            log.info("[Upgrade] New JAR staged at: {}", upgradeTarget);

            // 4. Send success status
            sendStatus("READY", targetVersion, "Upgrade downloaded and verified, restarting...");

            // 5. Trigger restart
            scheduleRestart(currentJar, backupPath, upgradeTarget);

        } catch (Exception e) {
            log.error("[Upgrade] Upgrade failed", e);
            sendStatus("FAILED", targetVersion, e.getMessage());
        }
    }

    private Path downloadJar(String url, String version) throws Exception {
        Path dir = Paths.get(upgradeDir);
        Files.createDirectories(dir);
        Path target = dir.resolve("probe-agent-" + version + ".jar");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Download failed with HTTP " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("[Upgrade] Downloaded {} bytes", Files.size(target));
        return target;
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Path getCurrentJarPath() {
        try {
            String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            Path path = Paths.get(jarPath);
            // If running from a directory (IDE), return a reasonable default
            if (Files.isDirectory(path)) {
                return path.resolve("probe-agent.jar");
            }
            return path;
        } catch (Exception e) {
            return Paths.get("probe-agent.jar");
        }
    }

    private void scheduleRestart(Path currentJar, Path backupPath, Path upgradeTarget) {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                log.info("[Upgrade] Backing up current JAR to: {}", backupPath);
                Files.copy(currentJar, backupPath, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(upgradeTarget, currentJar, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(upgradeTarget);
                log.info("[Upgrade] Restarting with new version...");
                System.exit(0);
            } catch (Exception e) {
                log.error("[Upgrade] Restart failed", e);
                System.exit(1);
            }
        }, "UpgradeRestartThread").start();
    }

    private void sendStatus(String status, String version, String message) {
        log.info("[Upgrade] Status: status={}, version={}, message={}", status, version, message);
    }
}
