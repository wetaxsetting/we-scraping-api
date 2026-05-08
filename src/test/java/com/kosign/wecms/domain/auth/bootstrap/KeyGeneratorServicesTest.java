package com.kosign.wecms.domain.auth.bootstrap;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyGeneratorServicesTest {

    @Mock LegacyFunctionRepository legacyFunctions;

    @Test
    void businessKeyGenerator_routesThroughLegacyFunction() {
        when(legacyFunctions.nextVal("BUSI_INFM", "BIZ_KEY", "VN", "VNM0004", null, null, null, null))
                .thenReturn("VN_VNM0004_000002");

        BusinessKeyGeneratorService svc = new BusinessKeyGeneratorService(legacyFunctions);
        assertThat(svc.nextBizKey("VN", "VNM0004")).isEqualTo("VN_VNM0004_000002");

        verify(legacyFunctions).nextVal("BUSI_INFM", "BIZ_KEY", "VN", "VNM0004", null, null, null, null);
    }

    @Test
    void businessKeyGenerator_rejectsBlankNtnlOrChnl() {
        BusinessKeyGeneratorService svc = new BusinessKeyGeneratorService(legacyFunctions);
        assertThatThrownBy(() -> svc.nextBizKey(null, "VNM0004"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.nextBizKey("VN", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void employeeNumberGenerator_routesThroughLegacyFunction() {
        when(legacyFunctions.nextVal("EMPL_INFM", "EMPL_NO", "VN_VNM0004_000001", null, null, null, null, null))
                .thenReturn("20260429_001");

        EmployeeNumberGeneratorService svc = new EmployeeNumberGeneratorService(legacyFunctions);
        assertThat(svc.nextEmplNo("VN_VNM0004_000001", LocalDate.of(2026, 4, 29)))
                .isEqualTo("20260429_001");
    }

    @Test
    void employeeNumberGenerator_rejectsBlankBizKey() {
        EmployeeNumberGeneratorService svc = new EmployeeNumberGeneratorService(legacyFunctions);
        assertThatThrownBy(() -> svc.nextEmplNo(null, LocalDate.of(2026, 4, 29)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
