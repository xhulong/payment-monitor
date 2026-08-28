package org.dromara.web.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dromara.system.api.model.LoginUser;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMfaChallenge implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private LoginUser loginUser;
    private String clientId;
}
