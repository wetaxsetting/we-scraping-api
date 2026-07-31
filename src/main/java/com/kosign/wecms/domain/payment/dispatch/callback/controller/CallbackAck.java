package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.springframework.dao.DataAccessException;

/**
 * Builds the EFS acknowledgement envelope for a callback that failed <b>outside</b> its handler's
 * own result mapping — i.e. an exception that escaped {@code handle(...)}.
 *
 * <h2>Why this exists</h2>
 * The callback controllers previously called their service with no exception handling, so an escaped
 * exception reached {@code GlobalExceptionHandler} and the gateway received <b>HTTP 500 with a
 * {@code WBE9999} {@code ApiResponse} body</b> — the internal WeCMS envelope. The EFS gateway parses
 * only the transport envelope ({@code ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM}); an {@code ApiResponse} is
 * not a valid acknowledgement, so the callback outcome is undefined from the bank's side and the
 * gateway's retry behaviour is driven by an unparseable body instead of an {@code RC}.
 *
 * <h2>Legacy shape</h2>
 * Legacy wraps the whole action body in {@code try/catch} and, in the {@code catch}, sets only
 * {@code RC}/{@code RM} on the envelope it has already {@code copyDomain}'d from the request —
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:425-441} (and the API2 twin). The code is the
 * {@code JexBIZException}'s own code when it carries one, else {@code WCConst.CD_ETC_ERR}
 * ({@code G999}) / {@code MSG_ETC_ERR}. The inbound {@code EV}/{@code VV} ride back untouched. This
 * class reproduces that: echo the inbound envelope, set {@code RC}/{@code RM}.
 *
 * <h2>Code mapping</h2>
 * The refactor's exceptions carry {@code WBE_*} codes, which are <b>not</b> EFS result codes — sending
 * one as {@code RC} would be as unparseable as the JSON body it replaces. Each is therefore mapped to
 * the legacy {@code WCConst} code that describes the same class of failure:
 * <table border="1">
 *   <caption>Exception → RC</caption>
 *   <tr><th>Escaped exception</th><th>RC</th><th>Legacy constant</th></tr>
 *   <tr><td>{@link IllegalArgumentException} (a missing required business field —
 *       {@code requireField}, or a blank envelope {@code ID})</td>
 *       <td>{@code G002}</td><td>{@code CD_DETAIL_NONE_INPUT_DATA} — 데이터부 필수 입력값 누락</td></tr>
 *   <tr><td>{@link DataAccessException} (persistence fault)</td>
 *       <td>{@code G004}</td><td>{@code CD_DB_ERR} — DB처리 오류</td></tr>
 *   <tr><td>anything else ({@link BusinessException} incl. {@code WBE_EFS_RESULT_FILE},
 *       {@link IllegalStateException} from an unprovisioned key / crypto / (de)serialize, …)</td>
 *       <td>{@code G999}</td><td>{@code CD_ETC_ERR} — 기타에러</td></tr>
 * </table>
 *
 * <p>Note what is <b>not</b> here: {@code G003} (verify failure) and the per-file business codes are
 * produced by the handlers themselves on their normal return paths and never reach this class.
 *
 * <p><b>TODO(B5): confirm the gateway's retry semantics per code against the captured EFS sample.</b>
 * The mapping above is sound as a <i>description</i> of the failure — each code says the right thing
 * about what went wrong — but it also decides, implicitly, whether the gateway resends. That matters
 * most for {@code G999}, the default: if the gateway treats it as terminal, a transient WeCMS-side fault
 * (an unprovisioned key, a DB blip) silently consumes the callback and the reception result is lost;
 * if it retries, the callback returns after the fault clears and the file advances normally. The same
 * question applies to {@code G004}. B5 is the open item that will pin the full result-code set and its
 * retry behaviour from a real sample — check this table against it then, and if {@code G999} turns out
 * to be terminal, pick a code the gateway does retry for the transient cases.
 *
 * <p><b>Not an error swallow.</b> The caller logs the exception at {@code error} with the full stack
 * before returning the envelope; the transaction has already rolled back by then, so the file stays in
 * its prior state and the callback remains replayable — which is the state the {@code RC} advertises.
 */
final class CallbackAck {

    private CallbackAck() {
    }

    /** The EFS error acknowledgement for {@code e}: the inbound envelope echoed, with RC/RM set. */
    static EfsEnvelope error(EfsCallbackEnvelope in, Throwable e) {
        String rc = resultCd(e);
        return new EfsEnvelope(
                in == null ? null : in.id(),
                in == null ? null : in.rqDtime(),
                null,
                in == null ? null : in.tno(),
                in == null ? null : in.ev(),
                in == null ? null : in.vv(),
                in == null || in.em() == null ? EfsEnvelope.EM_AES : in.em(),
                in == null || in.vm() == null ? EfsEnvelope.VM_HMAC_SHA256 : in.vm(),
                rc,
                resultMsg(rc));
    }

    private static String resultCd(Throwable e) {
        if (e instanceof IllegalArgumentException) {
            return FirmBankingResultCd.DETAIL_NONE_INPUT_DATA;
        }
        if (e instanceof DataAccessException) {
            return FirmBankingResultCd.DB_ERR;
        }
        return FirmBankingResultCd.ETC_ERR;
    }

    private static String resultMsg(String rc) {
        return switch (rc) {
            case FirmBankingResultCd.DETAIL_NONE_INPUT_DATA -> FirmBankingResultCd.MSG_DETAIL_NONE_INPUT_DATA;
            case FirmBankingResultCd.DB_ERR -> FirmBankingResultCd.MSG_DB_ERR;
            default -> FirmBankingResultCd.MSG_ETC_ERR;
        };
    }
}
