package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BatchStatusRequest {
    @NotEmpty(message = "请选择至少一条记录")
    private List<@NotNull(message = "记录 ID 不能为空") Long> ids;

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "0|1", message = "状态只能为启用或停用")
    private String status;
}
