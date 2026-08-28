package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ManualOrderMatchRequest {
    @NotNull
    private Long eventId;
    private boolean force;
    @Size(max = 500)
    private String note;
}
