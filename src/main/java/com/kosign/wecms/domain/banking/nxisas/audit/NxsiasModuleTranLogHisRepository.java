package com.kosign.wecms.domain.banking.nxisas.audit;

import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHisId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NxsiasModuleTranLogHisRepository
        extends JpaRepository<NxsiasModuleTranLogHis, NxsiasModuleTranLogHisId> { }
