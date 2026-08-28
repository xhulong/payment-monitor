package org.dromara.payment.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantAccessService;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.domain.bo.MerchantQueryBo;
import org.dromara.payment.domain.dto.MerchantSaveRequest;
import org.dromara.payment.domain.vo.MerchantContextVo;
import org.dromara.payment.domain.vo.MerchantVo;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.system.domain.bo.SysUserBo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MerchantService {
    private final MerchantMapper merchantMapper;
    private final MerchantUserMapper merchantUserMapper;
    private final MerchantAccessService accessService;
    private final ISysUserService userService;

    public MerchantContextVo currentContext() {
        Long merchantId = MerchantContext.merchantId();
        PmMerchant merchant = merchantId == null ? null : accessService.requireMerchant(merchantId, false);
        return new MerchantContextVo(
            MerchantContext.isSuperAdmin(),
            merchant == null ? null : merchant.getId(),
            merchant == null ? null : merchant.getMerchantCode(),
            merchant == null ? null : merchant.getName(),
            MerchantContext.accountType(),
            MerchantContext.scopeMode(),
            MerchantContext.canAccessAllMerchants(),
            MerchantContext.displayTimezone());
    }

    public PageResult<MerchantVo> queryPage(MerchantQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PmMerchant> wrapper = new LambdaQueryWrapper<PmMerchant>()
            .like(StringUtils.isNotBlank(bo.getMerchantCode()), PmMerchant::getMerchantCode, bo.getMerchantCode())
            .like(StringUtils.isNotBlank(bo.getName()), PmMerchant::getName, bo.getName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PmMerchant::getStatus, bo.getStatus())
            .orderByDesc(PmMerchant::getCreatedAt);
        Long scopedMerchantId = MerchantContext.resolveQueryMerchantId(bo.getMerchantId());
        if (scopedMerchantId != null) {
            wrapper.eq(PmMerchant::getId, scopedMerchantId);
        }
        Page<PmMerchant> page = merchantMapper.selectPage(pageQuery.build(), wrapper);
        List<MerchantVo> rows = page.getRecords().stream().map(this::toOptionVo).toList();
        enrichAdminUsers(rows);
        return PageResult.build(rows, page.getTotal());
    }

    public List<MerchantVo> options(String keyword, String status, Integer limit) {
        if (!MerchantContext.canAccessAllMerchants()) {
            return List.of(toVo(accessService.requireMerchant(MerchantContext.requireMerchantId(), false)));
        }
        int pageSize = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<PmMerchant> wrapper = new LambdaQueryWrapper<PmMerchant>()
            .and(StringUtils.isNotBlank(keyword), query -> query
                .like(PmMerchant::getMerchantCode, keyword)
                .or()
                .like(PmMerchant::getName, keyword))
            .eq(StringUtils.isNotBlank(status), PmMerchant::getStatus, status)
            .orderByAsc(PmMerchant::getName)
            .last("limit " + pageSize);
        return merchantMapper.selectList(wrapper).stream().map(this::toOptionVo).toList();
    }

    public MerchantVo queryById(Long id) {
        accessService.requireAccessible(id);
        return toVo(accessService.requireMerchant(id, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantVo create(MerchantSaveRequest request) {
        requireSuperAdmin();
        if (request.getAdminUserId() == null && StringUtils.isBlank(request.getAdminUserName())) {
            throw new ServiceException("必须指定现有用户或填写新商户管理员账号");
        }
        if (merchantMapper.selectCount(new LambdaQueryWrapper<PmMerchant>()
            .eq(PmMerchant::getMerchantCode, request.getMerchantCode().trim().toUpperCase())) > 0) {
            throw new ServiceException("商户编码已存在");
        }
        OffsetDateTime now = now();
        PmMerchant merchant = new PmMerchant();
        merchant.setId(IdWorker.getId());
        merchant.setMerchantCode(request.getMerchantCode().trim().toUpperCase());
        merchant.setName(request.getName().trim());
        merchant.setStatus(request.getStatus());
        merchant.setTimezone(validateTimezone(request.getTimezone()));
        merchant.setRemark(request.getRemark());
        merchant.setCreatedBy(LoginHelper.getUserId());
        merchant.setCreatedAt(now);
        merchant.setUpdatedAt(now);
        merchantMapper.insert(merchant);

        Long userId = request.getAdminUserId() == null ? createAdminUser(request) : request.getAdminUserId();
        bindUser(merchant.getId(), userId);
        return toVo(merchant);
    }

    public MerchantVo update(Long id, MerchantSaveRequest request) {
        requireSuperAdmin();
        PmMerchant merchant = accessService.requireMerchant(id, false);
        PmMerchant codeOwner = merchantMapper.selectOne(new LambdaQueryWrapper<PmMerchant>()
            .eq(PmMerchant::getMerchantCode, request.getMerchantCode().trim().toUpperCase())
            .ne(PmMerchant::getId, id)
            .last("limit 1"));
        if (codeOwner != null) {
            throw new ServiceException("商户编码已存在");
        }
        merchant.setMerchantCode(request.getMerchantCode().trim().toUpperCase());
        merchant.setName(request.getName().trim());
        merchant.setStatus(request.getStatus());
        merchant.setTimezone(validateTimezone(request.getTimezone()));
        merchant.setRemark(request.getRemark());
        merchant.setUpdatedAt(now());
        merchantMapper.updateById(merchant);
        return toVo(merchant);
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantVo bindUser(Long merchantId, Long userId) {
        requireSuperAdmin();
        accessService.requireMerchant(merchantId, false);
        if (merchantUserMapper.userExists(userId) == 0) {
            throw new ServiceException("用户不存在");
        }
        PmMerchantUser existing = merchantUserMapper.selectOne(
            new LambdaQueryWrapper<PmMerchantUser>()
                .eq(PmMerchantUser::getUserId, userId)
                .last("limit 1"));
        if (existing != null && !merchantId.equals(existing.getMerchantId())) {
            throw new ServiceException("普通后台用户只能绑定一个商户");
        }
        if (existing == null) {
            PmMerchantUser binding = new PmMerchantUser();
            binding.setId(IdWorker.getId());
            binding.setMerchantId(merchantId);
            binding.setUserId(userId);
            binding.setCreatedBy(LoginHelper.getUserId());
            binding.setCreatedAt(now());
            try {
                merchantUserMapper.insert(binding);
            } catch (DuplicateKeyException exception) {
                throw new ServiceException("用户已绑定其他商户");
            }
        }
        merchantUserMapper.grantRole(userId, PaymentConstants.MERCHANT_ADMIN_ROLE_ID);
        return toVo(accessService.requireMerchant(merchantId, false));
    }

    private Long createAdminUser(MerchantSaveRequest request) {
        if (StringUtils.isBlank(request.getAdminPassword())) {
            throw new ServiceException("新商户管理员必须设置初始密码");
        }
        SysUserBo user = new SysUserBo();
        user.setDeptId(PaymentConstants.DEFAULT_DEPT_ID);
        user.setUserName(request.getAdminUserName().trim());
        user.setNickName(StringUtils.blankToDefault(request.getAdminNickName(), request.getAdminUserName()).trim());
        user.setPassword(BCrypt.hashpw(request.getAdminPassword()));
        user.setStatus("0");
        user.setGender("0");
        user.setRoleIds(new Long[]{PaymentConstants.MERCHANT_ADMIN_ROLE_ID});
        user.setRemark("支付商户管理员");
        if (!userService.checkUserNameUnique(user)) {
            throw new ServiceException("后台用户账号已存在");
        }
        userService.insertUser(user);
        SysUserVo created = userService.selectUserByUserName(user.getUserName());
        if (created == null) {
            throw new ServiceException("创建商户管理员失败");
        }
        return created.getUserId();
    }

    private MerchantVo toVo(PmMerchant merchant) {
        MerchantVo vo = toOptionVo(merchant);
        PmMerchantUser binding = merchantUserMapper.selectOne(
            new LambdaQueryWrapper<PmMerchantUser>()
                .eq(PmMerchantUser::getMerchantId, merchant.getId())
                .orderByAsc(PmMerchantUser::getCreatedAt)
                .last("limit 1"));
        if (binding != null) {
            vo.setAdminUserId(binding.getUserId());
            vo.setAdminUserName(merchantUserMapper.selectUserName(binding.getUserId()));
        }
        return vo;
    }

    private MerchantVo toOptionVo(PmMerchant merchant) {
        MerchantVo vo = new MerchantVo();
        vo.setId(merchant.getId());
        vo.setMerchantCode(merchant.getMerchantCode());
        vo.setName(merchant.getName());
        vo.setStatus(merchant.getStatus());
        vo.setLifecycleStatus(merchant.getLifecycleStatus());
        vo.setTimezone(merchant.getTimezone());
        vo.setRemark(merchant.getRemark());
        vo.setCreatedBy(merchant.getCreatedBy());
        vo.setCreatedAt(merchant.getCreatedAt());
        vo.setUpdatedAt(merchant.getUpdatedAt());
        return vo;
    }

    private void enrichAdminUsers(List<MerchantVo> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return;
        }
        Map<Long, MerchantVo> byMerchantId = new LinkedHashMap<>();
        for (MerchantVo merchant : merchants) {
            byMerchantId.put(merchant.getId(), merchant);
        }
        merchantUserMapper.selectAdminBindings(byMerchantId.keySet()).forEach(binding -> {
            MerchantVo merchant = byMerchantId.get(binding.getMerchantId());
            if (merchant != null && merchant.getAdminUserId() == null) {
                merchant.setAdminUserId(binding.getUserId());
                merchant.setAdminUserName(binding.getUserName());
            }
        });
    }

    private void requireSuperAdmin() {
        if (!LoginHelper.isSuperAdmin()) {
            throw new ServiceException("仅平台超级管理员可执行此操作");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ServiceException("商户时区无效");
        }
    }
}
