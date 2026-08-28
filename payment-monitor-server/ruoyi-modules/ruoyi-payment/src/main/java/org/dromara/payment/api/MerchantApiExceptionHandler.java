package org.dromara.payment.api;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.controller.api.MerchantOrderApiController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MerchantOrderApiController.class)
@RequiredArgsConstructor
@Slf4j
public class MerchantApiExceptionHandler {
    private final PaymentProperties properties;

    @ExceptionHandler(MerchantApiException.class)
    public ResponseEntity<MerchantApiResponse<Void>> handle(MerchantApiException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
            .body(MerchantApiResponse.failure(exception.toError(), properties));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<MerchantApiResponse<Void>> validation(Exception exception) {
        MerchantApiError error = new MerchantApiError(
            "VALIDATION_FAILED",
            validationMessage(exception),
            false,
            null);
        return ResponseEntity.badRequest().body(MerchantApiResponse.failure(error, properties));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MerchantApiResponse<Void>> unexpected(Exception exception) {
        log.error("商户订单接口处理失败", exception);
        MerchantApiError error = new MerchantApiError(
            "INTERNAL_ERROR",
            "商户订单接口处理失败",
            true,
            null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(MerchantApiResponse.failure(error, properties));
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validation
            && validation.getBindingResult().getFieldError() != null) {
            return validation.getBindingResult().getFieldError().getDefaultMessage();
        }
        return exception.getMessage() == null ? "请求参数无效" : exception.getMessage();
    }
}
