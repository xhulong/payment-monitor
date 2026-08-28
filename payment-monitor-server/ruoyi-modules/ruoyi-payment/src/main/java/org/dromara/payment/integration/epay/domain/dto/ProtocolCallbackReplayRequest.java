package org.dromara.payment.integration.epay.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProtocolCallbackReplayRequest {
    @NotBlank @Size(max = 500)
    private String reason;
}
