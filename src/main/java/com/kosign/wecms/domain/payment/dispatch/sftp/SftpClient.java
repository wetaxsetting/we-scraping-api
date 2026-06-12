package com.kosign.wecms.domain.payment.dispatch.sftp;

/**
 * Port for the firm-banking SFTP file channel — the refactor seam replacing the
 * legacy {@code util.SftpUtil} (JSch). BATCH1 uploaded the generated fixed-length
 * request file; the 01302 / API2 path downloaded the bank result file and moved
 * it to an archive path.
 *
 * <p><b>TODO(B4):</b> SFTP host/port, the private key (legacy {@code ~/.ssh/id_rsa}),
 * and the source-IP whitelist registration with the VAN are not yet provisioned.
 * Only {@link MockSftpClient} (no network) exists; no live transfer is implemented.
 */
public interface SftpClient {

    /** Upload a local file to the remote path. TODO(B4). */
    void upload(String localPath, String remotePath);

    /** Download a remote file to a local path. TODO(B4). */
    void download(String remotePath, String localPath);

    /** Move/rename a remote file (legacy archive step after download). TODO(B4). */
    void move(String remoteFrom, String remoteTo);
}
