package org.dromara.payment.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

@Data
@ExcelIgnoreUnannotated
public class PaymentEventExportVo {
    private Long merchantId;
    @ExcelProperty("商户编码")
    private String merchantCode;
    @ExcelProperty("商户名称")
    private String merchantName;
    @ExcelProperty("事件ID")
    private Long id;
    @ExcelProperty("设备ID")
    private Long deviceId;
    @ExcelProperty("平台")
    private String platform;
    @ExcelProperty("方向")
    private String direction;
    @ExcelProperty("金额（分）")
    private Long amountMinor;
    @ExcelProperty("币种")
    private String currency;
    @ExcelProperty("解析状态")
    private String parseStatus;
    @ExcelProperty("业务状态")
    private String status;
    @ExcelProperty("通知时间")
    private String eventTime;
    @ExcelProperty("服务端接收时间")
    private String receivedAt;
    @ExcelProperty("同步延迟（毫秒）")
    private Long syncLatencyMs;
    @ExcelProperty("匹配规则")
    private String matchedRule;
    @ExcelProperty("审核时间")
    private String reviewedAt;
    @ExcelProperty("审核人ID")
    private Long reviewedBy;
    @ExcelProperty("审核备注")
    private String reviewNote;
}
