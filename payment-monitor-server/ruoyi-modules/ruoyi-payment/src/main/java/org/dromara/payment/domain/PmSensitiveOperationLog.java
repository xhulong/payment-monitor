package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_sensitive_operation_log", autoResultMap = true)
public class PmSensitiveOperationLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private String operationType;
    private String targetType;
    private Long targetId;
    private String reason;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String requestPayload;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String beforeSnapshot;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String afterSnapshot;
    private Long operatedBy;
    private OffsetDateTime operatedAt;
    private String verificationMethod;
    private String idempotencyKey;
}
