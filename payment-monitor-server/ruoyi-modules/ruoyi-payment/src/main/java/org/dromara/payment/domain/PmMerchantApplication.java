package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_merchant_application", autoResultMap = true)
public class PmMerchantApplication {
    @TableId
    private Long id;
    private Long userId;
    private String verifiedEmail;
    private String merchantDisplayName;
    private String applicantName;
    private String phoneNumber;
    private String countryRegion;
    private String province;
    private String city;
    private String paymentUseCase;
    private String monthlyOrderRange;
    private String monthlyAmountRange;
    private String plannedPlatforms;
    private String agreementVersion;
    private String privacyVersion;
    private String status;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String submissionSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reviewerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime claimedAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime cooldownUntil;
    private Long merchantId;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
