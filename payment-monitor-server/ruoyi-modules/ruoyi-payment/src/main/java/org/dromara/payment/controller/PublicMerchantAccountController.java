package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.dto.MerchantAccountRegisterRequest;
import org.dromara.payment.domain.dto.MerchantEmailCodeRequest;
import org.dromara.payment.domain.vo.MerchantRegistrationVo;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.dromara.payment.service.MerchantAccountService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@SaIgnore
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/merchant-accounts")
public class PublicMerchantAccountController {
    private final MerchantAccountService service;
    private final TrustedClientIpResolver clientIpResolver;

    @PostMapping("/email-code")
    public R<Void> emailCode(
        @Valid @RequestBody MerchantEmailCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        service.sendEmailCode(request, clientIpResolver.resolve(servletRequest));
        return R.ok();
    }

    @PostMapping("/register")
    public R<MerchantRegistrationVo> register(
        @Valid @RequestBody MerchantAccountRegisterRequest request
    ) {
        return R.ok(service.register(request));
    }
}
