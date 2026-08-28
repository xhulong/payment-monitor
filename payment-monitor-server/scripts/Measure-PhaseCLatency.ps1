param(
    [Parameter(Mandatory = $true)]
    [long]$DeviceId,
    [datetimeoffset]$SinceUtc = [datetimeoffset]::UtcNow.AddHours(-24)
)

$ErrorActionPreference = 'Stop'
$since = $SinceUtc.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$sql = @"
with samples as (
    select
        platform,
        floor(extract(epoch from received_at) * 1000)::bigint - event_time_ms as total_ms,
        client_received_at_ms - event_time_ms as listener_ms,
        client_sent_at_ms - client_received_at_ms as client_sync_ms
    from pm_payment_event
    where device_id = $DeviceId
      and direction = 'INCOME'
      and event_time_ms is not null
      and client_received_at_ms is not null
      and client_sent_at_ms is not null
      and received_at >= '$since'::timestamptz
)
select
    platform,
    count(*) as sample_count,
    round(avg(total_ms), 2) as average_total_ms,
    round((percentile_cont(0.95) within group(order by total_ms))::numeric, 2) as p95_total_ms,
    max(total_ms) as maximum_total_ms,
    round(avg(listener_ms), 2) as average_listener_ms,
    round(avg(client_sync_ms), 2) as average_client_sync_ms
from samples
group by platform
order by platform;
"@

& docker exec payment-monitor-postgres `
    psql -U payment_monitor -d payment_monitor `
    -P pager=off -c $sql
if ($LASTEXITCODE -ne 0) {
    throw "Latency query failed with exit code $LASTEXITCODE"
}
