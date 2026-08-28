package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("pm_reconciliation_run")
public class PmReconciliationRun {
    @TableId
    private Long id;
    private Long merchantId;
    @TableField(exist = false)
    private String merchantCode;
    @TableField(exist = false)
    private String merchantName;
    private String runNo;
    private LocalDate businessDate;
    private String timezone;
    private String status;
    private Long paidOrderCount;
    private Long paidOrderAmountMinor;
    private Long matchedIncomeCount;
    private Long matchedIncomeAmountMinor;
    private Long unmatchedIncomeCount;
    private Long unmatchedIncomeAmountMinor;
    private Long conflictOrderCount;
    private Long suspectedDuplicateCount;
    private Long webhookDeadCount;
    private Long amountDifferenceMinor;
    private Long openDifferenceCount;
    private Long resolvedDifferenceCount;
    private Integer version;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
}
