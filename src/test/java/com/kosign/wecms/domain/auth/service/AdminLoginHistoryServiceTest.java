package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.auth.entity.BusiAdmnLoginHis;
import com.kosign.wecms.domain.auth.repository.BusiAdmnLoginHisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminLoginHistoryServiceTest {

    @Mock BusiAdmnLoginHisRepository repository;
    @InjectMocks AdminLoginHistoryService service;

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String ID  = "admin@wecms.vn";
    private static final String NM  = "Admin Name";
    private static final String IP  = "10.0.0.5";

    @Test
    void record_populatesLegacyFields_andGeneratesUuidAndUtcDtm() {
        service.record(BIZ, ID, NM, IP);

        ArgumentCaptor<BusiAdmnLoginHis> cap = ArgumentCaptor.forClass(BusiAdmnLoginHis.class);
        verify(repository).save(cap.capture());
        BusiAdmnLoginHis row = cap.getValue();

        assertThat(row.getBizKey()).isEqualTo(BIZ);
        assertThat(row.getRegId()).isEqualTo(ID);
        assertThat(row.getRegNm()).isEqualTo(NM);
        assertThat(row.getIpAddr()).isEqualTo(IP);
        assertThat(row.getLgnChnl()).isEqualTo("PC");                 // legacy LgnChnl.PC
        assertThat(row.getRegDtm()).matches("\\d{14}");               // UTC yyyyMMddHHmmss
        assertThat(row.getLoginUuid()).isNotBlank();                  // Java equivalent of DB default
    }
}
