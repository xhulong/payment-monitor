package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.domain.PmMerchantApiAudit;
import org.dromara.payment.domain.PmReconciliationRun;
import org.dromara.payment.domain.vo.PaymentHomeDashboardVo;
import org.dromara.payment.service.MerchantApiAuditService;
import org.dromara.payment.service.PaymentHomeDashboardService;
import org.dromara.payment.service.PaymentReconciliationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PaymentOperationsController {
    private final PaymentHomeDashboardService dashboardService;
    private final PaymentReconciliationService reconciliationService;
    private final MerchantApiAuditService apiAuditService;

    @SaCheckPermission("payment:dashboard:view")
    @GetMapping("/home-dashboard")
    public R<PaymentHomeDashboardVo> dashboard() {
        return R.ok(dashboardService.dashboard());
    }

    @SaCheckPermission("payment:dashboard:view")
    @GetMapping("/reconciliation/latest")
    public R<PmReconciliationRun> latestReconciliation(
        @RequestParam(required = false) Long merchantId
    ) {
        return R.ok(reconciliationService.latest(merchantId));
    }

    @SaCheckPermission("payment:dashboard:view")
    @Log(title = "执行支付对账", businessType = BusinessType.OTHER)
    @PostMapping("/reconciliation/run")
    public R<PmReconciliationRun> runReconciliation(
        @RequestParam(required = false) Long merchantId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate businessDate
    ) {
        return R.ok(reconciliationService.run(merchantId, businessDate));
    }

    @SaCheckPermission("payment:merchant:key")
    @GetMapping("/merchant-api-audits/list")
    public R<PageResult<PmMerchantApiAudit>> apiAudits(
        @RequestParam(required = false) Long merchantId,
        PageQuery pageQuery
    ) {
        return R.ok(apiAuditService.queryPage(merchantId, pageQuery));
    }
}
