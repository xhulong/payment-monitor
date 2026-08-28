-- Phase G: multi-merchant isolation, merchant API credentials and suspected duplicates.
-- All additions are backward compatible with the Phase F application.

alter table pm_merchant
    add column if not exists timezone varchar(64) not null default 'Asia/Shanghai',
    add column if not exists remark varchar(500),
    add column if not exists created_by bigint;

update pm_merchant
set name = '默认商户'
where id = 1900000000000000001
  and merchant_code = 'DEFAULT';

create table if not exists pm_merchant_user
(
    id          bigint primary key,
    merchant_id bigint      not null references pm_merchant (id),
    user_id     bigint      not null,
    created_by  bigint,
    created_at  timestamptz not null default now(),
    unique (merchant_id, user_id),
    unique (user_id)
);

create index if not exists idx_pm_merchant_user_merchant
    on pm_merchant_user (merchant_id);

create table if not exists pm_merchant_api_key
(
    id              bigint primary key,
    merchant_id     bigint       not null references pm_merchant (id),
    key_id          varchar(64)  not null unique,
    key_name        varchar(100) not null,
    status          char(1)      not null default '0',
    current_version integer      not null default 1,
    last_used_at    timestamptz,
    created_by      bigint,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    constraint chk_pm_merchant_api_key_status check (status in ('0', '1'))
);

create index if not exists idx_pm_merchant_api_key_merchant
    on pm_merchant_api_key (merchant_id, status);

create table if not exists pm_merchant_api_credential
(
    id                bigint primary key,
    api_key_id        bigint        not null references pm_merchant_api_key (id),
    credential_version integer      not null,
    secret_ciphertext varchar(1024) not null,
    created_at        timestamptz   not null,
    revoked_at        timestamptz,
    unique (api_key_id, credential_version)
);

create unique index if not exists uk_pm_merchant_api_credential_active
    on pm_merchant_api_credential (api_key_id) where revoked_at is null;

alter table pm_qr_asset
    add column if not exists asset_code varchar(64);

update pm_qr_asset
set asset_code = 'QR-' || id::text
where asset_code is null or btrim(asset_code) = '';

alter table pm_qr_asset
    alter column asset_code set not null;

create unique index if not exists uk_pm_qr_asset_merchant_code
    on pm_qr_asset (merchant_id, asset_code);

alter table pm_payment_event
    add column if not exists duplicate_status varchar(16) not null default 'NONE',
    add column if not exists duplicate_of_event_id bigint references pm_payment_event (id),
    add column if not exists duplicate_detected_at timestamptz,
    add column if not exists duplicate_reviewed_at timestamptz,
    add column if not exists duplicate_reviewed_by bigint,
    add column if not exists duplicate_review_note varchar(500);

alter table pm_payment_event
    drop constraint if exists chk_pm_event_duplicate_status;

alter table pm_payment_event
    add constraint chk_pm_event_duplicate_status
        check (duplicate_status in ('NONE', 'SUSPECTED', 'CONFIRMED', 'EXCLUDED'));

create index if not exists idx_pm_event_duplicate_search
    on pm_payment_event (
        merchant_id,
        platform,
        direction,
        amount_minor,
        raw_hash,
        received_at desc
    );

create index if not exists idx_pm_event_duplicate_status
    on pm_payment_event (merchant_id, duplicate_status, received_at desc);

-- Shared payment merchant administrator role. Merchant data scope is enforced by
-- pm_merchant_user and MerchantContext rather than the generic department scope.
insert into sys_role (
    role_id, role_name, role_key, role_sort, data_scope,
    menu_check_strictly, dept_check_strictly, status, del_flag,
    create_dept, create_by, create_time, remark
) values (
    1900200000000000001, '支付商户管理员', 'payment_merchant_admin', 20, '5',
    true, true, '0', '0',
    1761000000000000103, 1761100000000000001, now(), '支付模块商户管理员'
) on conflict (role_id) do nothing;

insert into sys_menu values
    (1900100000000000070, '商户管理', 1900100000000000001, 7, 'merchant', 'payment/merchant/index', '', 'N', 'Y', 'C', '0', '0', 'payment:merchant:list', 'peoples', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '支付商户、用户和 API Key 管理'),
    (1900100000000001070, '新增商户', 1900100000000000070, 1, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant:add', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001071, '修改商户', 1900100000000000070, 2, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant:edit', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001072, '绑定商户用户', 1900100000000000070, 3, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant:bind', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001073, '管理 API Key', 1900100000000000070, 4, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:merchant:key', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, ''),
    (1900100000000001035, '审核疑似重复', 1900100000000000030, 6, '#', '', '', 'N', 'Y', 'F', '0', '0', 'payment:event:duplicate', '#', '', '', 1761000000000000103, 1761100000000000001, now(), null, null, '')
on conflict (menu_id) do nothing;

-- Merchant administrators receive all payment module menus except platform-only
-- merchant creation and global merchant selection capabilities.
insert into sys_role_menu (role_id, menu_id)
select 1900200000000000001, menu_id
from sys_menu
where menu_id in (
    1900100000000000001,
    1900100000000000010,
    1900100000000000020,
    1900100000000000030,
    1900100000000000040,
    1900100000000000050,
    1900100000000000060,
    1900100000000000070,
    1900100000000001020,
    1900100000000001021,
    1900100000000001030,
    1900100000000001031,
    1900100000000001032,
    1900100000000001033,
    1900100000000001034,
    1900100000000001035,
    1900100000000001040,
    1900100000000001041,
    1900100000000001050,
    1900100000000001051,
    1900100000000001052,
    1900100000000001060,
    1900100000000001061,
    1900100000000001062
    ,1900100000000001073
)
on conflict (role_id, menu_id) do nothing;
