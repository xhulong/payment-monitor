package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.PmDeviceAssignment;
import org.dromara.payment.domain.dto.DeviceAssignmentSaveRequest;
import org.dromara.payment.domain.vo.DeviceAssignmentVo;
import org.dromara.payment.mapper.DeviceAssignmentMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceAssignmentService {
    private final DeviceAssignmentMapper mapper;
    private final PaymentDeviceMapper deviceMapper;
    private final PaymentProperties properties;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public List<DeviceAssignmentVo> query(Long requestedMerchantId) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(requestedMerchantId);
        List<PmDeviceAssignment> assignments = mapper.selectList(
            new LambdaQueryWrapper<PmDeviceAssignment>()
                .eq(merchantId != null, PmDeviceAssignment::getMerchantId, merchantId)
                .orderByAsc(PmDeviceAssignment::getMerchantId)
                .orderByAsc(PmDeviceAssignment::getPlatform)
                .orderByAsc(PmDeviceAssignment::getRole)
                .orderByAsc(PmDeviceAssignment::getPriority));
        Map<Long, PmDevice> devices = new HashMap<>();
        for (PmDeviceAssignment assignment : assignments) {
            devices.computeIfAbsent(assignment.getDeviceId(), deviceMapper::selectById);
        }
        Map<String, Long> effectiveByPlatform = new HashMap<>();
        for (PmDeviceAssignment source : assignments) {
            String key = source.getMerchantId() + ":" + source.getPlatform();
            assignments.stream()
                .filter(item -> source.getMerchantId().equals(item.getMerchantId()))
                .filter(item -> source.getPlatform().equals(item.getPlatform()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> healthy(devices.get(item.getDeviceId())))
                .sorted(Comparator
                .comparing((PmDeviceAssignment item) -> !"PRIMARY".equals(item.getRole()))
                    .thenComparing(PmDeviceAssignment::getPriority))
                .findFirst()
                .ifPresent(item -> effectiveByPlatform.put(key, item.getDeviceId()));
        }
        List<DeviceAssignmentVo> rows = assignments.stream().map(item -> {
            PmDevice device = devices.get(item.getDeviceId());
            DeviceAssignmentVo vo = new DeviceAssignmentVo();
            vo.setId(item.getId());
            vo.setMerchantId(item.getMerchantId());
            vo.setPlatform(item.getPlatform());
            vo.setDeviceId(item.getDeviceId());
            vo.setDeviceName(device == null ? null : device.getDeviceName());
            vo.setRole(item.getRole());
            vo.setPriority(item.getPriority());
            vo.setEnabled(item.getEnabled());
            vo.setHealthy(healthy(device));
            vo.setEffectiveObserver(item.getDeviceId().equals(
                effectiveByPlatform.get(item.getMerchantId() + ":" + item.getPlatform())));
            vo.setLastSeenAt(device == null ? null : device.getLastSeenAt());
            vo.setHealthIssue(device == null ? "DEVICE_NOT_FOUND" : device.getLastHealthIssue());
            return vo;
        }).toList();
        merchantDisplayService.enrich(
            rows,
            DeviceAssignmentVo::getMerchantId,
            DeviceAssignmentVo::setMerchantCode,
            DeviceAssignmentVo::setMerchantName);
        return rows;
    }

    public AssignmentInfo infoForDevice(Long merchantId, Long deviceId) {
        List<PmDeviceAssignment> assignments = mapper.selectList(
            new LambdaQueryWrapper<PmDeviceAssignment>()
                .eq(PmDeviceAssignment::getMerchantId, merchantId)
                .eq(PmDeviceAssignment::getDeviceId, deviceId)
                .eq(PmDeviceAssignment::getEnabled, true)
                .orderByAsc(PmDeviceAssignment::getPriority));
        if (assignments.isEmpty()) {
            return new AssignmentInfo(null, null);
        }
        String role = assignments.stream().anyMatch(item -> "PRIMARY".equals(item.getRole()))
            ? "PRIMARY"
            : "BACKUP";
        String scope = assignments.stream()
            .map(PmDeviceAssignment::getPlatform)
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));
        return new AssignmentInfo(role, scope);
    }

    public boolean hasHealthyObserver(Long merchantId, String platform) {
        List<PmDeviceAssignment> assignments = mapper.selectList(
            new LambdaQueryWrapper<PmDeviceAssignment>()
                .eq(PmDeviceAssignment::getMerchantId, merchantId)
                .eq(PmDeviceAssignment::getPlatform, platform)
                .eq(PmDeviceAssignment::getEnabled, true)
                .orderByAsc(PmDeviceAssignment::getPriority));
        return assignments.stream()
            .map(PmDeviceAssignment::getDeviceId)
            .distinct()
            .map(deviceMapper::selectById)
            .anyMatch(this::healthy);
    }

    public record AssignmentInfo(String role, String platformScope) {
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DeviceAssignmentVo> save(DeviceAssignmentSaveRequest request) {
        Long merchantId = merchantAccessService.requireTargetMerchant(request.getMerchantId(), true);
        long primaryWechat = request.getAssignments().stream()
            .filter(item -> "WECHAT".equals(item.getPlatform()))
            .filter(item -> "PRIMARY".equals(item.getRole()))
            .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
            .count();
        long primaryAlipay = request.getAssignments().stream()
            .filter(item -> "ALIPAY".equals(item.getPlatform()))
            .filter(item -> "PRIMARY".equals(item.getRole()))
            .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
            .count();
        if (primaryWechat > 1 || primaryAlipay > 1) {
            throw new ServiceException("每个平台最多配置一个启用的主设备");
        }
        OffsetDateTime timestamp = now();
        mapper.delete(new LambdaQueryWrapper<PmDeviceAssignment>()
            .eq(PmDeviceAssignment::getMerchantId, merchantId));
        for (DeviceAssignmentSaveRequest.Item item : request.getAssignments()) {
            PmDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<PmDevice>()
                .eq(PmDevice::getId, item.getDeviceId())
                .eq(PmDevice::getMerchantId, merchantId)
                .last("limit 1"));
            if (device == null) {
                throw new ServiceException("设备不存在或不属于当前商户");
            }
            PmDeviceAssignment assignment = new PmDeviceAssignment();
            assignment.setId(IdWorker.getId());
            assignment.setMerchantId(merchantId);
            assignment.setPlatform(item.getPlatform());
            assignment.setDeviceId(item.getDeviceId());
            assignment.setRole(item.getRole());
            assignment.setPriority(item.getPriority());
            assignment.setEnabled(!Boolean.FALSE.equals(item.getEnabled()));
            assignment.setCreatedBy(currentUserId());
            assignment.setCreatedAt(timestamp);
            assignment.setUpdatedAt(timestamp);
            mapper.insert(assignment);
        }
        return query(merchantId);
    }

    private boolean healthy(PmDevice device) {
        if (device == null
            || !PaymentConstants.DEVICE_STATUS_ENABLED.equals(device.getStatus())
            || device.getLastSeenAt() == null) {
            return false;
        }
        boolean recent = device.getLastSeenAt().isAfter(
            now().minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds()));
        return recent
            && Boolean.TRUE.equals(device.getMonitoringEnabled())
            && Boolean.TRUE.equals(device.getListenerConnected())
            && Boolean.TRUE.equals(device.getForegroundRunning());
    }

    private Long currentUserId() {
        try {
            return LoginHelper.isLogin() ? LoginHelper.getUserId() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
