package com.kosign.wecms.domain.payment.dispatch.sftp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalised SFTP configuration — refactor home for the legacy
 * {@code wecms_batch.properties} {@code {ENV}_SFTP_IP/_PORT/_CERT/_PATH} and the
 * {@code _LOCAL_DOWN_PATH}/{@code _REMOTE_DOWN_PATH}/{@code _REMOTE_MOVE_PATH}
 * keys. Bound from {@code wecms.dispatch.sftp.*}.
 *
 * <p><b>All values empty by default — TODO(B4).</b> The private key is a secret
 * and must be supplied via {@code .gitignored} env/local config, never committed
 * (workspace rule). {@code privateKeyPath} points at a file path, not the key
 * material itself.
 */
@Component
@ConfigurationProperties(prefix = "wecms.dispatch.sftp")
@Getter
@Setter
public class SftpProperties {

    private String host = "";
    private int port = 22;
    private String username = "";
    /** Filesystem path to the private key (the key itself is never in config). TODO(B4). */
    private String privateKeyPath = "";
    private String remoteUploadPath = "";
    private String localDownloadPath = "";
    private String remoteDownloadPath = "";
    private String remoteMovePath = "";
}
