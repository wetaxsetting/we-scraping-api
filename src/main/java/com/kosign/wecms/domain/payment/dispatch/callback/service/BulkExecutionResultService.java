package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 01302 bulk execution result callback handler — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp}. <b>Unchanged from AS-IS</b> per the
 * confirmed TOBE.
 *
 * <p>Legacy flow: verify VV → decrypt EV → SFTP-download the fixed-length bank
 * result file → parse it (1000-byte records) into {@code TB_FIRM_FILE_RSLT} →
 * advance {@code PROC_STEP 2 → 3} → aggregate per-line outcomes into
 * {@code TB_PAY_FILE_MST} final status ({@code 0} all-success / {@code 1} partial
 * / {@code 3} all-fail) using the {@code TOT}/{@code SUCC}/{@code FAIL} counts.
 *
 * <p><b>SKELETON ONLY:</b>
 * <ul>
 *   <li><b>TODO(B4):</b> SFTP download of the result file.</li>
 *   <li><b>TODO(K3):</b> fixed-length parse ({@code FixedLengthCodec}) — offsets
 *       gated on the format version + a real sample (B6).</li>
 *   <li><b>TODO(B2):</b> VV verify / EV decrypt need the live API key.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkExecutionResultService {

    /**
     * Handle a 01302 execution-result callback. The encrypted {@code envelope} is
     * decrypted/verified into a {@link BulkExecutionResultRequest} before
     * processing. SKELETON — no parse, no aggregation.
     */
    public void handle(EfsCallbackEnvelope envelope) {
        // TODO(B2): verify VV + decrypt EV (EfsCryptoService) to yield a BulkExecutionResultRequest.
        // TODO(B4): SFTP-download the result file (SftpClient).
        // TODO(K3): parse the fixed-length result file (FixedLengthCodec) into TB_FIRM_FILE_RSLT.
        // TODO: advance PROC_STEP 2->3 and aggregate TB_PAY_FILE_MST final status (0/1/3).
        log.info("[skeleton] BulkExecutionResultService.handle id={} tno={} — not implemented (TODO B2/B4/K3)",
                envelope == null ? null : envelope.id(),
                envelope == null ? null : envelope.tno());
        throw new UnsupportedOperationException(
                "BulkExecutionResultService.handle not implemented — foundation skeleton (TODO B4 SFTP, K3 parse, B2 crypto).");
    }
}
