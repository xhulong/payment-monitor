package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_merchant_application_history", autoResultMap = true)
public class PmMerchantApplicationHistory {
    @TableId
    private Long id;
    private Long applicationId;
    private Long userId;
    private String action;
    private String fromStatus;
    private String toStatus;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String snapshot;
    private String note;
    private Long operatedBy;
    private OffsetDateTime operatedAt;
}
