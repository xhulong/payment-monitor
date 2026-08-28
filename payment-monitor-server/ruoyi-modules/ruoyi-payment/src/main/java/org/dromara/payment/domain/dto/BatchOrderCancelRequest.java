package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchOrderCancelRequest {
    @NotEmpty(message = "请选择至少一个订单")
    private List<@NotNull(message = "订单 ID 不能为空") Long> ids;

    @Size(max = 500, message = "取消原因不能超过 500 个字符")
    private String note;
}
