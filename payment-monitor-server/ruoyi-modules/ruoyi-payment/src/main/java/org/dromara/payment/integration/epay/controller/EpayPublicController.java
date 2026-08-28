package org.dromara.payment.integration.epay.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.integration.epay.application.EpayOrderFacade;
import org.dromara.payment.integration.epay.domain.vo.EpayOrderStatusVo;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.dromara.payment.integration.epay.protocol.EpayRequestParser;
import org.dromara.payment.security.TrustedClientIpResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@SaIgnore
@RestController
@RequiredArgsConstructor
public class EpayPublicController {
    private static final String PAYMENT_PAGE = """
        <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <meta name="color-scheme" content="light"><title>LuLuPay - 支付订单</title>
        <style>*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#f4f7fb;color:#172033;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}main{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}.card{width:100%;max-width:430px;background:#fff;border:1px solid #e8edf5;border-radius:24px;box-shadow:0 18px 55px rgba(33,55,94,.12);overflow:hidden}.header{padding:23px 25px 17px;background:linear-gradient(135deg,#f8fbff,#eef4ff)}h1{font-size:20px;margin:0}.sub{margin:5px 0 0;color:#7b879c;font-size:13px}.content{padding:21px 25px 25px;text-align:center}.platform{display:inline-flex;padding:7px 12px;border-radius:999px;background:#edf5ff;color:#1769d2;font-size:14px;font-weight:650}.amount-label{margin-top:18px;color:#7b879c;font-size:13px}.amount{margin:4px 0 12px;font-size:42px;font-weight:760}.status{display:inline-flex;padding:6px 11px;border-radius:999px;font-size:13px;font-weight:650}.pending{background:#fff4db;color:#9b6400}.success{background:#e7f8ef;color:#08783f}.closed{background:#f1f3f7;color:#667085}.qr{width:270px;height:270px;margin:20px auto 13px;padding:14px;border:1px solid #e5eaf2;border-radius:22px;box-shadow:0 10px 30px rgba(26,49,90,.09)}.qr img{width:100%;height:100%}.hint{margin:0;font-size:15px;font-weight:650}.hint-sub{margin:6px 0 0;color:#7b879c;font-size:13px}.details{margin-top:20px;padding-top:16px;border-top:1px solid #edf0f5;text-align:left}.row{display:flex;justify-content:space-between;gap:15px;padding:7px 0;font-size:13px}.row span:first-child{color:#8a95a8}.row span:last-child{text-align:right;word-break:break-all}.error{color:#c0392b;font-size:13px}.footer{padding:14px 22px;background:#fafbfc;color:#8a95a8;text-align:center;font-size:12px}[hidden]{display:none!important}@media(max-width:480px){main{align-items:flex-start;padding:12px}.qr{width:min(270px,78vw);height:min(270px,78vw)}}
        </style></head><body><main><section class="card"><header class="header"><h1>LuLuPay 码支付</h1><p class="sub">请核对金额后扫码完成支付</p></header><div class="content"><div id="platform" class="platform">加载中</div><div class="amount-label">应付金额</div><div id="amount" class="amount">--</div><div id="status" class="status pending">加载中</div><div id="qr" class="qr" hidden><img id="qr-image" alt="订单收款二维码"></div><p id="hint" class="hint">正在获取订单信息</p><p id="hint-sub" class="hint-sub">请稍候</p><p id="error" class="error" hidden></p><div class="details"><div class="row"><span>商户订单号</span><span id="order-no">--</span></div><div class="row"><span>剩余时间</span><span id="countdown">--</span></div></div></div><footer class="footer">LuLuPay · 当前依据到账通知确认支付状态</footer></section></main>
        <script>const token="__TOKEN__";const api="/epay/public/orders/"+token;let latest=null;const el=id=>document.getElementById(id);const platform=v=>v==="WECHAT"?"微信支付":"支付宝";const money=v=>"¥ "+(Number(v||0)/100).toFixed(2);function countdown(){if(!latest||latest.status!=="PENDING"){el("countdown").textContent="--";return}const s=Math.max(0,Math.floor((new Date(latest.expiresAt).getTime()-Date.now())/1000));el("countdown").textContent=Math.floor(s/60)+":"+String(s%60).padStart(2,"0")}function render(o){latest=o;el("platform").textContent=platform(o.platform);el("amount").textContent=money(o.payableAmountMinor);el("order-no").textContent=o.outTradeNo;const pending=o.status==="PENDING";el("qr").hidden=!pending;if(pending)el("qr-image").src=o.qrImageUrl;if(o.success){el("status").className="status success";el("status").textContent="支付已确认";el("hint").textContent="支付已完成";el("hint-sub").textContent=o.returnReady?"正在返回商户页面":"可关闭当前页面";if(o.returnReady)setTimeout(()=>location.replace("/epay/return/"+token),1200)}else if(pending){el("status").className="status pending";el("status").textContent="待支付";el("hint").textContent="请使用"+platform(o.platform)+"扫码支付";el("hint-sub").textContent="支付后页面会自动更新"}else{el("status").className="status closed";el("status").textContent=o.status==="PAID"?"等待确认":"订单已关闭";el("hint").textContent=o.status==="PAID"?"到账通知已匹配，等待配置的确认等级":"请重新创建订单";el("hint-sub").textContent=""}countdown()}async function load(){try{const r=await fetch(api,{cache:"no-store"});if(!r.ok)throw new Error();render(await r.json());el("error").hidden=true}catch(e){el("error").textContent="订单信息加载失败，请刷新重试";el("error").hidden=false}}load();setInterval(load,3000);setInterval(countdown,1000);</script></body></html>
        """;

    private final EpayOrderFacade facade;
    private final EpayRequestParser parser;
    private final TrustedClientIpResolver clientIpResolver;

    @GetMapping({"/submit.php", "/pay/submit.php", "/epay/submit.php"})
    public ResponseEntity<?> submitGet(@RequestParam MultiValueMap<String, String> values) {
        return submit(values);
    }

    @PostMapping(value = {"/submit.php", "/pay/submit.php", "/epay/submit.php"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> submitPost(@RequestParam MultiValueMap<String, String> values) {
        return submit(values);
    }

    @PostMapping(value = {"/mapi.php", "/pay/mapi.php", "/epay/mapi.php"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> mapi(@RequestParam MultiValueMap<String, String> values) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(facade.create(parser.parse(values)).response());
        } catch (EpayException exception) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(error(exception.getMessage()));
        }
    }

    @GetMapping(value = {"/api.php", "/pay/api.php", "/epay/api.php"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> apiGet(
        @RequestParam MultiValueMap<String, String> values,
        HttpServletRequest request
    ) {
        return api(values, request);
    }

    @PostMapping(value = {"/api.php", "/pay/api.php", "/epay/api.php"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> apiPost(
        @RequestParam MultiValueMap<String, String> values,
        HttpServletRequest request
    ) {
        return api(values, request);
    }

    @GetMapping(value = "/epay/public/orders/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public EpayOrderStatusVo status(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        return facade.publicStatus(token);
    }

    @GetMapping(value = "/epay/pay/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> paymentPage(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        facade.publicStatus(token);
        String html = PAYMENT_PAGE.replace("__TOKEN__", token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; connect-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'")
            .header("Referrer-Policy", "no-referrer")
            .header("X-Frame-Options", "DENY")
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/epay/return/{token}")
    public ResponseEntity<Void> returnRedirect(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        URI target = facade.returnTarget(token);
        return ResponseEntity.status(HttpStatus.FOUND).location(target)
            .cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer")
            .build();
    }

    private ResponseEntity<?> submit(MultiValueMap<String, String> values) {
        try {
            var result = facade.create(parser.parse(values));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.payUrl()))
                .cacheControl(CacheControl.noStore()).build();
        } catch (EpayException exception) {
            String html = "<!doctype html><meta charset=\"utf-8\"><title>支付请求失败</title><p>" +
                escape(exception.getMessage()) + "</p>";
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ResponseEntity<Map<String, Object>> api(
        MultiValueMap<String, String> values,
        HttpServletRequest request
    ) {
        try {
            Map<String, String> parameters = parser.parse(values);
            if (!"order".equals(parameters.get("act"))) throw new EpayException("不支持的接口操作");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(facade.query(parameters, isSecure(request)));
        } catch (EpayException exception) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(error(exception.getMessage()));
        }
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return clientIpResolver.isTrustedProxy(request)
            && proto != null && !proto.contains(",")
            && "https".equalsIgnoreCase(proto.trim());
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", -1); result.put("msg", message); return result;
    }

    private String escape(String value) {
        return value == null ? "请求处理失败" : value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
