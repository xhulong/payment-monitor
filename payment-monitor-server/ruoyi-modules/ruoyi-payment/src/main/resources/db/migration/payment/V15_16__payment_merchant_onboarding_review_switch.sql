insert into sys_config (
    config_id,
    config_name,
    config_key,
    config_value,
    config_type,
    create_dept,
    create_by,
    create_time,
    update_by,
    update_time,
    remark
)
select
    1900300000000000001,
    '商户入驻-是否开启人工审核',
    'payment.merchant.onboarding.reviewEnabled',
    'true',
    'Y',
    1761000000000000103,
    1761100000000000001,
    now(),
    null,
    null,
    'true：新申请进入人工审核；false：新申请提交后自动通过'
where not exists (
    select 1
    from sys_config
    where config_key = 'payment.merchant.onboarding.reviewEnabled'
)
on conflict (config_id) do nothing;

insert into sys_menu values
    (
        1900100000000000112,
        '修改商户入驻审核设置',
        1900100000000000110,
        2,
        '#',
        '',
        '',
        'N',
        'Y',
        'F',
        '0',
        '0',
        'payment:merchant-application:settings',
        '#',
        '',
        '',
        1761000000000000103,
        1761100000000000001,
        now(),
        null,
        null,
        '修改商户入驻是否启用人工审核'
    )
on conflict (menu_id) do nothing;
