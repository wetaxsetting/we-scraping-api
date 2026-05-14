package com.kosign.wecms.domain.banking.bankcredentials.dto;

import jakarta.validation.constraints.Size;

/**
 * Update payload. {@code username} and {@code password} are optional —
 * a null field means "leave unchanged". Password update goes through
 * the same AES-GCM encryption path as create.
 */
public record BankCredentialUpdateRequest(
        @Size(max = 2000) String username,
        @Size(max = 2000) String password
) { }
