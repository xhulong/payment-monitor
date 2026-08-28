package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmMerchantApiAudit;
import org.dromara.payment.mapper.MerchantApiAuditMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MerchantApiAuditService {
    private final MerchantApiAuditMapper mapper;
    private final MerchantAccessService accessService;
    private final MerchantDisplayService merchantDisplayService;

    public void record(
        Long merchantId,
        Long apiKeyId,
        String keyId,
        String method,
        String path,
        String clientIp,
        int httpStatus,
        String resultCode,
        long durationMs
    ) {
        PmMerchantApiAudit audit = new PmMerchantApiAudit();
        audit.setId(IdWorker.getId());
        audit.setMerchantId(merchantId);
        audit.setApiKeyId(apiKeyId);
        audit.setKeyId(keyId);
        audit.setRequestMethod(method);
        audit.setRequestPath(path);
        audit.setClientIp(clientIp);
        audit.setHttpStatus(httpStatus);
        audit.setResultCode(resultCode);
        audit.setSuccess(httpStatus >= 200 && httpStatus < 300);
        audit.setDurationMs(Math.max(0, durationMs));
        audit.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapper.insert(audit);
    }

    public PageResult<PmMerchantApiAudit> queryPage(Long merchantId, PageQuery pageQuery) {
        Long scopedMerchantId = MerchantContext.resolveQueryMerchantId(merchantId);
        Page<PmMerchantApiAudit> page = mapper.selectPage(
            pageQuery.build(),
            new LambdaQueryWrapper<PmMerchantApiAudit>()
                .eq(scopedMerchantId != null, PmMerchantApiAudit::getMerchantId, scopedMerchantId)
                .orderByDesc(PmMerchantApiAudit::getCreatedAt));
        merchantDisplayService.enrich(
            page.getRecords(),
            PmMerchantApiAudit::getMerchantId,
            PmMerchantApiAudit::setMerchantCode,
            PmMerchantApiAudit::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }
}
