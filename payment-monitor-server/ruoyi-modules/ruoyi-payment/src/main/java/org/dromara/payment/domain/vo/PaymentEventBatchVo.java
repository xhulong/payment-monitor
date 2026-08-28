package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量上传确认结果。
 */
@Data
public class PaymentEventBatchVo {
    private List<String> accepted = new ArrayList<>();
    private List<String> duplicates = new ArrayList<>();
    private List<RejectedEvent> rejected = new ArrayList<>();
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectedEvent {
        private String clientEventId;
        private String code;
        private String message;
    }
}
