package org.dromara.common.encrypt.v2.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Captures a response body before api-crypto-v2 encryption.
 */
public class ApiCryptoV2ResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PrintWriter writer;

    public ApiCryptoV2ResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                output.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                output.write(b, off, len);
            }
        };
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        }
        return writer;
    }

    public byte[] body() {
        if (writer != null) {
            writer.flush();
        }
        return output.toByteArray();
    }
}
