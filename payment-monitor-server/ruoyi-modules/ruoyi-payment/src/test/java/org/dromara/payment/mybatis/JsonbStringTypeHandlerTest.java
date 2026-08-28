package org.dromara.payment.mybatis;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.dromara.payment.domain.PmMerchant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class JsonbStringTypeHandlerTest {

    private final JsonbStringTypeHandler handler = new JsonbStringTypeHandler();

    @Test
    void merchantEntityUsesJsonbHandlerForQuotaConfig() throws Exception {
        TableName tableName = PmMerchant.class.getAnnotation(TableName.class);
        TableField tableField =
            PmMerchant.class.getDeclaredField("quotaConfig").getAnnotation(TableField.class);

        assertTrue(tableName.autoResultMap());
        assertEquals(JsonbStringTypeHandler.class, tableField.typeHandler());
    }

    @Test
    void bindsJsonAsPostgresJsonb() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, "{\"maxDevices\":5}", null);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(statement).setObject(eq(1), captor.capture());
        PGobject value = (PGobject) captor.getValue();
        assertEquals("jsonb", value.getType());
        assertEquals("{\"maxDevices\":5}", value.getValue());
    }

    @Test
    void readsJsonbAsPlainJsonString() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue("{\"maxDevices\": 5}");
        when(resultSet.getObject("quota_config")).thenReturn(value);

        assertEquals(
            "{\"maxDevices\": 5}",
            handler.getNullableResult(resultSet, "quota_config")
        );
    }
}
