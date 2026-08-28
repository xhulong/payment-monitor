package com.example.paymentmonitor.sync

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

private val MILLIS_INSTANT_FORMATTER: DateTimeFormatter =
    DateTimeFormatterBuilder().appendInstant(3).toFormatter()

fun epochMillisToUtcIso(epochMillis: Long): String =
    MILLIS_INSTANT_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

fun currentUtcIsoMillis(nowProvider: () -> Long = System::currentTimeMillis): String =
    epochMillisToUtcIso(nowProvider())
