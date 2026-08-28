package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MailTestRequest(
    @NotBlank @Email @Size(max = 255) String recipient
) {
}
