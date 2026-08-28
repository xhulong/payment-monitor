package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchIdsRequest {
    @NotEmpty(message = "请选择至少一条记录")
    private List<@NotNull(message = "记录 ID 不能为空") Long> ids;
}
