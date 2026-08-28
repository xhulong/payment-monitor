package org.dromara.payment.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.payment.domain.vo.PaymentEventExportVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PaymentEventExportTest {

    @Test
    void formatsOffsetDateTimeInMerchantTimezone() {
        String formatted = PaymentEventService.formatExportTime(
            OffsetDateTime.parse("2026-07-20T05:49:01Z"),
            ZoneId.of("Asia/Shanghai"));

        assertEquals("2026-07-20 13:49:01 +08:00", formatted);
        assertNull(PaymentEventService.formatExportTime(null, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void writesReadableWorkbookWithStringTimeColumns() throws Exception {
        PaymentEventExportVo row = new PaymentEventExportVo();
        row.setId(1001L);
        row.setDeviceId(2001L);
        row.setPlatform("WECHAT");
        row.setDirection("INCOME");
        row.setAmountMinor(1234L);
        row.setCurrency("CNY");
        row.setParseStatus("PARSED");
        row.setStatus("RECEIVED");
        row.setEventTime("2026-07-20 13:48:59 +08:00");
        row.setReceivedAt("2026-07-20 13:49:01 +08:00");
        row.setSyncLatencyMs(2000L);
        row.setMatchedRule("wechat_income");
        row.setReviewedAt(null);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelBuilder.of(List.of(row), PaymentEventExportVo.class)
            .sheetName("支付事件")
            .toStream(output);

        assertTrue(output.size() > 0);
        try (Workbook workbook = WorkbookFactory.create(
            new ByteArrayInputStream(output.toByteArray()))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertEquals("2026-07-20 13:48:59 +08:00",
                dataRow.getCell(10).getStringCellValue());
            assertEquals("2026-07-20 13:49:01 +08:00",
                dataRow.getCell(11).getStringCellValue());
            Cell reviewedAt = dataRow.getCell(14);
            assertTrue(reviewedAt == null || reviewedAt.getStringCellValue().isEmpty());
        }
    }

    @Test
    void writesReadableWorkbookWhenExportHasNoRows() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelBuilder.of(List.<PaymentEventExportVo>of(), PaymentEventExportVo.class)
            .sheetName("支付事件")
            .toStream(output);

        try (Workbook workbook = WorkbookFactory.create(
            new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals("支付事件", workbook.getSheetAt(0).getSheetName());
            assertTrue(workbook.getSheetAt(0).getPhysicalNumberOfRows() >= 1);
        }
    }
}
