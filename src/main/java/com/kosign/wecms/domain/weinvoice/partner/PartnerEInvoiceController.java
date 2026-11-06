package com.kosign.wecms.domain.weinvoice.partner;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceRow;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WeInvoice Partner API — called by WeCMS (server to server) instead of the Coocon engine.
 * Authenticated by {@link PartnerApiKeyFilter}; there is no user and no tenant, the caller names
 * the tax code. Dates are {@code yyyyMMdd} like the WeCMS intake.
 *
 * <ul>
 *   <li>{@code GET /einvoices} — invoice headers ({@code ScrapedEInvoiceRow}). {@code EXPENSE}
 *       covers the three GDT purchase lists (coded, without code, cash register); each row's
 *       {@code einv_test_results} tells them apart ({@code ""}, {@code "1"}, {@code "2"}).</li>
 *   <li>{@code POST /einvoices/details} — detail rows ({@code ScrapedEInvoiceDetailRow}) for
 *       up to 500 keys; unknown keys come back in {@code not_found}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/partner/v1")
@RequiredArgsConstructor
@Validated
public class PartnerEInvoiceController {

    private static final String DATE_8 = "\\d{8}";

    private final PartnerEInvoiceService service;

    @GetMapping("/einvoices")
    public ApiResponse<WeInvoicePage<ScrapedEInvoiceRow>> list(
            @RequestParam("tax_id") @NotBlank String taxId,
            @RequestParam("type") @NotBlank String type,
            @RequestParam("from") @Pattern(regexp = DATE_8, message = "from must be yyyyMMdd") String from,
            @RequestParam("to") @Pattern(regexp = DATE_8, message = "to must be yyyyMMdd") String to,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        return ApiResponse.ok(service.list(taxId, type, from, to, page, size));
    }

    @PostMapping("/einvoices/details")
    public ApiResponse<PartnerDetailResponse> details(@Valid @RequestBody PartnerDetailRequest request) {
        return ApiResponse.ok(service.details(request));
    }
}
