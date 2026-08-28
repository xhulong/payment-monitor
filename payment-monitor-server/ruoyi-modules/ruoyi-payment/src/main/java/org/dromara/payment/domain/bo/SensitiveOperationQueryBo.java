package org.dromara.payment.domain.bo;

import lombok.Data;

@Data
public class SensitiveOperationQueryBo {
    private Long merchantId;
    private String operationType;
    private String targetType;
    private Long targetId;
}
