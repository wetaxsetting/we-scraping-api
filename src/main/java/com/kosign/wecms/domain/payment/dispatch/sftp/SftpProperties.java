package com.kosign.wecms.domain.payment.dispatch.sftp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalised SFTP configuration — refactor home for the legacy
 * {@code wecms_batch.properties} {@code {ENV}_SFTP_IP/_PORT/_CERT/_PATH} and the
 * {@code _SEND_PATH}/{@code _LOCAL_DOWN_PATH}/{@code _REMOTE_DOWN_PATH}/
 * {@code _REMOTE_MOVE_PATH} keys. Bound from {@code wecms.dispatch.sftp.*}.
 *
 * <p><b>Disabled by default — TODO(B4).</b> When {@code enabled=false} (the
 * default, every local/test/R&D profile), {@link MockSftpClient} is the active
 * bean and no network I/O happens. Set {@code enabled=true} only in an
 * environment that has provisioned the key/host/whitelist; then
 * {@link JschSftpClient} is wired.
 *
 * <p>The private key is a secret and must be supplied via {@code .gitignored}
 * env/local config, never committed (workspace rule). {@code privateKeyPath}
 * points at a file path, not the key material itself. {@code password} is an
 * alternative to key auth (legacy used key-only; both are supported here).
 */
@Component
@ConfigurationProperties(prefix = "wecms.dispatch.sftp")
@Getter
@Setter
public class SftpProperties {

    /** Master switch — {@code false} keeps {@link MockSftpClient} active (no live SFTP). */
    private boolean enabled = false;

    private String host = "";
    private int port = 22;
    private String username = "";

    /** Filesystem path to the private key (the key itself is never in config). TODO(B4). */
    private String privateKeyPath = "";
    /** Optional passphrase for the private key, or password when no key is used. */
    private String password = "";

    /** TCP connect / channel timeout in milliseconds (legacy SftpUtil used 60s). */
    private int connectTimeoutMs = 60_000;

    /** Local staging dir the generated 00301 request file is written to before upload. */
    private String localUploadPath = "";
    /** Remote dir the 00301 request file is uploaded into (legacy {@code sdata}). */
    private String remoteUploadPath = "";

    /** Local dir the 01302 result file is downloaded into. */
    private String localDownloadPath = "";
    /** Remote dir the 01302 result file is downloaded from (legacy {@code rdata}). */
    private String remoteDownloadPath = "";
    /** Remote dir a downloaded result file is moved to after processing (legacy {@code rdata/success}). */
    private String remoteMovePath = "";
}
