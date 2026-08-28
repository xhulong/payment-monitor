package org.dromara.payment.domain.vo;

import lombok.Data;

@Data
public class MerchantAdminBindingVo {
    private Long merchantId;
    private Long userId;
    private String userName;
}
