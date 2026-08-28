package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.PmReconciliationItem;
import org.dromara.payment.domain.PmReconciliationRun;
import org.dromara.payment.domain.dto.ReconciliationResolveRequest;
import org.dromara.payment.domain.vo.ReconciliationRunDetailVo;
import org.dromara.payment.service.PaymentReconciliationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/reconciliation")
public class PaymentReconciliationController {
    private final PaymentReconciliationService service;

    @SaCheckPermission("payment:reconciliation:list")
    @GetMapping("/runs/list")
    public R<PageResult<PmReconciliationRun>> runs(
        @RequestParam(required = false) Long merchantId,
        PageQuery pageQuery
    ) {
        return R.ok(service.queryRuns(merchantId, pageQuery));
    }

    @SaCheckPermission("payment:reconciliation:list")
    @GetMapping("/runs/{id}")
    public R<ReconciliationRunDetailVo> run(@NotNull @PathVariable Long id) {
        return R.ok(service.queryRun(id));
    }

    @SaCheckPermission("payment:reconciliation:run")
    @Log(title = "执行版本化支付对账", businessType = BusinessType.OTHER)
    @PostMapping("/runs")
    public R<PmReconciliationRun> create(
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate businessDate
    ) {
        return R.ok(service.run(merchantId, businessDate));
    }

    @SaCheckPermission("payment:reconciliation:resolve")
    @Log(title = "处理支付对账差异", businessType = BusinessType.UPDATE)
    @PostMapping("/items/{id}/resolve")
    public R<PmReconciliationItem> resolve(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody ReconciliationResolveRequest request
    ) {
        return R.ok(service.resolve(id, request));
    }
}
