package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.repository.InternalTransferKeySeqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalTransferKeyGenerator}. Verifies the
 * format contract — {@code YYYYMMDD<seq:6>} in Vietnam-local timezone
 * — and the tenant-scope guard.
 */
class InternalTransferKeyGeneratorTest {

    private InternalTransferKeySeqRepository repo;
    private Clock fixedClock;
    private InternalTransferKeyGenerator generator;

    @BeforeEach
    void setUp() {
        repo = mock(InternalTransferKeySeqRepository.class);
        // 2026-05-13 00:00 UTC == 2026-05-13 07:00 Asia/Ho_Chi_Minh.
        fixedClock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC);
        generator = new InternalTransferKeyGenerator(repo, fixedClock);
    }

    @Test
    void should_return_14_char_key_when_first_call_of_the_day() {
        when(repo.allocateNext(eq("VN_VNM0004_000001"), eq(LocalDate.of(2026, 5, 13))))
                .thenReturn(1);

        String key = generator.nextScdulPymnNo("VN_VNM0004_000001");

        assertThat(key).hasSize(14);
        assertThat(key).isEqualTo("20260513000001");
        verify(repo).allocateNext("VN_VNM0004_000001", LocalDate.of(2026, 5, 13));
    }

    @Test
    void should_zero_pad_seq_when_seq_under_six_digits() {
        when(repo.allocateNext(eq("VN_VNM0004_000001"), eq(LocalDate.of(2026, 5, 13))))
                .thenReturn(42);

        String key = generator.nextScdulPymnNo("VN_VNM0004_000001");

        assertThat(key).isEqualTo("20260513000042");
    }

    @Test
    void should_use_vietnam_local_date_when_utc_is_previous_day() {
        // 2026-05-12 23:00 UTC == 2026-05-13 06:00 Vietnam — key uses 2026-05-13.
        Clock late = Clock.fixed(Instant.parse("2026-05-12T23:00:00Z"), ZoneOffset.UTC);
        InternalTransferKeyGenerator localGen = new InternalTransferKeyGenerator(repo, late);
        when(repo.allocateNext(eq("VN_VNM0004_000001"), eq(LocalDate.of(2026, 5, 13))))
                .thenReturn(1);

        String key = localGen.nextScdulPymnNo("VN_VNM0004_000001");

        assertThat(key).startsWith("20260513");
    }

    @Test
    void should_throw_when_bizKey_blank() {
        assertThatThrownBy(() -> generator.nextScdulPymnNo(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bizKey");
        assertThatThrownBy(() -> generator.nextScdulPymnNo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bizKey");
    }

    @Test
    void should_throw_when_seq_overflows_six_digit_cap() {
        // Static format(...) is the seam — exercise it directly.
        assertThatThrownBy(() -> InternalTransferKeyGenerator.format(LocalDate.of(2026, 5, 13), 1_000_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out of range");
        assertThatThrownBy(() -> InternalTransferKeyGenerator.format(LocalDate.of(2026, 5, 13), 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
