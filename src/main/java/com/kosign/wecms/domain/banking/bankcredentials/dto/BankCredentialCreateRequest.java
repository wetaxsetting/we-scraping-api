package com.kosign.wecms.domain.banking.bankcredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankCredentialCreateRequest(
        @NotBlank @Size(max = 20) String bankCd,
        @NotBlank @Size(max = 2000) String username,
        @NotBlank @Size(max = 2000) String password
) { }
