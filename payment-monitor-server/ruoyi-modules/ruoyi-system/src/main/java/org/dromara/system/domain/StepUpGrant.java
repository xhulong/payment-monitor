package org.dromara.system.domain;

import java.io.Serializable;

public record StepUpGrant(
    Long userId,
    String sessionToken,
    String operation
) implements Serializable {
}
