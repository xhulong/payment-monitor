update pm_mail_server_config
set from_name = 'LuLuPay',
    updated_at = now(),
    version = version + 1
where from_name in ('噜噜', 'LULU');
