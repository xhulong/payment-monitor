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
import org.dromara.payment.domain.bo.MerchantQueryBo;
import org.dromara.payment.domain.dto.MerchantApiKeyCreateRequest;
import org.dromara.payment.domain.dto.MerchantBindUserRequest;
import org.dromara.payment.domain.dto.MerchantSaveRequest;
import org.dromara.payment.domain.vo.MerchantApiKeySecretVo;
import org.dromara.payment.domain.vo.MerchantApiKeyVo;
import org.dromara.payment.domain.vo.MerchantContextVo;
import org.dromara.payment.domain.vo.MerchantVo;
import org.dromara.payment.service.MerchantApiKeyService;
import org.dromara.payment.service.MerchantService;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class MerchantController {
    private final MerchantService merchantService;
    private final MerchantApiKeyService apiKeyService;
    private final AccountMfaService mfaService;

    @GetMapping("/merchant-context")
    public R<MerchantContextVo> context() {
        return R.ok(merchantService.currentContext());
    }

    @SaCheckPermission("payment:merchant:list")
    @GetMapping("/merchants/list")
    public R<PageResult<MerchantVo>> list(MerchantQueryBo bo, PageQuery pageQuery) {
        return R.ok(merchantService.queryPage(bo, pageQuery));
    }

    @SaCheckPermission("payment:merchant:list")
    @GetMapping("/merchants/options")
    public R<List<MerchantVo>> options(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer limit
    ) {
        return R.ok(merchantService.options(keyword, status, limit));
    }

    @SaCheckPermission("payment:merchant:list")
    @GetMapping("/merchants/{id}")
    public R<MerchantVo> get(@NotNull @PathVariable Long id) {
        return R.ok(merchantService.queryById(id));
    }

    @SaCheckPermission("payment:merchant:add")
    @Log(title = "创建支付商户", businessType = BusinessType.INSERT)
    @PostMapping("/merchants")
    public R<MerchantVo> create(@Valid @RequestBody MerchantSaveRequest request) {
        return R.ok(merchantService.create(request));
    }

    @SaCheckPermission("payment:merchant:edit")
    @Log(title = "修改支付商户", businessType = BusinessType.UPDATE)
    @PutMapping("/merchants/{id}")
    public R<MerchantVo> update(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantSaveRequest request
    ) {
        return R.ok(merchantService.update(id, request));
    }

    @SaCheckPermission("payment:merchant:bind")
    @Log(title = "绑定商户管理员", businessType = BusinessType.GRANT)
    @PutMapping("/merchants/{id}/admin-user")
    public R<MerchantVo> bindUser(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantBindUserRequest request
    ) {
        return R.ok(merchantService.bindUser(id, request.getUserId()));
    }

    @SaCheckPermission("payment:merchant:key")
    @GetMapping("/merchants/{id}/api-keys")
    public R<List<MerchantApiKeyVo>> apiKeys(@NotNull @PathVariable Long id) {
        return R.ok(apiKeyService.list(id));
    }

    @SaCheckPermission("payment:merchant:key")
    @Log(title = "创建商户 API Key", businessType = BusinessType.INSERT)
    @PostMapping("/merchants/{id}/api-keys")
    public R<MerchantApiKeySecretVo> createApiKey(
        @NotNull @PathVariable Long id,
        @Valid @RequestBody MerchantApiKeyCreateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "API_KEY_WRITE");
        return R.ok(apiKeyService.create(id, request));
    }

    @SaCheckPermission("payment:merchant:key")
    @Log(title = "轮换商户 API Key", businessType = BusinessType.UPDATE)
    @PutMapping("/merchants/{id}/api-keys/{keyId}/rotate")
    public R<MerchantApiKeySecretVo> rotateApiKey(
        @NotNull @PathVariable Long id,
        @NotNull @PathVariable Long keyId,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "API_KEY_WRITE");
        return R.ok(apiKeyService.rotate(id, keyId));
    }

    @SaCheckPermission("payment:merchant:key")
    @Log(title = "撤销商户 API Key", businessType = BusinessType.UPDATE)
    @PutMapping("/merchants/{id}/api-keys/{keyId}/revoke")
    public R<MerchantApiKeyVo> revokeApiKey(
        @NotNull @PathVariable Long id,
        @NotNull @PathVariable Long keyId,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "API_KEY_WRITE");
        return R.ok(apiKeyService.revoke(id, keyId));
    }
}
