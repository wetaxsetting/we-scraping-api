package com.kosign.wecms.domain.banking.smartorganizer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Bulk reorder payload — the Organizer Settings popup posts the full
 * rule chain in the user's chosen order. Each entry pins a row's
 * {@code dispOrd}; service renumbers densely 1..N for stability.
 * Mirrors legacy {@code fnsh_0200_002_u001} REC1.
 */
public record OrganizerRuleReorderRequest(
        @NotEmpty
        @Valid
        List<Entry> rows
) {
    public record Entry(
            @NotNull @Positive Long rmrkSeqNo,
            @NotNull @Positive Integer dispOrd
    ) {}
}
