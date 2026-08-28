package org.dromara.payment.domain.bo;

import lombok.Data;

@Data
public class MerchantQueryBo {
    private Long merchantId;
    private String merchantCode;
    private String name;
    private String status;
}
