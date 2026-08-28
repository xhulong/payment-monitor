package org.dromara.payment.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.api.DeviceApiResponse;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.dto.HeartbeatRequest;
import org.dromara.payment.domain.dto.PairDeviceRequest;
import org.dromara.payment.domain.dto.PaymentEventBatchRequest;
import org.dromara.payment.domain.vo.PairDeviceVo;
import org.dromara.payment.domain.vo.DeviceConfigVo;
import org.dromara.payment.domain.vo.PaymentEventBatchVo;
import org.dromara.payment.service.PaymentDeviceService;
import org.dromara.payment.service.PaymentEventService;
import org.dromara.payment.service.DeviceAssignmentService;
import org.dromara.payment.service.AppReleaseService;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Android 监控设备 API。
 */
@Validated
@RestController
@RequiredArgsConstructor
public class DeviceApiController {

    private final PaymentDeviceService deviceService;
    private final PaymentEventService eventService;
    private final PaymentProperties properties;
    private final DeviceAssignmentService assignmentService;
    private final AppReleaseService appReleaseService;
    private final TrustedClientIpResolver clientIpResolver;

    @PostMapping("/api/v1/devices/pair")
    public DeviceApiResponse<PairDeviceVo> pair(
        @Valid @RequestBody PairDeviceRequest request,
        HttpServletRequest servletRequest
    ) {
        return DeviceApiResponse.success(
            deviceService.pair(request, clientIpResolver.resolve(servletRequest)), properties);
    }

    @PostMapping("/api/v1/device/heartbeat")
    public DeviceApiResponse<DeviceConfigVo> heartbeat(@Valid @RequestBody HeartbeatRequest request,
                                                       HttpServletRequest servletRequest) {
        Long deviceId = attribute(servletRequest, PaymentConstants.DEVICE_ID_ATTRIBUTE);
        Long merchantId = attribute(servletRequest, PaymentConstants.MERCHANT_ID_ATTRIBUTE);
        deviceService.heartbeat(deviceId, request, clientIpResolver.resolve(servletRequest));
        var assignment = assignmentService.infoForDevice(merchantId, deviceId);
        return DeviceApiResponse.success(
            appReleaseService.decorate(
                DeviceConfigVo.from(properties, assignment.role(), assignment.platformScope())),
            properties);
    }

    @GetMapping("/api/v1/device/config")
    public DeviceApiResponse<DeviceConfigVo> config(HttpServletRequest servletRequest) {
        Long deviceId = attribute(servletRequest, PaymentConstants.DEVICE_ID_ATTRIBUTE);
        Long merchantId = attribute(servletRequest, PaymentConstants.MERCHANT_ID_ATTRIBUTE);
        var assignment = assignmentService.infoForDevice(merchantId, deviceId);
        return DeviceApiResponse.success(
            appReleaseService.decorate(
                DeviceConfigVo.from(properties, assignment.role(), assignment.platformScope())),
            properties);
    }

    @PostMapping("/api/v1/payment-events/batch")
    public DeviceApiResponse<PaymentEventBatchVo> upload(
        @Valid @RequestBody PaymentEventBatchRequest request,
        HttpServletRequest servletRequest) {
        Long deviceId = attribute(servletRequest, PaymentConstants.DEVICE_ID_ATTRIBUTE);
        Long merchantId = attribute(servletRequest, PaymentConstants.MERCHANT_ID_ATTRIBUTE);
        return DeviceApiResponse.success(eventService.ingest(merchantId, deviceId, request), properties);
    }

    private Long attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    }
}
