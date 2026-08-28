package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantApplicationReviewRequest {
    @Size(max = 1000)
    private String note;

    public String requiredNote() {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("审核意见不能为空");
        }
        return note.trim();
    }
}
