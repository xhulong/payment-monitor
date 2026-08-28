package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_webhook_endpoint")
public class PmWebhookEndpoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private String endpointName;
    private String endpointUrl;
    private String secretCiphertext;
    private String status;
    private String eventTypes;
    private String platformFilter;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
