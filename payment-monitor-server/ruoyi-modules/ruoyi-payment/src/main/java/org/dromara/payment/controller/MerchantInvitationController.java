package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.vo.MerchantMemberVo;
import org.dromara.payment.service.MerchantMemberService;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/merchant-invitations")
public class MerchantInvitationController {
    private final MerchantMemberService service;

    @PostMapping("/{token}/accept")
    public R<MerchantMemberVo> accept(@NotBlank @PathVariable String token) {
        return R.ok(service.accept(token));
    }
}
