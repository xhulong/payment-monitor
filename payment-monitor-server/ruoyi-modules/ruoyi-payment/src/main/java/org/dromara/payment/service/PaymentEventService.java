package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.api.DeviceApiException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentEventReview;
import org.dromara.payment.domain.bo.PaymentEventQueryBo;
import org.dromara.payment.domain.dto.PaymentEventBatchRequest;
import org.dromara.payment.domain.dto.PaymentEventItem;
import org.dromara.payment.domain.dto.PaymentEventReviewRequest;
import org.dromara.payment.domain.dto.DuplicateReviewRequest;
import org.dromara.payment.domain.vo.PaymentDashboardVo;
import org.dromara.payment.domain.vo.PaymentEventBatchVo;
import org.dromara.payment.domain.vo.PaymentEventExportVo;
import org.dromara.payment.domain.vo.PaymentEventRawVo;
import org.dromara.payment.domain.vo.PaymentEventVo;
import org.dromara.payment.domain.vo.PaymentTrendPointVo;
import org.dromara.payment.event.PaymentIncomeReceivedEvent;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentEventReviewMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 支付通知事件服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final PaymentEventMapper eventMapper;
    private final PaymentEventReviewMapper reviewMapper;
    private final PaymentOrderService orderService;
    private final PaymentTransactionService transactionService;
    private final PaymentDeviceService deviceService;
    private final MerchantAccessService merchantAccessService;
    private final ObjectMapper objectMapper;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final MerchantDisplayService merchantDisplayService;

    public PaymentEventBatchVo ingest(
        Long merchantId,
        Long deviceId,
        PaymentEventBatchRequest request
    ) {
        if (request.getEvents().size() > properties.getEvents().getMaxBatchSize()) {
            throw new DeviceApiException(
                400,
                "VALIDATION_FAILED",
                "单批事件数量超限",
                false,
                false
            );
        }
        PaymentEventBatchVo result = new PaymentEventBatchVo();
        for (PaymentEventItem item : request.getEvents()) {
            try {
                PmPaymentEvent event = toEntity(
                    merchantId,
                    deviceId,
                    request.getSentAt(),
                    item
                );
                int inserted = eventMapper.insertOnConflict(event);
                if (inserted > 0) {
                    result.getAccepted().add(item.getClientEventId());
                    detectSuspectedDuplicate(event);
                    transactionService.observe(event);
                    try {
                        orderService.autoMatch(event);
                    } catch (RuntimeException exception) {
                        log.error("支付事件自动匹配失败，clientEventId={}", event.getClientEventId(), exception);
                    }
                    publishIncomeEvent(event);
                } else {
                    result.getDuplicates().add(item.getClientEventId());
                }
            } catch (Exception exception) {
                result.getRejected().add(new PaymentEventBatchVo.RejectedEvent(
                    item.getClientEventId(),
                    "EVENT_REJECTED",
                    exception.getMessage()
                ));
            }
        }
        deviceService.recordUpload(deviceId);
        return result;
    }

    public PageResult<PaymentEventVo> queryPage(
        PaymentEventQueryBo bo,
        PageQuery pageQuery
    ) {
        LambdaQueryWrapper<PmPaymentEvent> wrapper = buildQuery(bo);
        wrapper.orderByDesc(PmPaymentEvent::getEventTimeMs);
        wrapper.orderByDesc(PmPaymentEvent::getClientReceivedAtMs);
        wrapper.orderByDesc(PmPaymentEvent::getReceivedAt);
        wrapper.orderByDesc(PmPaymentEvent::getId);
        Page<PaymentEventVo> page = eventMapper.selectVoPage(
            pageQuery.build(),
            wrapper
        );
        page.getRecords().forEach(item -> item.setRawPayload(null));
        merchantDisplayService.enrich(
            page.getRecords(),
            PaymentEventVo::getMerchantId,
            PaymentEventVo::setMerchantCode,
            PaymentEventVo::setMerchantName);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    public PaymentEventVo queryById(Long id) {
        PaymentEventVo event = eventMapper.selectVoOne(new LambdaQueryWrapper<PmPaymentEvent>()
            .eq(PmPaymentEvent::getId, id)
            .last("limit 1"));
        if (event == null) {
            throw new ServiceException("支付事件不存在");
        }
        MerchantContext.requireAccessibleMerchant(event.getMerchantId());
        event.setRawPayload(null);
        event.setReviewHistory(reviewMapper.selectVoList(
            new LambdaQueryWrapper<PmPaymentEventReview>()
                .eq(PmPaymentEventReview::getMerchantId, event.getMerchantId())
                .eq(PmPaymentEventReview::getEventId, id)
                .orderByDesc(PmPaymentEventReview::getOperatedAt)));
        merchantDisplayService.enrich(
            List.of(event),
            PaymentEventVo::getMerchantId,
            PaymentEventVo::setMerchantCode,
            PaymentEventVo::setMerchantName);
        return event;
    }

    public PaymentEventRawVo queryRaw(Long id, boolean masked) {
        PmPaymentEvent event = requireEvent(id);
        String raw = event.getRawPayload();
        return new PaymentEventRawVo(id, masked, masked ? maskRawPayload(raw) : raw);
    }

    public List<PaymentEventExportVo> queryExportList(PaymentEventQueryBo bo) {
        ZoneId displayZone = ZoneId.of(MerchantContext.displayTimezone());
        LambdaQueryWrapper<PmPaymentEvent> wrapper = buildQuery(bo)
            .orderByDesc(PmPaymentEvent::getReceivedAt)
            .last("limit 10000");
        List<PaymentEventExportVo> rows = eventMapper.selectList(wrapper).stream()
            .map(event -> toExportVo(event, displayZone))
            .toList();
        merchantDisplayService.enrich(
            rows,
            PaymentEventExportVo::getMerchantId,
            PaymentEventExportVo::setMerchantCode,
            PaymentEventExportVo::setMerchantName);
        return rows;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public PaymentEventVo review(Long id, PaymentEventReviewRequest request) {
        PmPaymentEvent event = requireEvent(id);
        String beforeStatus = event.getStatus();
        String beforeDirection = event.getDirection();
        Long beforeAmount = event.getAmountMinor();

        switch (request.getAction()) {
            case "REVIEW" -> event.setStatus(PaymentConstants.EVENT_STATUS_REVIEWED);
            case "IGNORE" -> event.setStatus(PaymentConstants.EVENT_STATUS_IGNORED);
            case "CORRECT" -> {
                if (StringUtils.isBlank(request.getDirection()) && request.getAmountMinor() == null) {
                    throw new ServiceException("修正操作至少需要提供方向或金额");
                }
                if (StringUtils.isNotBlank(request.getDirection())) {
                    event.setDirection(request.getDirection());
                }
                if (request.getAmountMinor() != null) {
                    event.setAmountMinor(request.getAmountMinor());
                }
                event.setStatus(PaymentConstants.EVENT_STATUS_REVIEWED);
                event.setParseStatus("PARSED");
            }
            default -> throw new ServiceException("不支持的审核操作");
        }

        OffsetDateTime operatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Long operatedBy = LoginHelper.getUserId();
        event.setReviewedAt(operatedAt);
        event.setReviewedBy(operatedBy);
        event.setReviewNote(request.getNote());
        eventMapper.updateById(event);
        if ("IGNORE".equals(request.getAction())) {
            transactionService.rejectByEvent(event.getMerchantId(), event.getId(), "支付事件已忽略");
        }

        PmPaymentEventReview audit = new PmPaymentEventReview();
        audit.setId(IdWorker.getId());
        audit.setMerchantId(event.getMerchantId());
        audit.setEventId(event.getId());
        audit.setAction(request.getAction());
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(event.getStatus());
        audit.setBeforeDirection(beforeDirection);
        audit.setAfterDirection(event.getDirection());
        audit.setBeforeAmountMinor(beforeAmount);
        audit.setAfterAmountMinor(event.getAmountMinor());
        audit.setNote(request.getNote());
        audit.setOperatedBy(operatedBy);
        audit.setOperatedAt(operatedAt);
        reviewMapper.insert(audit);
        return queryById(id);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void review(List<Long> ids, String action, String note) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<PmPaymentEvent> events = eventMapper.selectList(
            new LambdaQueryWrapper<PmPaymentEvent>().in(PmPaymentEvent::getId, distinctIds));
        if (events.size() != distinctIds.size()) {
            throw new ServiceException("部分支付事件不存在");
        }
        MerchantContext.requireSingleAccessibleMerchant(
            events.stream().map(PmPaymentEvent::getMerchantId).toList());
        for (Long id : distinctIds) {
            PaymentEventReviewRequest request = new PaymentEventReviewRequest();
            request.setAction(action);
            request.setNote(note);
            review(id, request);
        }
    }

    public PaymentDashboardVo dashboard() {
        Long merchantId = MerchantContext.resolveQueryMerchantId(null);
        ZoneId displayZone = ZoneId.of(MerchantContext.displayTimezone());
        OffsetDateTime begin = LocalDate.now(displayZone)
            .atStartOfDay(displayZone)
            .toOffsetDateTime();
        List<PmPaymentEvent> events = eventMapper.selectList(
            new LambdaQueryWrapper<PmPaymentEvent>()
                .eq(merchantId != null, PmPaymentEvent::getMerchantId, merchantId)
                .ge(PmPaymentEvent::getReceivedAt, begin)
                .orderByAsc(PmPaymentEvent::getReceivedAt));
        long todayEvents = events.size();
        long wechatEvents = countBy(events, "WECHAT", null);
        long alipayEvents = countBy(events, "ALIPAY", null);
        long incomeEvents = countBy(events, null, "INCOME");
        long expenseEvents = countBy(events, null, "EXPENSE");
        long todayIncomeAmount = incomeAmount(events, null);
        long wechatIncomeAmount = incomeAmount(events, "WECHAT");
        long alipayIncomeAmount = incomeAmount(events, "ALIPAY");
        long parseFailures = events.stream()
            .filter(item -> !"PARSED".equals(item.getParseStatus()))
            .count();
        List<Long> latencies = events.stream()
            .map(this::syncLatency)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        return PaymentDashboardVo.builder()
            .displayTimezone(MerchantContext.displayTimezone())
            .todayEvents(todayEvents)
            .wechatEvents(wechatEvents)
            .alipayEvents(alipayEvents)
            .incomeEvents(incomeEvents)
            .expenseEvents(expenseEvents)
            .onlineDevices(deviceService.onlineCount(merchantId))
            .todayIncomeAmountMinor(todayIncomeAmount)
            .wechatIncomeAmountMinor(wechatIncomeAmount)
            .alipayIncomeAmountMinor(alipayIncomeAmount)
            .pendingReviewEvents(events.stream()
                .filter(item -> PaymentConstants.EVENT_STATUS_RECEIVED.equals(item.getStatus()))
                .count())
            .parseFailureRate(todayEvents == 0 ? 0 : (double) parseFailures / todayEvents)
            .averageSyncLatencyMs(latencies.isEmpty() ? 0
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0)))
            .p95SyncLatencyMs(percentile95(latencies))
            .trend(buildTrend(events, displayZone))
            .build();
    }

    private long count(LambdaQueryWrapper<PmPaymentEvent> wrapper) {
        return eventMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<PmPaymentEvent> buildQuery(PaymentEventQueryBo bo) {
        LambdaQueryWrapper<PmPaymentEvent> wrapper = new LambdaQueryWrapper<>();
        Long merchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        wrapper.eq(merchantId != null, PmPaymentEvent::getMerchantId, merchantId);
        wrapper.eq(StringUtils.isNotBlank(bo.getPlatform()), PmPaymentEvent::getPlatform, bo.getPlatform());
        wrapper.eq(StringUtils.isNotBlank(bo.getDirection()), PmPaymentEvent::getDirection, bo.getDirection());
        wrapper.eq(StringUtils.isNotBlank(bo.getParseStatus()), PmPaymentEvent::getParseStatus, bo.getParseStatus());
        wrapper.eq(StringUtils.isNotBlank(bo.getStatus()), PmPaymentEvent::getStatus, bo.getStatus());
        wrapper.eq(StringUtils.isNotBlank(bo.getDuplicateStatus()), PmPaymentEvent::getDuplicateStatus, bo.getDuplicateStatus());
        wrapper.eq(bo.getDeviceId() != null, PmPaymentEvent::getDeviceId, bo.getDeviceId());
        wrapper.eq(bo.getAmountMinor() != null, PmPaymentEvent::getAmountMinor, bo.getAmountMinor());
        wrapper.ge(bo.getMinAmountMinor() != null, PmPaymentEvent::getAmountMinor, bo.getMinAmountMinor());
        wrapper.le(bo.getMaxAmountMinor() != null, PmPaymentEvent::getAmountMinor, bo.getMaxAmountMinor());
        wrapper.ge(bo.getBeginTime() != null, PmPaymentEvent::getReceivedAt, bo.getBeginTime());
        wrapper.le(bo.getEndTime() != null, PmPaymentEvent::getReceivedAt, bo.getEndTime());
        if (StringUtils.isNotBlank(bo.getKeyword())) {
            String keyword = bo.getKeyword().trim();
            wrapper.and(query -> query
                .like(PmPaymentEvent::getMatchedRule, keyword)
                .or().like(PmPaymentEvent::getClientEventId, keyword)
                .or().like(PmPaymentEvent::getFingerprint, keyword)
                .or().like(PmPaymentEvent::getRawHash, keyword));
        }
        return wrapper;
    }

    private PmPaymentEvent requireEvent(Long id) {
        PmPaymentEvent event = eventMapper.selectOne(new LambdaQueryWrapper<PmPaymentEvent>()
            .eq(PmPaymentEvent::getId, id)
            .last("limit 1"));
        if (event == null) {
            throw new ServiceException("支付事件不存在");
        }
        MerchantContext.requireAccessibleMerchant(event.getMerchantId());
        return event;
    }

    private PaymentEventExportVo toExportVo(PmPaymentEvent event, ZoneId displayZone) {
        PaymentEventExportVo vo = new PaymentEventExportVo();
        vo.setMerchantId(event.getMerchantId());
        vo.setId(event.getId());
        vo.setDeviceId(event.getDeviceId());
        vo.setPlatform(event.getPlatform());
        vo.setDirection(event.getDirection());
        vo.setAmountMinor(event.getAmountMinor());
        vo.setCurrency(event.getCurrency());
        vo.setParseStatus(event.getParseStatus());
        vo.setStatus(event.getStatus());
        vo.setEventTime(formatExportTime(event.getEventTime(), displayZone));
        vo.setReceivedAt(formatExportTime(event.getReceivedAt(), displayZone));
        vo.setSyncLatencyMs(syncLatency(event));
        vo.setMatchedRule(event.getMatchedRule());
        vo.setReviewedAt(formatExportTime(event.getReviewedAt(), displayZone));
        vo.setReviewedBy(event.getReviewedBy());
        vo.setReviewNote(event.getReviewNote());
        return vo;
    }

    static String formatExportTime(OffsetDateTime value, ZoneId displayZone) {
        if (value == null) {
            return null;
        }
        ZoneId zone = displayZone == null ? ZoneId.of("Asia/Shanghai") : displayZone;
        return value.atZoneSameInstant(zone).format(EXPORT_TIME_FORMATTER);
    }

    private long countBy(List<PmPaymentEvent> events, String platform, String direction) {
        return events.stream()
            .filter(item -> platform == null || platform.equals(item.getPlatform()))
            .filter(item -> direction == null || direction.equals(item.getDirection()))
            .count();
    }

    private long incomeAmount(List<PmPaymentEvent> events, String platform) {
        return events.stream()
            .filter(item -> "INCOME".equals(item.getDirection()))
            .filter(item -> platform == null || platform.equals(item.getPlatform()))
            .map(PmPaymentEvent::getAmountMinor)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();
    }

    private Long syncLatency(PmPaymentEvent event) {
        if (event.getClientReceivedAtMs() == null || event.getReceivedAt() == null) {
            return null;
        }
        return Math.max(0, event.getReceivedAt().toInstant().toEpochMilli() - event.getClientReceivedAtMs());
    }

    private long percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(sortedValues.size() * 0.95) - 1);
        return sortedValues.get(index);
    }

    private List<PaymentTrendPointVo> buildTrend(List<PmPaymentEvent> events, ZoneId zoneId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            buckets.put(String.format("%02d:00", hour), new long[3]);
        }
        for (PmPaymentEvent event : events) {
            String bucket = event.getReceivedAt().atZoneSameInstant(zoneId).format(formatter);
            long[] values = buckets.get(bucket);
            values[0]++;
            if ("INCOME".equals(event.getDirection())) {
                values[1]++;
                values[2] += event.getAmountMinor() == null ? 0 : event.getAmountMinor();
            }
        }
        List<PaymentTrendPointVo> result = new ArrayList<>();
        buckets.forEach((bucket, values) ->
            result.add(new PaymentTrendPointVo(bucket, values[0], values[1], values[2])));
        return result;
    }

    private String maskRawPayload(String raw) {
        if (StringUtils.isBlank(raw)) {
            return raw;
        }
        String masked = MOBILE_PATTERN.matcher(raw).replaceAll("$1****$2");
        masked = LONG_NUMBER_PATTERN.matcher(masked).replaceAll(match -> {
            String value = match.group();
            return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
        });
        return masked;
    }

    private static final Pattern MOBILE_PATTERN =
        Pattern.compile("(1\\d{2})\\d{4}(\\d{4})");
    private static final Pattern LONG_NUMBER_PATTERN =
        Pattern.compile("(?<!\\d)\\d{10,}(?!\\d)");

    private PmPaymentEvent toEntity(
        Long merchantId,
        Long deviceId,
        OffsetDateTime sentAt,
        PaymentEventItem item
    ) throws Exception {
        if (item.getAmountMinor() != null && item.getAmountMinor() < 1) {
            throw new ServiceException("金额必须大于零");
        }
        OffsetDateTime serverReceivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ResolvedTimestamp eventTimestamp = resolveTimestamp(
            "eventTime",
            item.getEventTime(),
            item.getEventTimeMs(),
            null
        );
        ResolvedTimestamp clientReceivedTimestamp = resolveTimestamp(
            "clientReceivedAt",
            item.getClientReceivedAt(),
            item.getClientReceivedAtMs(),
            eventTimestamp.value() == null
                ? serverReceivedAt
                : eventTimestamp.value()
        );
        ResolvedTimestamp clientSentTimestamp = resolveTimestamp(
            "sentAt",
            sentAt,
            null,
            serverReceivedAt
        );

        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(IdWorker.getId());
        event.setMerchantId(merchantId);
        event.setDeviceId(deviceId);
        event.setDeviceSequence(item.getDeviceSequence());
        event.setClientEventId(item.getClientEventId());
        event.setPlatform(item.getPlatform());
        event.setDirection(item.getDirection());
        event.setAmountMinor(item.getAmountMinor());
        event.setCurrency(StringUtils.blankToDefault(item.getCurrency(), "CNY"));
        event.setEventTime(eventTimestamp.value());
        event.setEventTimeMs(eventTimestamp.epochMillis());
        event.setClientReceivedAt(clientReceivedTimestamp.value());
        event.setClientReceivedAtMs(clientReceivedTimestamp.epochMillis());
        event.setClientSentAt(clientSentTimestamp.value());
        event.setClientSentAtMs(clientSentTimestamp.epochMillis());
        event.setReceivedAt(serverReceivedAt);
        event.setParseStatus(item.getParseStatus());
        event.setParserVersion(item.getParserVersion());
        event.setMatchedRule(item.getMatchedRule());
        event.setFingerprint(item.getFingerprint());
        event.setNotificationKeyHash(item.getNotificationKeyHash());
        event.setRawHash(item.getRawHash());
        if (
            properties.getEvents().isRawPayloadUploadEnabled()
                && item.getRawPayload() != null
        ) {
            event.setRawPayload(
                objectMapper.writeValueAsString(item.getRawPayload())
            );
        }
        event.setStatus(PaymentConstants.EVENT_STATUS_RECEIVED);
        event.setDuplicateStatus(PaymentConstants.DUPLICATE_STATUS_NONE);
        return event;
    }

    private ResolvedTimestamp resolveTimestamp(
        String field,
        OffsetDateTime value,
        Long epochMillis,
        OffsetDateTime fallback
    ) {
        if (
            value != null
                && epochMillis != null
                && value.toInstant().toEpochMilli() != epochMillis
        ) {
            throw new ServiceException(field + " 与对应毫秒时间不一致");
        }
        if (epochMillis != null) {
            return new ResolvedTimestamp(
                OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(epochMillis),
                    ZoneOffset.UTC
                ),
                epochMillis
            );
        }
        OffsetDateTime resolved = value == null ? fallback : value;
        if (resolved == null) {
            return new ResolvedTimestamp(null, null);
        }
        OffsetDateTime utc = toUtc(resolved);
        return new ResolvedTimestamp(utc, utc.toInstant().toEpochMilli());
    }

    private OffsetDateTime toUtc(OffsetDateTime value) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(value.toInstant().toEpochMilli()),
            ZoneOffset.UTC
        );
    }

    private record ResolvedTimestamp(
        OffsetDateTime value,
        Long epochMillis
    ) {
    }

    private void publishIncomeEvent(PmPaymentEvent event) {
        if (!"INCOME".equals(event.getDirection())) {
            return;
        }
        try {
            eventPublisher.publishEvent(new PaymentIncomeReceivedEvent(
                event.getMerchantId(),
                event.getDeviceId(),
                event.getClientEventId(),
                event.getPlatform(),
                event.getAmountMinor(),
                event.getEventTimeMs(),
                event.getClientReceivedAtMs(),
                event.getClientSentAtMs(),
                event.getReceivedAt()
            ));
        } catch (RuntimeException exception) {
            log.error(
                "发布收款内部事件失败，clientEventId={}",
                event.getClientEventId(),
                exception
            );
        }
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public PaymentEventVo reviewDuplicate(Long id, DuplicateReviewRequest request) {
        PmPaymentEvent event = requireEvent(id);
        if (!PaymentConstants.DUPLICATE_STATUS_SUSPECTED.equals(event.getDuplicateStatus())
            && !request.getStatus().equals(event.getDuplicateStatus())) {
            throw new ServiceException("只有疑似重复事件可以确认或排除");
        }
        event.setDuplicateStatus(request.getStatus());
        event.setDuplicateReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setDuplicateReviewedBy(LoginHelper.getUserId());
        event.setDuplicateReviewNote(request.getNote());
        eventMapper.updateById(event);
        if (PaymentConstants.DUPLICATE_STATUS_CONFIRMED.equals(request.getStatus())) {
            transactionService.rejectByEvent(event.getMerchantId(), event.getId(), "已确认重复支付事件");
        }
        return queryById(id);
    }

    private void detectSuspectedDuplicate(PmPaymentEvent event) {
        if (StringUtils.isBlank(event.getRawHash())) {
            return;
        }
        LambdaQueryWrapper<PmPaymentEvent> query = new LambdaQueryWrapper<PmPaymentEvent>()
            .eq(PmPaymentEvent::getMerchantId, event.getMerchantId())
            .ne(PmPaymentEvent::getDeviceId, event.getDeviceId())
            .eq(PmPaymentEvent::getPlatform, event.getPlatform())
            .eq(PmPaymentEvent::getDirection, event.getDirection())
            .eq(PmPaymentEvent::getRawHash, event.getRawHash())
            .ge(PmPaymentEvent::getReceivedAt, event.getReceivedAt().minusSeconds(10))
            .le(PmPaymentEvent::getReceivedAt, event.getReceivedAt())
            .ne(PmPaymentEvent::getId, event.getId())
            .orderByAsc(PmPaymentEvent::getReceivedAt)
            .orderByAsc(PmPaymentEvent::getId)
            .last("limit 1");
        if (event.getAmountMinor() == null) {
            query.isNull(PmPaymentEvent::getAmountMinor);
        } else {
            query.eq(PmPaymentEvent::getAmountMinor, event.getAmountMinor());
        }
        PmPaymentEvent first = eventMapper.selectOne(query);
        if (first == null) {
            return;
        }
        event.setDuplicateStatus(PaymentConstants.DUPLICATE_STATUS_SUSPECTED);
        event.setDuplicateOfEventId(first.getId());
        event.setDuplicateDetectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        eventMapper.updateById(event);
    }
}
