package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.dromara.payment.domain.PmReconciliationItem;
import org.dromara.payment.domain.PmReconciliationRun;

import java.util.List;

@Data
@AllArgsConstructor
public class ReconciliationRunDetailVo {
    private PmReconciliationRun run;
    private List<PmReconciliationItem> items;
}
