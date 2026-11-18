package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /companies}. {@code gdt_username} is the GDT login when it differs from the MST
 * (sub-account form {@code 1201496252-U002}); blank = the MST itself.
 */
public record CompanyCreateRequest(
        @NotBlank @Size(max = 14) String companyMst,
        @Size(max = 64) String gdtUsername,
        @NotBlank @Size(max = 200) String password,
        @Size(max = 100) String label) {

    /** Never print the password. */
    @Override
    public String toString() {
        return "CompanyCreateRequest[companyMst=" + companyMst + ", gdtUsername=" + gdtUsername
                + ", password=***, label=" + label + "]";
    }
}
