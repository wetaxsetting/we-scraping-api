package com.kosign.wecms.domain.payment.dispatch.callback;

/**
 * A required inbound callback field was absent, carrying the EFS result code legacy answers for
 * <b>that particular field</b>.
 *
 * <h2>Why the code travels with the exception</h2>
 * Legacy does not use one code for "something was missing" — it splits, and the split is
 * per-field and per-leg:
 *
 * <table border="1">
 *   <caption>legacy missing-field → RC</caption>
 *   <tr><th>Leg</th><th>Fields</th><th>RC</th><th>Legacy line</th></tr>
 *   <tr><td>01301</td><td>{@code ID} {@code RQ_DTIME} {@code TNO} {@code EV} {@code VV} {@code EM} {@code VM}</td>
 *       <td>{@code G001}</td><td>{@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:68-77}</td></tr>
 *   <tr><td>01301</td><td>{@code bank_cd} {@code prtn_id}</td>
 *       <td>{@code G002}</td><td>{@code API1_act.jsp:125-129}</td></tr>
 *   <tr><td>01301</td><td>{@code file_nm} {@code data_cnt} {@code data_amt} {@code data_dcm_amt}
 *       {@code result_cd} {@code result_msg}</td>
 *       <td>{@code G001}</td><td>{@code API1_act.jsp:131-147}</td></tr>
 *   <tr><td>01302</td><td>{@code ID} {@code EV} {@code VV}</td>
 *       <td>{@code G001}</td><td>{@code FILE_DEPOSIT_TRANSFER_API2_act.jsp:94-99}</td></tr>
 *   <tr><td>01302</td><td>{@code bank_cd} {@code prtn_id} {@code file_nm} {@code data_amt} {@code data_dcm_amt}</td>
 *       <td>{@code G002}</td><td>{@code API2_act.jsp:130-136}</td></tr>
 * </table>
 *
 * <p><b>Read the legacy constant names as labels, not as meanings.</b>
 * {@code CD_BASE_NONE_INPUT_DATA} is {@code G001} and {@code CD_DETAIL_NONE_INPUT_DATA} is
 * {@code G002} — but 01301 answers {@code G001} for six *body* fields and {@code G002} for exactly
 * two (the bank-identity pair). The names invert what the usage does; the table above is the
 * authority.
 *
 * <p>Because the correct code is decided entirely at the throw site, the acknowledgement builder
 * ({@code CallbackAck}) needs no notion of which leg it is serving — it simply honours the code the
 * exception carries. That is why there is no {@code CallbackLeg} parameter: after the 2026-08-03
 * correction to the unprovisioned-key mapping (G003 on <i>both</i> legs, not G004/G003), no
 * exception type maps to different codes on different legs, so a leg parameter would never be
 * branched on.
 *
 * <p>Extends {@link IllegalArgumentException} so any existing {@code catch (IllegalArgumentException)}
 * keeps working unchanged.
 */
public class MissingCallbackFieldException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient String resultCd;
    private final transient String resultMsg;

    public MissingCallbackFieldException(String resultCd, String resultMsg, String detail) {
        super(detail);
        this.resultCd = resultCd;
        this.resultMsg = resultMsg;
    }

    /** The EFS {@code RC} legacy answers for this field. */
    public String resultCd() {
        return resultCd;
    }

    /** The EFS {@code RM} paired with {@link #resultCd()}. */
    public String resultMsg() {
        return resultMsg;
    }
}
