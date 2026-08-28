-- Phase K: personal merchant onboarding, merchant teams, MFA and Android releases.
-- The migration is additive and keeps Phase J applications able to run against
-- the upgraded schema during a rollback.

alter table pm_merchant
    add column if not exists lifecycle_status varchar(16) not null default 'ACTIVE',
    add column if not exists owner_user_id bigint,
    add column if not exists agreement_version varchar(32),
    add column if not exists privacy_version varchar(32),
    add column if not exists onboarding_completed_at timestamptz,
    add column if not exists quota_config jsonb not null default '{}'::jsonb;

alter table pm_merchant
    drop constraint if exists chk_pm_merchant_lifecycle;
alter table pm_merchant
    add constraint chk_pm_merchant_lifecycle
        check (lifecycle_status in ('ONBOARDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'));

update pm_merchant
set lifecycle_status = 'ACTIVE',
    onboarding_completed_at = coalesce(onboarding_completed_at, updated_at, created_at, now())
where lifecycle_status is null or lifecycle_status = '';

alter table pm_merchant_user
    add column if not exists role_code varchar(16) not null default 'ADMIN',
    add column if not exists status char(1) not null default '0',
    add column if not exists invited_by bigint,
    add column if not exists updated_at timestamptz;

alter table pm_merchant_user
    drop constraint if exists chk_pm_merchant_user_role;
alter table pm_merchant_user
    add constraint chk_pm_merchant_user_role
        check (role_code in ('OWNER', 'ADMIN', 'FINANCE', 'DEVELOPER', 'VIEWER'));
alter table pm_merchant_user
    drop constraint if exists chk_pm_merchant_user_status;
alter table pm_merchant_user
    add constraint chk_pm_merchant_user_status check (status in ('0', '1'));

with ranked as (
    select id, merchant_id, user_id,
           row_number() over (partition by merchant_id order by created_at, id) as rn
    from pm_merchant_user
)
update pm_merchant_user u
set role_code = case when ranked.rn = 1 then 'OWNER' else 'ADMIN' end,
    updated_at = coalesce(u.updated_at, u.created_at, now())
from ranked
where ranked.id = u.id;

update pm_merchant m
set owner_user_id = owners.user_id
from (
    select distinct on (merchant_id) merchant_id, user_id
    from pm_merchant_user
    where role_code = 'OWNER'
    order by merchant_id, created_at, id
) owners
where owners.merchant_id = m.id
  and m.owner_user_id is null;

create table if not exists pm_merchant_application
(
    id                    bigint primary key,
    user_id               bigint       not null,
    verified_email        varchar(100) not null,
    merchant_display_name varchar(120) not null,
    applicant_name        varchar(80)  not null,
    phone_number          varchar(32),
    country_region        varchar(80)  not null,
    province              varchar(80),
    city                  varchar(80),
    payment_use_case      varchar(1000) not null,
    monthly_order_range   varchar(64)  not null,
    monthly_amount_range  varchar(64)  not null,
    planned_platforms     varchar(64)  not null,
    agreement_version     varchar(32)  not null,
    privacy_version       varchar(32)  not null,
    status                varchar(24)  not null,
    submission_snapshot   jsonb,
    reviewer_id           bigint,
    review_note           varchar(1000),
    claimed_at            timestamptz,
    submitted_at          timestamptz,
    reviewed_at           timestamptz,
    cooldown_until        timestamptz,
    merchant_id           bigint references pm_merchant (id),
    version               integer      not null default 0,
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    constraint chk_pm_merchant_application_status check (
        status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CHANGES',
                   'APPROVED', 'REJECTED', 'WITHDRAWN')
    )
);

create unique index if not exists uk_pm_merchant_application_active_user
    on pm_merchant_application (user_id)
    where status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CHANGES');
create index if not exists idx_pm_merchant_application_status_time
    on pm_merchant_application (status, submitted_at desc, created_at desc);

create table if not exists pm_merchant_application_history
(
    id              bigint primary key,
    application_id  bigint       not null references pm_merchant_application (id),
    user_id         bigint       not null,
    action          varchar(32)  not null,
    from_status     varchar(24),
    to_status       varchar(24)  not null,
    snapshot        jsonb,
    note            varchar(1000),
    operated_by     bigint,
    operated_at     timestamptz  not null
);

create index if not exists idx_pm_merchant_application_history
    on pm_merchant_application_history (application_id, operated_at);

create table if not exists pm_merchant_invitation
(
    id              bigint primary key,
    merchant_id     bigint       not null references pm_merchant (id),
    invited_email   varchar(100) not null,
    role_code       varchar(16)  not null,
    token_hash      varchar(64)  not null unique,
    status          varchar(16)  not null,
    invited_by      bigint       not null,
    accepted_by     bigint,
    expires_at      timestamptz  not null,
    accepted_at     timestamptz,
    cancelled_at    timestamptz,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    constraint chk_pm_merchant_invitation_role check (
        role_code in ('OWNER', 'ADMIN', 'FINANCE', 'DEVELOPER', 'VIEWER')
    ),
    constraint chk_pm_merchant_invitation_status check (
        status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'CANCELLED')
    )
);

create index if not exists idx_pm_merchant_invitation_merchant
    on pm_merchant_invitation (merchant_id, status, created_at desc);

create table if not exists pm_account_mfa
(
    id                     bigint primary key,
    user_id                bigint        not null unique,
    totp_secret_ciphertext varchar(1024) not null,
    enabled                boolean       not null default false,
    recovery_code_hashes   jsonb         not null default '[]'::jsonb,
    last_used_time_step    bigint,
    enabled_at             timestamptz,
    last_used_at           timestamptz,
    created_at             timestamptz   not null,
    updated_at             timestamptz   not null
);

create table if not exists pm_app_release
(
    id                         bigint primary key,
    platform                   varchar(16)   not null,
    version_code               integer       not null,
    version_name               varchar(64)   not null,
    min_supported_version_code integer       not null,
    enforcement_at             timestamptz,
    object_key                 varchar(512)  not null,
    file_size                  bigint        not null,
    sha256                     varchar(64)   not null,
    signing_certificate_sha256 varchar(64)   not null,
    release_notes              varchar(4000),
    status                     varchar(16)   not null,
    published_at               timestamptz,
    created_by                 bigint,
    created_at                 timestamptz   not null,
    updated_at                 timestamptz   not null,
    unique (platform, version_code),
    constraint chk_pm_app_release_platform check (platform in ('ANDROID')),
    constraint chk_pm_app_release_status check (status in ('DRAFT', 'PUBLISHED', 'REVOKED')),
    constraint chk_pm_app_release_version check (
        version_code > 0 and min_supported_version_code > 0
        and min_supported_version_code <= version_code
    )
);

create index if not exists idx_pm_app_release_latest
    on pm_app_release (platform, status, version_code desc);

alter table pm_device
    add column if not exists app_version_code integer,
    add column if not exists update_required_at timestamptz;

update sys_user
set email = lower(trim(email))
where email is not null and trim(email) <> '';

create unique index if not exists uk_sys_user_email_normalized
    on sys_user (lower(email))
    where email is not null and email <> '' and del_flag = '0';

-- Shared system roles. Merchant data isolation remains enforced by
-- pm_merchant_user and MerchantContext.
insert into sys_role (
    role_id, role_name, role_key, role_sort, data_scope,
    menu_check_strictly, dept_check_strictly, status, del_flag,
    create_dept, create_by, create_time, remark
) values
    (1900200000000000002, '支付商户申请人', 'payment_merchant_applicant', 21, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), '个人商户入驻申请'),
    (1900200000000000003, '支付平台审核员', 'payment_platform_reviewer', 22, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), '个人商户资料审核'),
    (1900200000000000004, '支付商户所有者', 'payment_merchant_owner', 23, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), '商户所有者'),
    (1900200000000000005, '支付商户财务', 'payment_merchant_finance', 24, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), '交易、审批和对账'),
    (1900200000000000006, '支付商户开发者', 'payment_merchant_developer', 25, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), 'API Key 与 Webhook'),
    (1900200000000000007, '支付商户只读', 'payment_merchant_viewer', 26, '5', true, true, '0', '0', 1761000000000000103, 1761100000000000001, now(), '只读查询')
on conflict (role_id) do nothing;

insert into sys_menu values
    (1900100000000000140, '商户入驻', 1900100000000000001, 0, 'onboarding', 'payment/onboarding/index', '', 'N', 'Y', 'C', '0', '0', 'payment:onboarding:view', 'guide', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '个人商户申请与开通向导'),
    (1900100000000000110, '商户入驻审核', 1900100000000000001, 11, 'merchant-application', 'payment/merchant-application/index', '', 'N', 'Y', 'C', '0', '0', 'payment:merchant-application:list', 'audit', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '个人商户申请审核工作台'),
    (1900100000000000111, '审核商户申请', 1900100000000000110, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant-application:review', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000000120, '商户成员', 1900100000000000001, 12, 'member', 'payment/member/index', '', 'N', 'Y', 'C', '0', '0', 'payment:merchant-member:list', 'peoples', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '商户成员、邀请和岗位权限'),
    (1900100000000000121, '管理商户成员', 1900100000000000120, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant-member:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000000130, 'App 版本发布', 1900100000000000001, 13, 'app-release', 'payment/app-release/index', '', 'N', 'Y', 'C', '0', '0', 'payment:app-release:list', 'download', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, 'Android 安装包版本管理'),
    (1900100000000000131, '发布 App 版本', 1900100000000000130, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:app-release:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000000150, '账号安全', 1900100000000000001, 14, 'account-security', 'account/security/index', '', 'N', 'Y', 'C', '0', '0', 'payment:account-security:view', 'lock', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, 'TOTP 与恢复码管理')
on conflict (menu_id) do nothing;

insert into sys_role_menu (role_id, menu_id)
values
    (1900200000000000003, 1900100000000000001),
    (1900200000000000003, 1900100000000000110),
    (1900200000000000003, 1900100000000000111),
    (1900200000000000003, 1900100000000000150)
on conflict (role_id, menu_id) do nothing;

-- Owners inherit the previous merchant-admin capabilities plus team management.
insert into sys_role_menu (role_id, menu_id)
select 1900200000000000004, menu_id
from sys_role_menu
where role_id = 1900200000000000001
on conflict (role_id, menu_id) do nothing;
insert into sys_role_menu (role_id, menu_id)
values
    (1900200000000000004, 1900100000000000140),
    (1900200000000000004, 1900100000000000120),
    (1900200000000000004, 1900100000000000121),
    (1900200000000000004, 1900100000000000150)
on conflict (role_id, menu_id) do nothing;

-- Existing merchant administrators also receive the team page.
insert into sys_role_menu (role_id, menu_id)
values
    (1900200000000000001, 1900100000000000120),
    (1900200000000000001, 1900100000000000121),
    (1900200000000000001, 1900100000000000150)
on conflict (role_id, menu_id) do nothing;

-- Platform administrators keep release management through their existing
-- super-admin permission bypass. The explicit menus make navigation visible.
insert into sys_role_menu (role_id, menu_id)
select role_id, menu_id
from (values
    (1900200000000000003::bigint, 1900100000000000130::bigint),
    (1900200000000000003::bigint, 1900100000000000131::bigint)
) v(role_id, menu_id)
on conflict (role_id, menu_id) do nothing;

-- Applicant navigation is intentionally limited to onboarding and account
-- security. The home route remains available as a shell route in the SPA.
insert into sys_role_menu (role_id, menu_id)
values
    (1900200000000000002, 1900100000000000001),
    (1900200000000000002, 1900100000000000140),
    (1900200000000000002, 1900100000000000150)
on conflict (role_id, menu_id) do nothing;

-- Finance can confirm transactions, participate in two-person approvals and
-- operate internal reconciliation, but cannot manage devices, API keys or
-- Webhook configuration.
insert into sys_role_menu (role_id, menu_id)
select 1900200000000000005, menu_id
from unnest(array[
    1900100000000000001::bigint,
    1900100000000000010::bigint,
    1900100000000000030::bigint,
    1900100000000001030::bigint,
    1900100000000000050::bigint,
    1900100000000001052::bigint,
    1900100000000000080::bigint,
    1900100000000001080::bigint,
    1900100000000001081::bigint,
    1900100000000000090::bigint,
    1900100000000001090::bigint,
    1900100000000000100::bigint,
    1900100000000001100::bigint,
    1900100000000001101::bigint,
    1900100000000000150::bigint
]) menu_id
on conflict (role_id, menu_id) do nothing;

-- Developers own integration-facing configuration while payment confirmation
-- and reconciliation remain outside their role.
insert into sys_role_menu (role_id, menu_id)
select 1900200000000000006, menu_id
from unnest(array[
    1900100000000000001::bigint,
    1900100000000000010::bigint,
    1900100000000000020::bigint,
    1900100000000001020::bigint,
    1900100000000000040::bigint,
    1900100000000001040::bigint,
    1900100000000001041::bigint,
    1900100000000000060::bigint,
    1900100000000001060::bigint,
    1900100000000001061::bigint,
    1900100000000001062::bigint,
    1900100000000000070::bigint,
    1900100000000001073::bigint,
    1900100000000000150::bigint
]) menu_id
on conflict (role_id, menu_id) do nothing;

-- Viewer receives query-only pages and no function-button permissions.
insert into sys_role_menu (role_id, menu_id)
select 1900200000000000007, menu_id
from unnest(array[
    1900100000000000001::bigint,
    1900100000000000010::bigint,
    1900100000000000020::bigint,
    1900100000000000030::bigint,
    1900100000000001030::bigint,
    1900100000000000040::bigint,
    1900100000000000050::bigint,
    1900100000000000060::bigint,
    1900100000000000080::bigint,
    1900100000000000090::bigint,
    1900100000000000100::bigint,
    1900100000000000150::bigint
]) menu_id
on conflict (role_id, menu_id) do nothing;
