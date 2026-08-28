package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchEventReviewRequest {
    @NotEmpty(message = "请选择至少一个支付事件")
    private List<@NotNull(message = "支付事件 ID 不能为空") Long> ids;

    @NotBlank(message = "处理动作不能为空")
    @Pattern(regexp = "REVIEW|IGNORE", message = "批量处理只支持确认或忽略")
    private String action;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}
