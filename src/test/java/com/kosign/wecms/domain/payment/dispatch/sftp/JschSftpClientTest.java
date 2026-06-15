package com.kosign.wecms.domain.payment.dispatch.sftp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit guard for {@link JschSftpClient}. A full transfer needs a live SFTP server
 * (covered by manual/integration testing once B4 provisions the host/key); here we
 * assert the configuration guard — an unconfigured host/username fails fast with
 * {@link JschSftpClient.SftpTransferException} rather than attempting a connection.
 */
class JschSftpClientTest {

    @Test
    void upload_withUnconfiguredHost_throwsTransferException() {
        SftpProperties props = new SftpProperties(); // host/username blank
        JschSftpClient client = new JschSftpClient(props);
        assertThrows(JschSftpClient.SftpTransferException.class,
                () -> client.upload("/tmp/whatever", "remote/whatever"));
    }
}
