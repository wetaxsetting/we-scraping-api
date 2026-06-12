package com.kosign.wecms.domain.payment.dispatch.sftp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-only mock of {@link SftpClient}. Active whenever SFTP is NOT enabled
 * ({@code wecms.dispatch.sftp.enabled} false or missing) — i.e. every
 * local/test/R&D profile. Performs no network I/O; every operation throws so a
 * live transfer can never happen by accident.
 *
 * <p>Mutually exclusive with {@link JschSftpClient} via the inverse
 * {@code @ConditionalOnProperty} guard — no duplicate-bean conflict. Once the
 * SFTP key/host/whitelist are provisioned (B4) and {@code enabled=true} is set,
 * {@link JschSftpClient} (a JSch port of legacy {@code util.SftpUtil}) takes over.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wecms.dispatch.sftp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockSftpClient implements SftpClient {

    @Override
    public void upload(String localPath, String remotePath) {
        // TODO(B4): no live SFTP in local R&D.
        throw new UnsupportedOperationException("Live SFTP upload not implemented — blocked on B4 (key/host/whitelist).");
    }

    @Override
    public void download(String remotePath, String localPath) {
        // TODO(B4): no live SFTP in local R&D.
        throw new UnsupportedOperationException("Live SFTP download not implemented — blocked on B4 (key/host/whitelist).");
    }

    @Override
    public void move(String remoteFrom, String remoteTo) {
        // TODO(B4): no live SFTP in local R&D.
        throw new UnsupportedOperationException("Live SFTP move not implemented — blocked on B4 (key/host/whitelist).");
    }
}
