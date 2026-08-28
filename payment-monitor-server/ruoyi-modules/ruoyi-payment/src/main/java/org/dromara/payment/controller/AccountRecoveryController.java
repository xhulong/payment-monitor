package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.encrypt.annotation.ApiCryptoV2;
import org.dromara.payment.domain.dto.EmailChangeCodeRequest;
import org.dromara.payment.domain.dto.EmailChangeConfirmRequest;
import org.dromara.payment.domain.dto.MerchantEmailCodeRequest;
import org.dromara.payment.domain.dto.PasswordResetConfirmRequest;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.dromara.payment.service.AccountRecoveryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
public class AccountRecoveryController {
    private final AccountRecoveryService service;
    private final TrustedClientIpResolver clientIpResolver;

    @SaIgnore
    @PostMapping("/api/v1/public/accounts/password-reset/code")
    @ApiCryptoV2(request = true, response = true)
    public R<Void> passwordResetCode(
        @Valid @RequestBody MerchantEmailCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        service.sendPasswordResetCode(request, clientIpResolver.resolve(servletRequest));
        return R.ok();
    }

    @SaIgnore
    @PostMapping("/api/v1/public/accounts/password-reset/confirm")
    @ApiCryptoV2(request = true, response = true)
    public R<Void> passwordResetConfirm(
        @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        service.resetPassword(request);
        return R.ok();
    }

    @SaCheckLogin
    @PostMapping("/account/email-change/code")
    @ApiCryptoV2(request = true, response = true)
    public R<Void> emailChangeCode(
        @Valid @RequestBody EmailChangeCodeRequest request,
        @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken,
        HttpServletRequest servletRequest
    ) {
        service.sendEmailChangeCode(
            request,
            stepUpToken,
            clientIpResolver.resolve(servletRequest)
        );
        return R.ok();
    }

    @SaCheckLogin
    @PostMapping("/account/email-change/confirm")
    @ApiCryptoV2(request = true, response = true)
    public R<Void> emailChangeConfirm(
        @Valid @RequestBody EmailChangeConfirmRequest request
    ) {
        service.confirmEmailChange(request);
        return R.ok();
    }
}
