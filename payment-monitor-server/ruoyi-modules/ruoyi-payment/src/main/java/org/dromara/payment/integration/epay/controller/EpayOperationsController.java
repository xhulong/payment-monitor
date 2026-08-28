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
import org.dromara.payment.integration.epay.application.EpayOperationsService;
import org.dromara.payment.integration.epay.domain.bo.ExternalOrderQueryBo;
import org.dromara.payment.integration.epay.domain.bo.ProtocolCallbackQueryBo;
import org.dromara.payment.integration.epay.domain.dto.ProtocolCallbackReplayRequest;
import org.dromara.payment.integration.epay.domain.vo.ExternalOrderVo;
import org.dromara.payment.integration.epay.domain.vo.ProtocolCallbackVo;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class EpayOperationsController {
    private final EpayOperationsService service;
    private final AccountMfaService mfaService;

    @SaCheckPermission("payment:external-order:list")
    @GetMapping("/external-orders/list")
    public R<PageResult<ExternalOrderVo>> externalOrders(ExternalOrderQueryBo bo, PageQuery pageQuery) {
        return R.ok(service.externalOrders(bo, pageQuery));
    }

    @SaCheckPermission("payment:external-order:list")
    @GetMapping("/external-orders/{id}")
    public R<ExternalOrderVo> externalOrder(@NotNull @PathVariable Long id) {
        return R.ok(service.externalOrder(id));
    }

    @SaCheckPermission("payment:protocol-callback:list")
    @GetMapping("/protocol-callbacks/list")
    public R<PageResult<ProtocolCallbackVo>> callbacks(ProtocolCallbackQueryBo bo, PageQuery pageQuery) {
        return R.ok(service.callbacks(bo, pageQuery));
    }

    @SaCheckPermission("payment:protocol-callback:list")
    @GetMapping("/protocol-callbacks/{id}")
    public R<ProtocolCallbackVo> callback(@NotNull @PathVariable Long id) {
        return R.ok(service.callback(id));
    }

    @SaCheckPermission("payment:protocol-callback:retry")
    @Log(title = "重试易支付回调", businessType = BusinessType.UPDATE)
    @PutMapping("/protocol-callbacks/{id}/retry")
    public R<ProtocolCallbackVo> retry(
        @NotNull @PathVariable Long id,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, "EPAY_INTEGRATION_WRITE");
        return R.ok(service.retry(id));
    }

    @SaCheckPermission("payment:protocol-callback:retry")
    @Log(title = "重放易支付回调", businessType = BusinessType.OTHER)
    @PostMapping("/protocol-callbacks/{id}/replay")
    public R<ProtocolCallbackVo> replay(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody ProtocolCallbackReplayRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String token
    ) {
        mfaService.requireStepUp(token, "EPAY_INTEGRATION_WRITE");
        return R.ok(service.replay(id, request.getReason()));
    }
}