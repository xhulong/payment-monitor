package org.dromara.payment.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PaymentMetricsMapper {
    @Select("select count(*) from pm_payment_event")
    long eventCount();

    @Select("select count(*) from pm_payment_order where status = 'PAID'")
    long paidOrderCount();

    @Select("""
        select coalesce(avg(extract(epoch from (o.paid_at - e.received_at)) * 1000), 0)
        from pm_payment_order o
        join pm_payment_event e on e.id = o.matched_event_id
        where o.status = 'PAID' and o.paid_at is not null
        """)
    double averageMatchLatencyMs();

    @Select("""
        select count(*) from pm_webhook_delivery_log
        where success = #{success}
        """)
    long webhookDeliveryCount(@Param("success") boolean success);

    @Select("""
        select count(*) from pm_webhook_outbox
        where status in ('PENDING', 'RETRYING', 'DELIVERING')
        """)
    long outboxBacklog();

    @Select("""
        select count(*) from pm_device
        where status = '0'
          and last_seen_at >= now() - make_interval(secs => #{thresholdSeconds})
        """)
    long onlineDevices(@Param("thresholdSeconds") int thresholdSeconds);
}
