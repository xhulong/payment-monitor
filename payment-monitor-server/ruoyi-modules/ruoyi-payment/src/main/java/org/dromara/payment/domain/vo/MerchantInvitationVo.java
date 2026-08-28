package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class MerchantInvitationVo {
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String email;
    private String roleCode;
    private String status;
    private OffsetDateTime expiresAt;
    private String acceptanceToken;
}
