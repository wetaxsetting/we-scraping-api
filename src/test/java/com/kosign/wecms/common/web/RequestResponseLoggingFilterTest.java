package com.kosign.wecms.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for {@link RequestResponseLoggingFilter}'s body-logging policy.
 * Binary / download content types must log metadata only — never the
 * stringified bytes — while JSON / text bodies keep logging in full.
 *
 * <p>The motivating bug: the XLSX MIME type
 * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}
 * contains the substring {@code "xml"}, so the old textual heuristic dumped
 * the ZIP body ({@code PK ... xl/workbook.xml ...}) to the log.
 */
class RequestResponseLoggingFilterTest {

    private final RequestResponseLoggingFilter filter = new RequestResponseLoggingFilter();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    /** Concatenated formatted text of every captured log line. */
    private String capturedLog() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : appender.list) {
            sb.append(e.getFormattedMessage()).append('\n');
        }
        return sb.toString();
    }

    private void runFilter(String uri, String contentType, byte[] body, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(status);
            http.setContentType(contentType);
            http.getOutputStream().write(body);
        };
        filter.doFilter(request, response, chain);
    }

    @Test
    void xlsxResponse_bodyOmitted_noBinaryLeak() throws Exception {
        // A real .xlsx starts with the ZIP magic "PK\x03\x04" and contains
        // the workbook part path — exactly what was leaking into the log.
        byte[] xlsx = "PK xl/workbook.xml xl/worksheets/sheet1.xml"
                .getBytes(StandardCharsets.ISO_8859_1);

        runFilter("/api/v1/addon/operation-status/export",
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                  xlsx, 200);

        String log = capturedLog();
        assertThat(log).contains("[binary response body omitted]");
        assertThat(log).contains("content-type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(log).contains("size=" + xlsx.length + " bytes");
        // The whole point: no raw ZIP / workbook bytes in the log.
        assertThat(log).doesNotContain("PK");
        assertThat(log).doesNotContain("workbook.xml");
    }

    @Test
    void pdfResponse_bodyOmitted() throws Exception {
        byte[] pdf = "%PDF-1.7 binary…".getBytes(StandardCharsets.ISO_8859_1);
        runFilter("/api/v1/some/report.pdf", "application/pdf", pdf, 200);

        String log = capturedLog();
        assertThat(log).contains("[binary response body omitted]");
        assertThat(log).doesNotContain("%PDF");
    }

    @Test
    void octetStreamResponse_bodyOmitted() throws Exception {
        runFilter("/api/v1/download", "application/octet-stream",
                  new byte[]{1, 2, 3, 4, 5}, 200);

        String log = capturedLog();
        assertThat(log).contains("[binary response body omitted]");
        assertThat(log).contains("size=5 bytes");
    }

    @Test
    void imageResponse_bodyOmitted_matchedByPrefix() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        runFilter("/api/v1/users/u1/image", "image/png", png, 200);

        assertThat(capturedLog()).contains("[binary response body omitted]");
    }

    @Test
    void contentTypeWithCharsetParam_stillTreatedAsBinary() throws Exception {
        runFilter("/api/v1/download", "application/octet-stream; charset=binary",
                  new byte[]{9, 9, 9}, 200);

        assertThat(capturedLog()).contains("[binary response body omitted]");
    }

    @Test
    void jsonResponse_bodyStillLogged() throws Exception {
        byte[] json = "{\"status\":{\"error\":false,\"code\":\"WBI0000\"}}"
                .getBytes(StandardCharsets.UTF_8);
        runFilter("/api/v1/addon/operation-status", "application/json", json, 200);

        String log = capturedLog();
        assertThat(log).doesNotContain("[binary response body omitted]");
        // JSON bodies keep logging in full (pretty-printed).
        assertThat(log).contains("WBI0000");
    }
}
