package org.dromara.payment.integration.epay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.payment.integration.epay.application.PaymentIntegrationService;
import org.dromara.payment.integration.epay.domain.bo.PaymentIntegrationQueryBo;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationRouteSaveRequest;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationSaveRequest;
import org.dromara.payment.integration.epay.domain.dto.PaymentIntegrationStatusRequest;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationRouteVo;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationSecretVo;
import org.dromara.payment.integration.epay.domain.vo.PaymentIntegrationVo;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/integrations")
public class PaymentIntegrationController {
    private static final String MFA_OPERATION = "EPAY_INTEGRATION_WRITE";
    private final PaymentIntegrationService service;
    private final AccountMfaService mfaService;

    @SaCheckPermission("payment:integration:list")
    @GetMapping
    public R<PageResult<PaymentIntegrationVo>> list(PaymentIntegrationQueryBo bo, PageQuery pageQuery) {
        return R.ok(service.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:integration:list")
    @GetMapping("/{id}")
    public R<PaymentIntegrationVo> get(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    @SaCheckPermission("payment:integration:add")
    @Log(title = "新增易支付接入", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PaymentIntegrationSecretVo> create(
        @Valid @RequestBody PaymentIntegrationSaveRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.create(request));
    }

    @SaCheckPermission("payment:integration:edit")
    @Log(title = "修改易支付接入", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<PaymentIntegrationVo> update(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody PaymentIntegrationSaveRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.update(id, request));
    }

    @SaCheckPermission("payment:integration:edit")
    @PutMapping("/{id}/status")
    public R<PaymentIntegrationVo> status(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody PaymentIntegrationStatusRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.updateStatus(id, request.getStatus()));
    }

    @SaCheckPermission("payment:integration:secret")
    @PostMapping("/{id}/secrets/rotate")
    public R<PaymentIntegrationSecretVo> rotate(
        @NotNull @PathVariable Long id,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.rotateSecret(id));
    }

    @SaCheckPermission("payment:integration:secret")
    @PutMapping("/{id}/secrets/{secretId}/revoke")
    public R<PaymentIntegrationVo> revoke(
        @NotNull @PathVariable Long id,
        @NotNull @PathVariable Long secretId,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.revokeSecret(id, secretId));
    }

    @SaCheckPermission("payment:integration:list")
    @GetMapping("/{id}/routes")
    public R<List<PaymentIntegrationRouteVo>> routes(@NotNull @PathVariable Long id) {
        return R.ok(service.routes(id));
    }

    @SaCheckPermission("payment:integration:route")
    @PutMapping("/{id}/routes")
    public R<List<PaymentIntegrationRouteVo>> saveRoutes(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody PaymentIntegrationRouteSaveRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, MFA_OPERATION);
        return R.ok(service.saveRoutes(id, request));
    }
}

