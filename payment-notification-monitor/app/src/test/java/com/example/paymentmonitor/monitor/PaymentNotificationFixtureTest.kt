package com.example.paymentmonitor.monitor

import com.example.paymentmonitor.model.RawNotificationData
import com.google.gson.Gson
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationFixtureTest {
    @Test
    fun reviewedPhaseCFixturesMatchExpectedParserResults() {
        val fixtures = listOf(
            "payment-fixtures/v2/baseline.json",
            "payment-fixtures/v2/real-20260717.json",
            "payment-fixtures/v3/phase-l-desensitized.json",
        ).associateWith(::loadFixture)
        val phaseL = fixtures.getValue(
            "payment-fixtures/v3/phase-l-desensitized.json",
        )

        assertEquals(60, phaseL.cases.size)
        assertEquals(25, phaseL.cases.count { it.group == "WECHAT" })
        assertEquals(25, phaseL.cases.count { it.group == "ALIPAY" })
        assertEquals(10, phaseL.cases.count { it.group == "EDGE_NEGATIVE" })
        assertEquals(60, phaseL.cases.count(FixtureCase::reviewed))
        assertTrue(
            phaseL.cases.count {
                it.group == "EDGE_NEGATIVE" && !it.expected.matched
            } >= 4,
        )

        val cases = fixtures.values.flatMap(FixtureFile::cases)
        assertTrue(cases.size >= 73)
        cases.filter(FixtureCase::reviewed).forEach { case ->
            val event = PaymentNotificationParser.parse(case.raw.toModel(), receivedAt = 1)
            if (!case.expected.matched) {
                assertNull(case.caseId, event)
                return@forEach
            }
            assertNotNull(case.caseId, event)
            assertEquals(case.caseId, case.expected.platform, event?.platform?.name)
            assertEquals(case.caseId, case.expected.direction, event?.direction?.name)
            assertEquals(case.caseId, case.expected.parseStatus, event?.parseStatus?.name)
            assertEquals(
                case.caseId,
                case.expected.amount?.let(::BigDecimal),
                event?.amount,
            )
        }

        val parsedById = cases.associate { case ->
            case.caseId to PaymentNotificationParser.parse(case.raw.toModel(), receivedAt = 1)
        }
        assertNotEquals(
            parsedById.getValue("edge_same_amount_a")?.fingerprint,
            parsedById.getValue("edge_same_amount_b")?.fingerprint,
        )
        assertNotEquals(
            parsedById.getValue("edge_notification_update_original")?.fingerprint,
            parsedById.getValue("edge_notification_update_changed")?.fingerprint,
        )
    }

    private fun loadFixture(resource: String): FixtureFile {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource))
        return stream.reader(Charsets.UTF_8).use {
            Gson().fromJson(it, FixtureFile::class.java)
        }.also { fixture ->
            assertEquals(1, fixture.schema)
        }
    }

    private data class FixtureFile(
        val schema: Int,
        val cases: List<FixtureCase>,
    )

    private data class FixtureCase(
        val caseId: String,
        val group: String?,
        val reviewed: Boolean,
        val expected: Expected,
        val raw: RawFixture,
    )

    private data class Expected(
        val matched: Boolean,
        val platform: String?,
        val direction: String?,
        val parseStatus: String?,
        val amount: String?,
    )

    private data class RawFixture(
        val packageName: String,
        val notificationId: Int,
        val notificationTag: String?,
        val notificationKey: String?,
        val postTime: Long,
        val title: String?,
        val text: String?,
        val bigText: String?,
        val textLines: List<String> = emptyList(),
        val ticker: String?,
        val subText: String?,
        val infoText: String?,
        val summaryText: String?,
        val extras: Map<String, String> = emptyMap(),
    ) {
        fun toModel() = RawNotificationData(
            packageName = packageName,
            notificationId = notificationId,
            notificationTag = notificationTag,
            notificationKey = notificationKey,
            postTime = postTime,
            title = title,
            text = text,
            bigText = bigText,
            textLines = textLines,
            ticker = ticker,
            subText = subText,
            infoText = infoText,
            summaryText = summaryText,
            extras = extras,
        )
    }
}
