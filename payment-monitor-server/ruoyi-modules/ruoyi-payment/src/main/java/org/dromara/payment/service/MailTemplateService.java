package org.dromara.payment.service;

import org.springframework.stereotype.Service;

@Service
public class MailTemplateService {

    public String code(String title, String description, String code, String expiry) {
        return layout(
            title,
            "<p>" + escape(description) + "</p>"
                + "<div style=\"margin:22px 0;padding:18px;border-radius:14px;"
                + "background:#f1f4ff;color:#4f5ed7;font-size:30px;font-weight:800;"
                + "letter-spacing:8px;text-align:center\">"
                + escape(code)
                + "</div><p style=\"color:#7b889e\">验证码 "
                + escape(expiry)
                + " 内有效，请勿转发给他人。</p>"
        );
    }

    public String notice(String title, String message) {
        return layout(title, "<p>" + escape(message) + "</p>");
    }

    public String noticeWithAction(
        String title,
        String message,
        String actionLabel,
        String actionUrl
    ) {
        return layout(
            title,
            "<p>" + escape(message) + "</p>"
                + "<p style=\"margin:24px 0\"><a href=\""
                + escapeAttribute(actionUrl)
                + "\" style=\"display:inline-block;padding:11px 18px;border-radius:10px;"
                + "background:#5b67f1;color:#fff;text-decoration:none;font-weight:700\">"
                + escape(actionLabel)
                + "</a></p>"
        );
    }

    private String layout(String title, String body) {
        return """
            <!doctype html>
            <html lang="zh-CN">
            <body style="margin:0;padding:24px;background:#f5f7fb">
              <div style="max-width:600px;margin:0 auto;overflow:hidden;border:1px solid #e7ebf3;
                   border-radius:18px;background:#fff;font-family:Arial,'Microsoft YaHei',sans-serif;
                   color:#17243d;line-height:1.75">
                <div style="padding:22px 28px;background:linear-gradient(135deg,#5b67f1,#24c6c8);
                     color:#fff">
                  <div style="font-size:22px;font-weight:800;letter-spacing:.4px">LuLuPay</div>
                  <div style="margin-top:4px;font-size:12px;font-weight:700;letter-spacing:2px">码支付</div>
                </div>
                <div style="padding:28px">
                  <h2 style="margin:0 0 14px;font-size:22px">%s</h2>
                  %s
                  <p style="margin:26px 0 0;color:#98a2b3;font-size:12px">
                    此邮件由 LuLuPay 系统自动发送，请勿直接回复。
                  </p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(escape(title), body);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String escapeAttribute(String value) {
        return escape(value);
    }
}
