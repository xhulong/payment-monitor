package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.PmSensitiveOperationLog;
import org.dromara.payment.domain.bo.SensitiveOperationQueryBo;
import org.dromara.payment.service.SensitiveOperationLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/sensitive-operations")
public class SensitiveOperationController {
    private final SensitiveOperationLogService service;

    @SaCheckPermission("payment:sensitive-operation:list")
    @GetMapping("/list")
    public R<PageResult<PmSensitiveOperationLog>> list(
        SensitiveOperationQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(service.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:sensitive-operation:list")
    @GetMapping("/{id}")
    public R<PmSensitiveOperationLog> get(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }
}
