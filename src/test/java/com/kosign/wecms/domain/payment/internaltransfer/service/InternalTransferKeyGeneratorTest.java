package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalTransferKeyGenerator} after the OD-2 reversal
 * (MR-1, 2026-06-09): the generator now delegates {@code SCDUL_PYMN_NO}
 * allocation to the {@link LegacyFunctionRepository} funnel
 * ({@code wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no', biz_key)}, V140
 * branch&nbsp;#36 — per-tenant plain-int {@code MAX+1}). The former
 * date-prefixed {@code YYYYMMDD<seq:6>} contract and its refactor-only counter
 * table are gone, so this test asserts delegation + the tenant-scope guard, not a format.
 */
class InternalTransferKeyGeneratorTest {

    private LegacyFunctionRepository legacyFunctions;
    private InternalTransferKeyGenerator generator;

    @BeforeEach
    void setUp() {
        legacyFunctions = mock(LegacyFunctionRepository.class);
        generator = new InternalTransferKeyGenerator(legacyFunctions);
    }

    @Test
    void should_return_funnel_value_and_delegate_to_wb_get_next_val() {
        when(legacyFunctions.nextScdulPymnNo("VN_VNM0004_000001")).thenReturn("1001");

        String key = generator.nextScdulPymnNo("VN_VNM0004_000001");

        assertThat(key).isEqualTo("1001");
        verify(legacyFunctions).nextScdulPymnNo("VN_VNM0004_000001");
    }

    @Test
    void should_pass_through_short_plain_int_keys() {
        // JEX MAX+1 can return a sub-5-digit key on a low-count tenant; the
        // generator must not reshape it (the controller @Pattern now accepts
        // {1,30} digits).
        when(legacyFunctions.nextScdulPymnNo("VN_VNM0004_000001")).thenReturn("7");

        assertThat(generator.nextScdulPymnNo("VN_VNM0004_000001")).isEqualTo("7");
    }

    @Test
    void should_throw_and_not_hit_the_funnel_when_bizKey_blank() {
        assertThatThrownBy(() -> generator.nextScdulPymnNo(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bizKey");
        assertThatThrownBy(() -> generator.nextScdulPymnNo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bizKey");
        verifyNoInteractions(legacyFunctions);
    }
}
