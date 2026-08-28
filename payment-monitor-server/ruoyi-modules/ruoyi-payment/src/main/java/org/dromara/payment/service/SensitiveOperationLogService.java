package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmSensitiveOperationLog;
import org.dromara.payment.domain.bo.SensitiveOperationQueryBo;
import org.dromara.payment.mapper.SensitiveOperationLogMapper;
import org.dromara.payment.security.StepUpVerificationMethod;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SensitiveOperationLogService {
    private final SensitiveOperationLogMapper mapper;
    private final ObjectMapper objectMapper;
    private final MerchantDisplayService merchantDisplayService;

    public PageResult<PmSensitiveOperationLog> queryPage(
        SensitiveOperationQueryBo bo,
        PageQuery pageQuery
    ) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        LambdaQueryWrapper<PmSensitiveOperationLog> wrapper =
            new LambdaQueryWrapper<PmSensitiveOperationLog>()
                .eq(merchantId != null, PmSensitiveOperationLog::getMerchantId, merchantId)
                .eq(StringUtils.isNotBlank(bo.getOperationType()),
                    PmSensitiveOperationLog::getOperationType,
                    bo.getOperationType())
                .eq(StringUtils.isNotBlank(bo.getTargetType()),
                    PmSensitiveOperationLog::getTargetType,
                    bo.getTargetType())
                .eq(bo.getTargetId() != null,
                    PmSensitiveOperationLog::getTargetId,
                    bo.getTargetId())
                .orderByDesc(PmSensitiveOperationLog::getOperatedAt);
        Page<PmSensitiveOperationLog> page =
            mapper.selectPage(pageQuery.build(), wrapper);
        merchantDisplayService.enrich(
            page.getRecords(),
            PmSensitiveOperationLog::getMerchantId,
            PmSensitiveOperationLog::setMerchantCode,
            PmSensitiveOperationLog::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PmSensitiveOperationLog queryById(Long id) {
        PmSensitiveOperationLog log = mapper.selectOne(
            new LambdaQueryWrapper<PmSensitiveOperationLog>()
                .eq(PmSensitiveOperationLog::getId, id)
                .last("limit 1"));
        if (log == null) {
            throw new ServiceException("敏感操作记录不存在");
        }
        MerchantContext.requireAccessibleMerchant(log.getMerchantId());
        merchantDisplayService.enrich(
            List.of(log),
            PmSensitiveOperationLog::getMerchantId,
            PmSensitiveOperationLog::setMerchantCode,
            PmSensitiveOperationLog::setMerchantName);
        return log;
    }

    public PmSensitiveOperationLog record(
        Long merchantId,
        String operationType,
        String targetType,
        Long targetId,
        String reason,
        Object requestPayload,
        String beforeSnapshot,
        Object afterSnapshot,
        StepUpVerificationMethod verificationMethod,
        String idempotencyKey
    ) {
        PmSensitiveOperationLog log = new PmSensitiveOperationLog();
        log.setId(IdWorker.getId());
        log.setMerchantId(merchantId);
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setReason(reason);
        log.setRequestPayload(json(requestPayload));
        log.setBeforeSnapshot(beforeSnapshot);
        log.setAfterSnapshot(json(afterSnapshot));
        log.setOperatedBy(currentUserId());
        log.setOperatedAt(now());
        log.setVerificationMethod(verificationMethod.name());
        log.setIdempotencyKey(idempotencyKey);
        mapper.insert(log);
        return log;
    }

    public String snapshot(Object value) {
        return json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ServiceException("敏感操作快照生成失败");
        }
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        return userId;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
