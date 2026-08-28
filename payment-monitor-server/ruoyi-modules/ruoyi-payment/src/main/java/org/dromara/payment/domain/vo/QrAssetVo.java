package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmQrAsset;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = PmQrAsset.class)
public class QrAssetVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String assetCode;
    private String platform;
    private String assetName;
    private String qrContentTemplate;
    private String status;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
