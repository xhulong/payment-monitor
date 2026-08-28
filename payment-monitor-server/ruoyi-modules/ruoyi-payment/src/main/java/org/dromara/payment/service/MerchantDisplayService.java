package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.mapper.MerchantMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantDisplayService {
    private final MerchantMapper merchantMapper;

    public <T> void enrich(
        Collection<T> rows,
        Function<T, Long> merchantIdGetter,
        BiConsumer<T, String> merchantCodeSetter,
        BiConsumer<T, String> merchantNameSetter
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        var merchantIds = rows.stream()
            .map(merchantIdGetter)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, PmMerchant> merchants = merchantIds.isEmpty()
            ? Collections.emptyMap()
            : merchantMapper.selectBatchIds(merchantIds).stream()
                .collect(Collectors.toMap(PmMerchant::getId, Function.identity()));
        rows.forEach(row -> {
            PmMerchant merchant = merchants.get(merchantIdGetter.apply(row));
            if (merchant != null) {
                merchantCodeSetter.accept(row, merchant.getMerchantCode());
                merchantNameSetter.accept(row, merchant.getName());
            }
        });
    }
}
