package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransactionConfirmRequest {
    @Size(max = 500)
    private String note;
}
