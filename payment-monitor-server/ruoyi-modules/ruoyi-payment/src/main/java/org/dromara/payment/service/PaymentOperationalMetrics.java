package org.dromara.payment.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.mapper.PaymentMetricsMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class PaymentOperationalMetrics {
    private final PaymentMetricsMapper metricsMapper;
    private final PaymentProperties properties;
    private final AtomicLong events = new AtomicLong();
    private final AtomicLong paidOrders = new AtomicLong();
    private final AtomicLong matchLatencyMs = new AtomicLong();
    private final AtomicLong webhookSuccess = new AtomicLong();
    private final AtomicLong webhookFailure = new AtomicLong();
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong onlineDevices = new AtomicLong();

    public PaymentOperationalMetrics(
        PaymentMetricsMapper metricsMapper,
        PaymentProperties properties,
        MeterRegistry registry
    ) {
        this.metricsMapper = metricsMapper;
        this.properties = properties;
        Gauge.builder("payment_events_ingested", events, AtomicLong::doubleValue)
            .description("Payment events stored in the database")
            .register(registry);
        Gauge.builder("payment_orders_paid", paidOrders, AtomicLong::doubleValue)
            .description("Paid payment orders")
            .register(registry);
        Gauge.builder("payment_order_match_latency_ms", matchLatencyMs, AtomicLong::doubleValue)
            .description("Average order matching latency in milliseconds")
            .register(registry);
        Gauge.builder("payment_webhook_deliveries_success", webhookSuccess, AtomicLong::doubleValue)
            .register(registry);
        Gauge.builder("payment_webhook_deliveries_failure", webhookFailure, AtomicLong::doubleValue)
            .register(registry);
        Gauge.builder("payment_outbox_backlog", outboxBacklog, AtomicLong::doubleValue)
            .register(registry);
        Gauge.builder("payment_online_devices", onlineDevices, AtomicLong::doubleValue)
            .register(registry);
    }

    @Scheduled(fixedDelayString = "${payment.metrics.refresh-ms:15000}")
    public void refresh() {
        events.set(metricsMapper.eventCount());
        paidOrders.set(metricsMapper.paidOrderCount());
        matchLatencyMs.set(Math.max(0, Math.round(metricsMapper.averageMatchLatencyMs())));
        webhookSuccess.set(metricsMapper.webhookDeliveryCount(true));
        webhookFailure.set(metricsMapper.webhookDeliveryCount(false));
        outboxBacklog.set(metricsMapper.outboxBacklog());
        onlineDevices.set(metricsMapper.onlineDevices(
            properties.getHeartbeat().getOnlineThresholdSeconds()));
    }
}
