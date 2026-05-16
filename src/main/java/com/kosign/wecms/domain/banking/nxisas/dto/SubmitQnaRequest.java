package com.kosign.wecms.domain.banking.nxisas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire body for {@code POST /api/v1/banking/scraping/{sessionId}/qna}.
 *
 * <p>The user picks one of the LOGIN response's security questions
 * and types its answer. {@code question} + {@code hiddenValue} echo
 * back unchanged from {@link QnaQuestion}; {@code answer} is the
 * free-text response.
 *
 * <p>Legacy parity: shape lifted from nxisas_v2.js:222-228 —
 * {@code Input.res = { Question, HiddenValue, Answer }}.
 */
public record SubmitQnaRequest(

        @NotBlank
        @Size(max = 200)
        String hiddenValue,

        @NotBlank
        @Size(max = 500)
        String question,

        @NotBlank
        @Size(max = 200)
        String answer) { }
