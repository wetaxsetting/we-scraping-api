package com.kosign.wecms.domain.payment.dispatch.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Real JSch-backed {@link SftpClient} — refactor of legacy {@code util.SftpUtil}
 * (JSch). Active only when {@code wecms.dispatch.sftp.enabled=true}; otherwise
 * {@link MockSftpClient} is the bean.
 *
 * <p>Auth mirrors the legacy quirks deliberately: key-based when a
 * {@code privateKeyPath} is configured (legacy used {@code ~/.ssh/id_rsa}), with
 * an optional passphrase; password auth as a fallback when no key is set.
 * {@code StrictHostKeyChecking=no} and the connect timeout match legacy
 * ({@code SftpUtil.CertInit}). Each operation opens and closes its own session
 * (legacy {@code CertInit}/{@code disconnect} per call) — bulk dispatch volume is
 * low, so connection pooling is unnecessary.
 *
 * <p>Credentials are never hardcoded — host/port/username/key/password come from
 * {@link SftpProperties} (env / {@code .gitignored} config).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wecms.dispatch.sftp", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class JschSftpClient implements SftpClient {

    private static final String CHANNEL_SFTP = "sftp";

    private final SftpProperties props;

    /** Upload a local file to {@code remotePath} (creating parent dirs). */
    @Override
    public void upload(String localPath, String remotePath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = openChannel(session);
            mkdirs(channel, parentOf(remotePath));
            try (var in = Files.newInputStream(Path.of(localPath))) {
                channel.put(in, remotePath);
            }
            log.info("SFTP upload ok local={} remote={}", localPath, remotePath);
        } catch (JSchException | SftpException | IOException e) {
            throw new SftpTransferException("SFTP upload failed: " + localPath + " -> " + remotePath, e);
        } finally {
            disconnect(channel, session);
        }
    }

    /** Download {@code remotePath} to a local file (creating local parent dirs). */
    @Override
    public void download(String remotePath, String localPath) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = openChannel(session);
            Path local = Path.of(localPath);
            if (local.getParent() != null) {
                Files.createDirectories(local.getParent());
            }
            try (var out = Files.newOutputStream(local)) {
                channel.get(remotePath, out);
            }
            log.info("SFTP download ok remote={} local={}", remotePath, localPath);
        } catch (JSchException | SftpException | IOException e) {
            throw new SftpTransferException("SFTP download failed: " + remotePath + " -> " + localPath, e);
        } finally {
            disconnect(channel, session);
        }
    }

    /**
     * Move/rename a remote file. <b>Best-effort</b> — a failed move is logged but
     * NOT rethrown, matching legacy {@code SftpUtil.fileMove} (a failure to relocate
     * the processed file must not abort the callback / corrupt WeCMS state).
     */
    @Override
    public void move(String remoteFrom, String remoteTo) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession();
            channel = openChannel(session);
            mkdirs(channel, parentOf(remoteTo));
            channel.rename(remoteFrom, remoteTo);
            log.info("SFTP move ok {} -> {}", remoteFrom, remoteTo);
        } catch (JSchException | SftpException e) {
            // Legacy parity: swallow — the move is housekeeping, not state-bearing.
            log.warn("SFTP move failed (ignored) {} -> {}: {}", remoteFrom, remoteTo, e.getMessage());
        } finally {
            disconnect(channel, session);
        }
    }

    private Session openSession() throws JSchException {
        if (!StringUtils.hasText(props.getHost()) || !StringUtils.hasText(props.getUsername())) {
            throw new SftpTransferException("SFTP host/username not configured (B4)", null);
        }
        JSch jsch = new JSch();
        boolean keyAuth = StringUtils.hasText(props.getPrivateKeyPath());
        if (keyAuth) {
            if (StringUtils.hasText(props.getPassword())) {
                jsch.addIdentity(props.getPrivateKeyPath(), props.getPassword()); // password = passphrase
            } else {
                jsch.addIdentity(props.getPrivateKeyPath());
            }
        }
        Session session = jsch.getSession(props.getUsername(), props.getHost(), props.getPort());
        if (!keyAuth && StringUtils.hasText(props.getPassword())) {
            session.setPassword(props.getPassword());
        }
        Properties cfg = new Properties();
        cfg.put("StrictHostKeyChecking", "no"); // legacy SftpUtil parity
        session.setConfig(cfg);
        session.setTimeout(props.getConnectTimeoutMs());
        session.connect(props.getConnectTimeoutMs());
        return session;
    }

    private ChannelSftp openChannel(Session session) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel(CHANNEL_SFTP);
        channel.connect(props.getConnectTimeoutMs());
        return channel;
    }

    /** Recursively create remote directories (JSch {@code mkdir} has no -p). */
    private void mkdirs(ChannelSftp channel, String dir) {
        if (!StringUtils.hasText(dir)) {
            return;
        }
        StringBuilder path = new StringBuilder(dir.startsWith("/") ? "/" : "");
        for (String seg : dir.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            path.append(seg);
            String cur = path.toString();
            try {
                channel.stat(cur);
            } catch (SftpException notThere) {
                try {
                    channel.mkdir(cur);
                } catch (SftpException e) {
                    log.debug("SFTP mkdir skipped for {}: {}", cur, e.getMessage());
                }
            }
            path.append('/');
        }
    }

    private static String parentOf(String remotePath) {
        if (!StringUtils.hasText(remotePath)) {
            return "";
        }
        int idx = remotePath.lastIndexOf('/');
        return idx <= 0 ? "" : remotePath.substring(0, idx);
    }

    private void disconnect(ChannelSftp channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /** Unchecked wrapper so callers (services) catch one transfer-failure type. */
    public static class SftpTransferException extends RuntimeException {
        public SftpTransferException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
