package org.dromara.payment.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 支付事件批量上传请求。
 */
@Data
public class PaymentEventBatchRequest {
    private OffsetDateTime sentAt;
    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<PaymentEventItem> events;
}
