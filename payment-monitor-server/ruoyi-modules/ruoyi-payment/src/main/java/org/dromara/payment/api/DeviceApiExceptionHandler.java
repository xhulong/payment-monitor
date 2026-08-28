package org.dromara.payment.api;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.controller.api.DeviceApiController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts all controller errors under the device API into the versioned envelope.
 */
@RestControllerAdvice(assignableTypes = DeviceApiController.class)
@RequiredArgsConstructor
public class DeviceApiExceptionHandler {

    private final PaymentProperties properties;

    @ExceptionHandler(DeviceApiException.class)
    public ResponseEntity<DeviceApiResponse<Void>> handleDeviceApiException(DeviceApiException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
            .body(DeviceApiResponse.failure(exception.toError(), properties));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<DeviceApiResponse<Void>> handleValidation(Exception exception) {
        DeviceApiError error = new DeviceApiError(
            "VALIDATION_FAILED", validationMessage(exception), false, false, null);
        return ResponseEntity.badRequest().body(DeviceApiResponse.failure(error, properties));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DeviceApiResponse<Void>> handleUnexpected(Exception exception) {
        DeviceApiError error = new DeviceApiError(
            "INTERNAL_ERROR", "设备接口处理失败", true, false, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(DeviceApiResponse.failure(error, properties));
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validation = (MethodArgumentNotValidException) exception;
            if (validation.getBindingResult().getFieldError() == null) {
                return "请求参数无效";
            }
            return validation.getBindingResult().getFieldError().getDefaultMessage();
        }
        return exception.getMessage() == null ? "请求参数无效" : exception.getMessage();
    }
}
