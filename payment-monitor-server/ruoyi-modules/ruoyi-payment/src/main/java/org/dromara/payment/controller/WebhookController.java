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
import org.dromara.payment.domain.bo.WebhookEndpointQueryBo;
import org.dromara.payment.domain.bo.WebhookOutboxQueryBo;
import org.dromara.payment.domain.dto.BatchIdsRequest;
import org.dromara.payment.domain.dto.BatchStatusRequest;
import org.dromara.payment.domain.dto.WebhookEndpointSaveRequest;
import org.dromara.payment.domain.dto.WebhookReplayRequest;
import org.dromara.payment.domain.dto.WebhookResolutionRequest;
import org.dromara.payment.domain.vo.WebhookEndpointSecretVo;
import org.dromara.payment.domain.vo.WebhookEndpointVo;
import org.dromara.payment.domain.vo.WebhookOutboxVo;
import org.dromara.payment.service.WebhookEndpointService;
import org.dromara.payment.service.WebhookOutboxService;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/webhooks")
public class WebhookController {

    private final WebhookEndpointService endpointService;
    private final WebhookOutboxService outboxService;
    private final AccountMfaService mfaService;

    @SaCheckPermission("payment:webhook:list")
    @GetMapping("/endpoints/list")
    public R<PageResult<WebhookEndpointVo>> endpointList(
        WebhookEndpointQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(endpointService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:webhook:list")
    @GetMapping("/endpoints/{id}")
    public R<WebhookEndpointVo> endpoint(@NotNull @PathVariable Long id) {
        return R.ok(endpointService.queryById(id));
    }

    @SaCheckPermission("payment:webhook:add")
    @Log(title = "新增 Webhook 端点", businessType = BusinessType.INSERT)
    @PostMapping("/endpoints")
    public R<WebhookEndpointSecretVo> createEndpoint(
        @Valid @RequestBody WebhookEndpointSaveRequest request
    ) {
        return R.ok(endpointService.create(request));
    }

    @SaCheckPermission("payment:webhook:edit")
    @Log(title = "修改 Webhook 端点", businessType = BusinessType.UPDATE)
    @PutMapping("/endpoints/{id}")
    public R<WebhookEndpointVo> updateEndpoint(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody WebhookEndpointSaveRequest request
    ) {
        return R.ok(endpointService.update(id, request));
    }

    @SaCheckPermission("payment:webhook:edit")
    @Log(title = "批量修改 Webhook 状态", businessType = BusinessType.UPDATE)
    @PutMapping("/endpoints/batch-status")
    public R<Void> batchEndpointStatus(@Valid @RequestBody BatchStatusRequest request) {
        endpointService.updateStatus(request.getIds(), request.getStatus());
        return R.ok();
    }

    @SaCheckPermission("payment:webhook:edit")
    @Log(title = "删除未使用 Webhook 端点", businessType = BusinessType.DELETE)
    @DeleteMapping("/endpoints")
    public R<Void> deleteEndpoints(@Valid @RequestBody BatchIdsRequest request) {
        endpointService.deleteUnused(request.getIds());
        return R.ok();
    }

    @SaCheckPermission("payment:webhook:edit")
    @Log(title = "轮换 Webhook 密钥", businessType = BusinessType.UPDATE)
    @PutMapping("/endpoints/{id}/rotate-secret")
    public R<WebhookEndpointSecretVo> rotateSecret(
        @NotNull @PathVariable Long id,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "WEBHOOK_SECRET_WRITE");
        return R.ok(endpointService.rotateSecret(id));
    }

    @SaCheckPermission("payment:webhook:edit")
    @Log(title = "测试 Webhook 端点", businessType = BusinessType.OTHER)
    @PostMapping("/endpoints/{id}/test")
    public R<WebhookOutboxVo> testEndpoint(@NotNull @PathVariable Long id) {
        return R.ok(outboxService.testEndpoint(id));
    }

    @SaCheckPermission("payment:webhook:list")
    @GetMapping("/outbox/list")
    public R<PageResult<WebhookOutboxVo>> outboxList(
        WebhookOutboxQueryBo bo,
        PageQuery pageQuery
    ) {
        return R.ok(outboxService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:webhook:list")
    @GetMapping("/outbox/{id}")
    public R<WebhookOutboxVo> outbox(@NotNull @PathVariable Long id) {
        return R.ok(outboxService.queryById(id));
    }

    @SaCheckPermission("payment:webhook:retry")
    @Log(title = "重试 Webhook 投递", businessType = BusinessType.UPDATE)
    @PutMapping("/outbox/{id}/retry")
    public R<WebhookOutboxVo> retry(@NotNull @PathVariable Long id) {
        return R.ok(outboxService.retry(id));
    }

    @SaCheckPermission("payment:webhook:retry")
    @Log(title = "批量重试 Webhook", businessType = BusinessType.UPDATE)
    @PutMapping("/outbox/batch-retry")
    public R<Void> batchRetry(@Valid @RequestBody BatchIdsRequest request) {
        outboxService.retry(request.getIds());
        return R.ok();
    }

    @SaCheckPermission("payment:webhook:retry")
    @Log(title = "重放 Webhook 投递", businessType = BusinessType.OTHER)
    @PostMapping("/outbox/{id}/replay")
    public R<WebhookOutboxVo> replay(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody WebhookReplayRequest request
    ) {
        return R.ok(outboxService.replay(id, request.getReason()));
    }

    @SaCheckPermission("payment:webhook:retry")
    @Log(title = "处理 DEAD Webhook", businessType = BusinessType.UPDATE)
    @PutMapping("/outbox/{id}/resolve")
    public R<WebhookOutboxVo> resolve(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody WebhookResolutionRequest request
    ) {
        return R.ok(outboxService.resolve(id, request));
    }
}
