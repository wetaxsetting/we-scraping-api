package com.kosign.wecms.domain.payment.dispatch.sftp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Local-only mock of {@link SftpClient}. Default bean while SFTP is blocked;
 * performs no network I/O. Every operation throws so a live transfer can never
 * happen by accident in R&D.
 *
 * <p><b>TODO(B4):</b> replace with a JSch-backed adapter (port of legacy
 * {@code util.SftpUtil}) once the SFTP key/host/whitelist are provisioned.
 */
@Slf4j
@Component
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
