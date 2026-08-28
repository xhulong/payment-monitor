package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_payment_order")
public class PmPaymentOrder implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private String merchantOrderNo;
    private String platform;
    private Long qrAssetId;
    private Long requestedAmountMinor;
    private Long payableAmountMinor;
    private Integer amountOffsetMinor;
    private String currency;
    private String status;
    private String publicToken;
    private String subject;
    private String customerNote;
    private Long matchedEventId;
    private Long transactionId;
    private String confirmationStatus;
    private OffsetDateTime confirmedAt;
    private Long confirmedBy;
    private String confirmationSource;
    private String confirmationNote;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime updatedAt;
}
