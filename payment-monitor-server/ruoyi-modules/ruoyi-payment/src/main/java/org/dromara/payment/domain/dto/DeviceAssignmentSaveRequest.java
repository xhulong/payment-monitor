package org.dromara.payment.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeviceAssignmentSaveRequest {
    private Long merchantId;
    @NotEmpty
    @Valid
    private List<Item> assignments = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private Long deviceId;
        @NotNull
        @Pattern(regexp = "WECHAT|ALIPAY")
        private String platform;
        @NotNull
        @Pattern(regexp = "PRIMARY|BACKUP")
        private String role;
        @NotNull
        @Positive
        private Integer priority;
        private Boolean enabled = true;
    }
}
