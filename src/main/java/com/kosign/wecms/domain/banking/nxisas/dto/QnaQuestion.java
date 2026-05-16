package com.kosign.wecms.domain.banking.nxisas.dto;

/**
 * One security question the bank adapter expects the user to answer.
 * Parsed server-side from the LOGIN response's {@code Result.req.req}
 * array (e.g. Shinhan VN returns 3 questions, the user picks ONE).
 * The {@code hiddenValue} echoes back unchanged with the answer so
 * the bank knows which question was answered.
 */
public record QnaQuestion(
        String hiddenValue,
        String question
) { }
