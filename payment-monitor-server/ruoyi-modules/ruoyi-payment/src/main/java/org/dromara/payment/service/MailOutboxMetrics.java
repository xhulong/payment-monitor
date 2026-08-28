package org.dromara.payment.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.dromara.payment.mapper.MailOutboxMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class MailOutboxMetrics {

    private final MailOutboxMapper mapper;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();

    public MailOutboxMetrics(
        MailOutboxMapper mapper,
        MeterRegistry registry
    ) {
        this.mapper = mapper;
        Gauge.builder(
                "payment_mail_outbox_backlog",
                backlog,
                AtomicLong::doubleValue
            )
            .description("Pending or retrying payment mail jobs")
            .register(registry);
        Gauge.builder(
                "payment_mail_outbox_dead",
                dead,
                AtomicLong::doubleValue
            )
            .description("Dead payment mail jobs")
            .register(registry);
    }

    @Scheduled(fixedDelayString = "${payment.metrics.refresh-ms:15000}")
    public void refresh() {
        backlog.set(mapper.backlogCount());
        dead.set(mapper.deadCount());
    }
}
