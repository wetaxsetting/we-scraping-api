package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /companies/{mst}/password}; {@code gdt_username} optionally replaces the stored login. */
public record CompanyPasswordRequest(
        @NotBlank @Size(max = 200) String password,
        @Size(max = 64) String gdtUsername) {

    /** Never print the password. */
    @Override
    public String toString() {
        return "CompanyPasswordRequest[password=***, gdtUsername=" + gdtUsername + "]";
    }
}
