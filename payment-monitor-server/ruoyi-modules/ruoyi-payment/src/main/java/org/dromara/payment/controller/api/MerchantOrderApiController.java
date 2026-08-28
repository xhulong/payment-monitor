package org.dromara.payment.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.api.MerchantApiResponse;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.dto.MerchantOrderCreateRequest;
import org.dromara.payment.domain.vo.MerchantOrderVo;
import org.dromara.payment.service.PaymentOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderApiController {
    private final PaymentOrderService orderService;
    private final PaymentProperties properties;

    @PostMapping
    public MerchantApiResponse<MerchantOrderVo> create(
        @Valid @RequestBody MerchantOrderCreateRequest request,
        HttpServletRequest servletRequest
    ) {
        return MerchantApiResponse.success(
            orderService.createForMerchant(attribute(servletRequest), request),
            properties);
    }

    @GetMapping("/{merchantOrderNo}")
    public MerchantApiResponse<MerchantOrderVo> get(
        @NotBlank @PathVariable String merchantOrderNo,
        HttpServletRequest servletRequest
    ) {
        return MerchantApiResponse.success(
            orderService.queryForMerchant(attribute(servletRequest), merchantOrderNo),
            properties);
    }

    @PutMapping("/{merchantOrderNo}/cancel")
    public MerchantApiResponse<MerchantOrderVo> cancel(
        @NotBlank @PathVariable String merchantOrderNo,
        HttpServletRequest servletRequest
    ) {
        return MerchantApiResponse.success(
            orderService.cancelForMerchant(attribute(servletRequest), merchantOrderNo),
            properties);
    }

    private Long attribute(HttpServletRequest request) {
        Object value = request.getAttribute(PaymentConstants.MERCHANT_ID_ATTRIBUTE);
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    }
}
