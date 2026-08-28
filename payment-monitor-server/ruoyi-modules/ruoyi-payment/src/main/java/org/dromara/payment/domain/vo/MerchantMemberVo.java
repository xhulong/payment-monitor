package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class MerchantMemberVo {
    private Long userId;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String username;
    private String nickname;
    private String email;
    private String roleCode;
    private String status;
    private boolean mfaEnabled;
    private OffsetDateTime joinedAt;
}
