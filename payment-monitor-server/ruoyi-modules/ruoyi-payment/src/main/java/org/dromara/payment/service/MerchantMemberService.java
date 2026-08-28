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
import org.dromara.payment.domain.PmAccountMfa;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantInvitation;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.domain.dto.MerchantInvitationCreateRequest;
import org.dromara.payment.domain.dto.MerchantMemberUpdateRequest;
import org.dromara.payment.domain.vo.MerchantInvitationVo;
import org.dromara.payment.domain.vo.MerchantMemberVo;
import org.dromara.payment.mapper.AccountMfaMapper;
import org.dromara.payment.mapper.MerchantInvitationMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.payment.security.PaymentCrypto;
import org.dromara.payment.support.MerchantRoleLabels;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MerchantMemberService {
    private final MerchantUserMapper memberMapper;
    private final MerchantInvitationMapper invitationMapper;
    private final MerchantMapper merchantMapper;
    private final AccountMfaMapper mfaMapper;
    private final ISysUserService userService;
    private final PaymentProperties properties;
    private final MailOutboxService mailOutboxService;
    private final MailTemplateService mailTemplateService;
    private final MerchantAccessService merchantAccessService;
    private final MerchantDisplayService merchantDisplayService;

    public List<MerchantMemberVo> list() {
        return list(null);
    }

    public List<MerchantMemberVo> list(Long requestedMerchantId) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(requestedMerchantId);
        List<MerchantMemberVo> rows = memberMapper.selectList(new LambdaQueryWrapper<PmMerchantUser>()
                .eq(merchantId != null, PmMerchantUser::getMerchantId, merchantId)
                .orderByAsc(PmMerchantUser::getMerchantId)
                .orderByAsc(PmMerchantUser::getCreatedAt))
            .stream()
            .map(this::toVo)
            .toList();
        enrichMembers(rows);
        return rows;
    }

    public List<MerchantInvitationVo> invitations() {
        return invitations(null);
    }

    public List<MerchantInvitationVo> invitations(Long requestedMerchantId) {
        Long merchantId = MerchantContext.resolveQueryMerchantId(requestedMerchantId);
        if (merchantId != null) {
            requireManager(merchantId);
        } else if (!MerchantContext.canAccessAllMerchants()) {
            throw new ServiceException("当前账号无权查询全部商户邀请");
        }
        List<MerchantInvitationVo> rows =
            invitationMapper.selectList(new LambdaQueryWrapper<PmMerchantInvitation>()
                    .eq(merchantId != null, PmMerchantInvitation::getMerchantId, merchantId)
                    .orderByDesc(PmMerchantInvitation::getCreatedAt))
                .stream()
                .map(invitation -> toInvitationVo(invitation, null))
                .toList();
        enrichInvitations(rows);
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantInvitationVo invite(MerchantInvitationCreateRequest request) {
        Long merchantId = merchantAccessService.requireTargetMerchant(request.getMerchantId(), true);
        PmMerchantUser operator = requireManager(merchantId);
        String role = request.getRoleCode().trim().toUpperCase(Locale.ROOT);
        if (PaymentConstants.MEMBER_OWNER.equals(role)
            && operator != null
            && !PaymentConstants.MEMBER_OWNER.equals(operator.getRoleCode())) {
            throw new ServiceException("只有商户所有者可以发起所有权转让");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Long existingUserId = memberMapper.selectUserIdByEmail(email);
        if (existingUserId != null && memberMapper.selectCount(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getUserId, existingUserId)) > 0) {
            throw new ServiceException("该账号已属于一个商户");
        }
        String token = PaymentCrypto.randomSecret();
        OffsetDateTime timestamp = now();
        PmMerchantInvitation invitation = new PmMerchantInvitation();
        invitation.setId(IdWorker.getId());
        invitation.setMerchantId(merchantId);
        invitation.setInvitedEmail(email);
        invitation.setRoleCode(role);
        invitation.setTokenHash(PaymentCrypto.sha256Hex(token));
        invitation.setStatus("PENDING");
        invitation.setInvitedBy(currentUserId());
        invitation.setExpiresAt(timestamp.plusHours(properties.getOnboarding().getInvitationTtlHours()));
        invitation.setCreatedAt(timestamp);
        invitation.setUpdatedAt(timestamp);
        invitationMapper.insert(invitation);
        sendInvitationEmail(invitation, token);
        MerchantInvitationVo vo = toInvitationVo(invitation, token);
        enrichInvitations(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantMemberVo accept(String token) {
        PmMerchantInvitation invitation =
            invitationMapper.selectByTokenForUpdate(PaymentCrypto.sha256Hex(token));
        if (invitation == null || !"PENDING".equals(invitation.getStatus())) {
            throw new ServiceException("邀请不存在或已使用");
        }
        if (invitation.getExpiresAt().isBefore(now())) {
            invitation.setStatus("EXPIRED");
            invitation.setUpdatedAt(now());
            invitationMapper.updateById(invitation);
            throw new ServiceException("邀请已过期");
        }
        Long userId = currentUserId();
        SysUserVo user = userService.selectUserById(userId);
        if (user == null || user.getEmail() == null
            || !invitation.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ServiceException("当前登录账号与邀请邮箱不一致");
        }
        PmMerchantUser existing = memberMapper.selectOne(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getUserId, userId)
            .last("limit 1"));
        if (existing != null && !existing.getMerchantId().equals(invitation.getMerchantId())) {
            throw new ServiceException("一个普通账号只能属于一个商户");
        }
        OffsetDateTime timestamp = now();
        PmMerchantUser member = existing;
        if (member == null) {
            member = new PmMerchantUser();
            member.setId(IdWorker.getId());
            member.setMerchantId(invitation.getMerchantId());
            member.setUserId(userId);
            member.setInvitedBy(invitation.getInvitedBy());
            member.setCreatedBy(invitation.getInvitedBy());
            member.setCreatedAt(timestamp);
        }
        member.setRoleCode(invitation.getRoleCode());
        member.setStatus("0");
        member.setUpdatedAt(timestamp);
        if (existing == null) {
            memberMapper.insert(member);
        } else {
            memberMapper.updateById(member);
        }
        if (PaymentConstants.MEMBER_OWNER.equals(invitation.getRoleCode())) {
            transferOwnership(invitation.getMerchantId(), userId, timestamp);
        }
        syncSystemRole(userId, invitation.getRoleCode());
        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedBy(userId);
        invitation.setAcceptedAt(timestamp);
        invitation.setUpdatedAt(timestamp);
        invitationMapper.updateById(invitation);
        MerchantMemberVo vo = toVo(member);
        enrichMembers(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MerchantMemberVo update(Long userId, MerchantMemberUpdateRequest request) {
        PmMerchantUser member = requireMember(userId);
        requireManager(member.getMerchantId());
        if (request.getRoleCode() != null) {
            String role = request.getRoleCode();
            if (PaymentConstants.MEMBER_OWNER.equals(role)
                || PaymentConstants.MEMBER_OWNER.equals(member.getRoleCode())) {
                throw new ServiceException("所有权变更必须通过所有者邀请并由新所有者接受");
            }
            member.setRoleCode(role);
            syncSystemRole(userId, role);
        }
        if (request.getStatus() != null) {
            if (member.getUserId().equals(currentUserId())
                && "1".equals(request.getStatus())) {
                throw new ServiceException("不能停用当前登录账号");
            }
            if (PaymentConstants.MEMBER_OWNER.equals(member.getRoleCode())
                && "1".equals(request.getStatus())) {
                ensureAnotherOwner(member.getMerchantId(), member.getUserId());
            }
            member.setStatus(request.getStatus());
            if ("1".equals(request.getStatus())) {
                memberMapper.revokePaymentRoles(userId);
            } else {
                syncSystemRole(userId, member.getRoleCode());
            }
        }
        member.setUpdatedAt(now());
        memberMapper.updateById(member);
        MerchantMemberVo vo = toVo(member);
        enrichMembers(List.of(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId) {
        PmMerchantUser member = requireMember(userId);
        requireManager(member.getMerchantId());
        if (member.getUserId().equals(currentUserId())) {
            throw new ServiceException("不能移除当前登录账号");
        }
        if (PaymentConstants.MEMBER_OWNER.equals(member.getRoleCode())) {
            ensureAnotherOwner(member.getMerchantId(), member.getUserId());
        }
        memberMapper.deleteById(member.getId());
        memberMapper.revokePaymentRoles(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(List<Long> userIds, String status) {
        List<Long> distinctIds = userIds.stream().distinct().toList();
        List<PmMerchantUser> members = loadMembers(distinctIds);
        Long merchantId = MerchantContext.requireSingleAccessibleMerchant(
            members.stream().map(PmMerchantUser::getMerchantId).toList());
        requireManager(merchantId);
        for (Long userId : distinctIds) {
            MerchantMemberUpdateRequest request = new MerchantMemberUpdateRequest();
            request.setStatus(status);
            update(userId, request);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().distinct().toList();
        List<PmMerchantUser> members = loadMembers(distinctIds);
        Long merchantId = MerchantContext.requireSingleAccessibleMerchant(
            members.stream().map(PmMerchantUser::getMerchantId).toList());
        requireManager(merchantId);
        for (Long userId : distinctIds) {
            remove(userId);
        }
    }

    public PmMerchantUser requireAnyRole(String... roles) {
        PmMerchantUser member = currentMembership();
        for (String role : roles) {
            if (role.equals(member.getRoleCode())) {
                return member;
            }
        }
        throw new ServiceException("当前商户岗位无权执行此操作");
    }

    private PmMerchantUser requireManager(Long merchantId) {
        MerchantContext.requireAccessibleMerchant(merchantId);
        if (MerchantContext.canAccessAllMerchants()) {
            return null;
        }
        PmMerchantUser member = currentMembership(merchantId);
        if (PaymentConstants.MEMBER_OWNER.equals(member.getRoleCode())
            || PaymentConstants.MEMBER_ADMIN.equals(member.getRoleCode())) {
            return member;
        }
        throw new ServiceException("当前商户岗位无权执行此操作");
    }

    private PmMerchantUser currentMembership() {
        return currentMembership(MerchantContext.requireMerchantId());
    }

    private PmMerchantUser currentMembership(Long merchantId) {
        PmMerchantUser member = memberMapper.selectOne(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getMerchantId, merchantId)
            .eq(PmMerchantUser::getUserId, currentUserId())
            .eq(PmMerchantUser::getStatus, "0")
            .last("limit 1"));
        if (member == null) {
            throw new ServiceException("当前账号不是有效商户成员");
        }
        return member;
    }

    private PmMerchantUser requireMember(Long userId) {
        PmMerchantUser member = memberMapper.selectOne(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getUserId, userId)
            .last("limit 1"));
        if (member == null) {
            throw new ServiceException("商户成员不存在");
        }
        MerchantContext.requireAccessibleMerchant(member.getMerchantId());
        return member;
    }

    private List<PmMerchantUser> loadMembers(List<Long> userIds) {
        List<PmMerchantUser> members = memberMapper.selectList(
            new LambdaQueryWrapper<PmMerchantUser>().in(PmMerchantUser::getUserId, userIds));
        if (members.size() != userIds.size()) {
            throw new ServiceException("部分商户成员不存在");
        }
        return members;
    }

    private void transferOwnership(Long merchantId, Long newOwnerId, OffsetDateTime timestamp) {
        List<PmMerchantUser> owners = memberMapper.selectList(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getMerchantId, merchantId)
            .eq(PmMerchantUser::getRoleCode, PaymentConstants.MEMBER_OWNER));
        for (PmMerchantUser owner : owners) {
            if (!owner.getUserId().equals(newOwnerId)) {
                owner.setRoleCode(PaymentConstants.MEMBER_ADMIN);
                owner.setUpdatedAt(timestamp);
                memberMapper.updateById(owner);
                syncSystemRole(owner.getUserId(), PaymentConstants.MEMBER_ADMIN);
            }
        }
        PmMerchant merchant = merchantMapper.selectById(merchantId);
        merchant.setOwnerUserId(newOwnerId);
        merchant.setUpdatedAt(timestamp);
        merchantMapper.updateById(merchant);
    }

    private void ensureAnotherOwner(Long merchantId, Long excludingUserId) {
        long count = memberMapper.selectCount(new LambdaQueryWrapper<PmMerchantUser>()
            .eq(PmMerchantUser::getMerchantId, merchantId)
            .eq(PmMerchantUser::getRoleCode, PaymentConstants.MEMBER_OWNER)
            .eq(PmMerchantUser::getStatus, "0")
            .ne(PmMerchantUser::getUserId, excludingUserId));
        if (count == 0) {
            throw new ServiceException("不允许移除或停用最后一个商户所有者");
        }
    }

    private void syncSystemRole(Long userId, String roleCode) {
        Map<String, Long> roles = Map.of(
            PaymentConstants.MEMBER_OWNER, PaymentConstants.MERCHANT_OWNER_ROLE_ID,
            PaymentConstants.MEMBER_ADMIN, PaymentConstants.MERCHANT_ADMIN_ROLE_ID,
            PaymentConstants.MEMBER_FINANCE, PaymentConstants.MERCHANT_FINANCE_ROLE_ID,
            PaymentConstants.MEMBER_DEVELOPER, PaymentConstants.MERCHANT_DEVELOPER_ROLE_ID,
            PaymentConstants.MEMBER_VIEWER, PaymentConstants.MERCHANT_VIEWER_ROLE_ID);
        memberMapper.revokePaymentRoles(userId);
        memberMapper.grantRole(userId, roles.get(roleCode));
    }

    private MerchantMemberVo toVo(PmMerchantUser member) {
        SysUserVo user = userService.selectUserById(member.getUserId());
        PmAccountMfa mfa = mfaMapper.selectOne(new LambdaQueryWrapper<PmAccountMfa>()
            .eq(PmAccountMfa::getUserId, member.getUserId())
            .last("limit 1"));
        return new MerchantMemberVo(
            member.getUserId(),
            member.getMerchantId(),
            null,
            null,
            user == null ? null : user.getUserName(),
            user == null ? null : user.getNickName(),
            user == null ? null : user.getEmail(),
            member.getRoleCode(),
            member.getStatus(),
            mfa != null && Boolean.TRUE.equals(mfa.getEnabled()),
            member.getCreatedAt());
    }

    private MerchantInvitationVo toInvitationVo(
        PmMerchantInvitation invitation,
        String acceptanceToken
    ) {
        return new MerchantInvitationVo(
            invitation.getId(),
            invitation.getMerchantId(),
            null,
            null,
            invitation.getInvitedEmail(),
            invitation.getRoleCode(),
            invitation.getStatus(),
            invitation.getExpiresAt(),
            acceptanceToken);
    }

    private void enrichMembers(List<MerchantMemberVo> rows) {
        merchantDisplayService.enrich(
            rows,
            MerchantMemberVo::getMerchantId,
            MerchantMemberVo::setMerchantCode,
            MerchantMemberVo::setMerchantName);
    }

    private void enrichInvitations(List<MerchantInvitationVo> rows) {
        merchantDisplayService.enrich(
            rows,
            MerchantInvitationVo::getMerchantId,
            MerchantInvitationVo::setMerchantCode,
            MerchantInvitationVo::setMerchantName);
    }

    private void sendInvitationEmail(
        PmMerchantInvitation invitation,
        String token
    ) {
        String baseUrl = properties.getPublicBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String invitationUrl = baseUrl + "/merchant-invitation/" + token;
        mailOutboxService.enqueueHtml(
            "MERCHANT_INVITATION",
            invitation.getInvitedEmail(),
            "[LuLuPay] 商户成员邀请",
            mailTemplateService.noticeWithAction(
                "加入 LuLuPay 商户",
                "您被邀请加入商户，岗位为 " + MerchantRoleLabels.label(invitation.getRoleCode())
                    + "。邀请将在 "
                    + properties.getOnboarding().getInvitationTtlHours()
                    + " 小时后失效。",
                "查看并接受邀请",
                invitationUrl
            ),
            "MERCHANT_INVITATION:" + invitation.getId(),
            invitation.getExpiresAt()
        );
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        return userId;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
