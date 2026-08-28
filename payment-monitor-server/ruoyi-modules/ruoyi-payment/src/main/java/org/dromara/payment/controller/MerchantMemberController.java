package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.dto.BatchIdsRequest;
import org.dromara.payment.domain.dto.BatchStatusRequest;
import org.dromara.payment.domain.dto.MerchantInvitationCreateRequest;
import org.dromara.payment.domain.dto.MerchantMemberUpdateRequest;
import org.dromara.payment.domain.vo.MerchantInvitationVo;
import org.dromara.payment.domain.vo.MerchantMemberVo;
import org.dromara.payment.service.MerchantMemberService;
import org.dromara.payment.service.AccountMfaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/merchant")
public class MerchantMemberController {
    private final MerchantMemberService service;
    private final AccountMfaService mfaService;

    @GetMapping("/members")
    @SaCheckPermission("payment:merchant-member:list")
    public R<List<MerchantMemberVo>> members(
        @RequestParam(required = false) Long merchantId
    ) {
        return R.ok(service.list(merchantId));
    }

    @GetMapping("/invitations")
    @SaCheckPermission("payment:merchant-member:list")
    public R<List<MerchantInvitationVo>> invitations(
        @RequestParam(required = false) Long merchantId
    ) {
        return R.ok(service.invitations(merchantId));
    }

    @PostMapping("/invitations")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<MerchantInvitationVo> invite(
        @Valid @RequestBody MerchantInvitationCreateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        return R.ok(service.invite(request));
    }

    @PutMapping("/members/{userId}/role")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<MerchantMemberVo> update(
        @NotNull @PathVariable Long userId,
        @Valid @RequestBody MerchantMemberUpdateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        return R.ok(service.update(userId, request));
    }

    @PutMapping("/members/{userId}/status")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<MerchantMemberVo> status(
        @NotNull @PathVariable Long userId,
        @RequestBody MerchantMemberUpdateRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        return R.ok(service.update(userId, request));
    }

    @DeleteMapping("/members/{userId}")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<Void> remove(
        @NotNull @PathVariable Long userId,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        service.remove(userId);
        return R.ok();
    }

    @PutMapping("/members/batch-status")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<Void> batchStatus(
        @Valid @RequestBody BatchStatusRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        service.updateStatus(request.getIds(), request.getStatus());
        return R.ok();
    }

    @DeleteMapping("/members")
    @SaCheckPermission("payment:merchant-member:edit")
    public R<Void> batchRemove(
        @Valid @RequestBody BatchIdsRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken
    ) {
        mfaService.requireStepUp(stepUpToken, "MERCHANT_MEMBER_MANAGE");
        service.remove(request.getIds());
        return R.ok();
    }
}
