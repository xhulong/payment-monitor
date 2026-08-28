package org.dromara.payment.controller.api;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.domain.vo.PublicPaymentOrderVo;
import org.dromara.payment.service.PaymentOrderService;
import org.dromara.payment.util.QrCodeSvgRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@SaIgnore
@Validated
@RestController
@RequiredArgsConstructor
public class PublicPaymentPageController {
    private static final String PAYMENT_PAGE_TEMPLATE = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <meta name="color-scheme" content="light">
          <title>LuLuPay - 支付订单</title>
          <style>
            *{box-sizing:border-box}
            body{margin:0;min-height:100vh;background:#f4f7fb;color:#172033;
              font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",
              "Microsoft YaHei",sans-serif}
            main{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
            .card{width:100%;max-width:430px;background:#fff;border:1px solid #e8edf5;
              border-radius:24px;box-shadow:0 18px 55px rgba(33,55,94,.12);overflow:hidden}
            .header{padding:24px 26px 18px;background:linear-gradient(135deg,#f8fbff,#eef4ff)}
            .brand{display:flex;align-items:center;gap:12px}
            .brand-icon{width:42px;height:42px;border-radius:14px;
              background:linear-gradient(135deg,#6257f6,#24c6c8);color:#fff;
              display:grid;place-items:center;font-size:16px;font-weight:900;letter-spacing:-2px}
            h1{font-size:20px;margin:0}.sub{margin:4px 0 0;color:#7b879c;font-size:13px}
            .content{padding:22px 26px 26px;text-align:center}
            .platform{display:inline-flex;align-items:center;gap:7px;padding:7px 12px;
              border-radius:999px;background:#edf5ff;color:#1769d2;font-size:14px;font-weight:650}
            .platform-dot{width:8px;height:8px;border-radius:50%;background:currentColor}
            .amount-label{margin-top:20px;color:#7b879c;font-size:13px}
            .amount{margin:4px 0 12px;font-size:42px;line-height:1.1;font-weight:760;letter-spacing:-1px}
            .status{display:inline-flex;padding:6px 11px;border-radius:999px;font-size:13px;font-weight:650}
            .status.pending{background:#fff4db;color:#9b6400}
            .status.success{background:#e7f8ef;color:#08783f}
            .status.closed{background:#f1f3f7;color:#667085}
            .qr-shell{width:270px;height:270px;margin:22px auto 14px;padding:14px;border-radius:22px;
              background:#fff;border:1px solid #e5eaf2;box-shadow:0 10px 30px rgba(26,49,90,.09);
              display:grid;place-items:center}
            .qr-shell[hidden]{display:none}
            #qr-image{display:block;width:100%;height:100%}
            .hint{margin:0;font-size:15px;font-weight:650}.hint-sub{margin:6px 0 0;color:#7b879c;font-size:13px}
            .result-icon{display:none;width:76px;height:76px;margin:24px auto 14px;border-radius:50%;
              place-items:center;font-size:38px;font-weight:700}
            .result-icon.success{display:grid;background:#e7f8ef;color:#08783f}
            .result-icon.closed{display:grid;background:#f1f3f7;color:#667085}
            .details{margin-top:22px;padding-top:18px;border-top:1px solid #edf0f5;text-align:left}
            .row{display:flex;justify-content:space-between;gap:18px;padding:7px 0;font-size:13px}
            .row span:first-child{color:#8a95a8;flex:none}.row span:last-child{text-align:right;word-break:break-all}
            .footer{padding:15px 24px;background:#fafbfc;color:#8a95a8;text-align:center;font-size:12px}
            .error{margin:18px 0 0;color:#c0392b;font-size:13px}
            @media(max-width:480px){
              main{align-items:flex-start;padding:12px}.card{border-radius:20px}
              .header{padding:21px 20px 16px}.content{padding:20px}
              .qr-shell{width:min(270px,78vw);height:min(270px,78vw)}
            }
          </style>
        </head>
        <body>
        <main>
          <section class="card" aria-live="polite">
            <header class="header">
              <div class="brand">
                <div class="brand-icon">LL</div>
                <div><h1>LuLuPay 码支付</h1><p class="sub">请核对金额后扫码完成支付</p></div>
              </div>
            </header>
            <div class="content">
              <div id="platform" class="platform"><i class="platform-dot"></i><span>加载中</span></div>
              <div class="amount-label">应付金额</div>
              <div id="amount" class="amount">--</div>
              <div id="status" class="status pending">正在加载</div>
              <div id="result-icon" class="result-icon">✓</div>
              <div id="qr-shell" class="qr-shell" hidden>
                <img id="qr-image" alt="订单收款二维码">
              </div>
              <p id="hint" class="hint">正在获取收款二维码</p>
              <p id="hint-sub" class="hint-sub">请稍候</p>
              <p id="error" class="error" hidden></p>
              <div class="details">
                <div class="row"><span>订单号</span><span id="order-no">--</span></div>
                <div class="row"><span>剩余时间</span><span id="countdown">--</span></div>
              </div>
            </div>
            <footer class="footer">LuLuPay · 当前依据到账通知确认支付状态</footer>
          </section>
        </main>
        <script>
          const token="__TOKEN__";
          const api="/api/public/payment-orders/"+token;
          const byId=id=>document.getElementById(id);
          let latestOrder=null;
          let qrUrl=null;

          function money(minor,currency){
            const value=(Number(minor||0)/100).toFixed(2);
            return (currency==="CNY"?"¥ ":"")+value;
          }
          function platformName(platform){
            return platform==="WECHAT"?"微信支付":platform==="ALIPAY"?"支付宝":"扫码支付";
          }
          function statusView(status){
            if(status==="PAID")return["支付成功","success","支付已完成","可返回商户页面"];
            if(status==="EXPIRED")return["订单已过期","closed","订单已过期","请重新创建订单"];
            if(status==="CANCELLED")return["订单已取消","closed","订单已取消","请重新创建订单"];
            return["待支付","pending","请使用"+platformName(latestOrder.platform)+"扫码支付","支付后页面将自动更新"];
          }
          function render(order){
            latestOrder=order;
            byId("platform").querySelector("span").textContent=platformName(order.platform);
            byId("amount").textContent=money(order.payableAmountMinor,order.currency);
            byId("order-no").textContent=order.merchantOrderNo||"--";
            const view=statusView(order.status);
            byId("status").textContent=view[0];
            byId("status").className="status "+view[1];
            byId("hint").textContent=view[2];
            byId("hint-sub").textContent=view[3];
            const pending=order.status==="PENDING";
            byId("qr-shell").hidden=!pending;
            byId("result-icon").className=pending?"result-icon":"result-icon "+view[1];
            byId("result-icon").textContent=order.status==="PAID"?"✓":"×";
            if(pending&&order.qrImageUrl&&qrUrl!==order.qrImageUrl){
              qrUrl=order.qrImageUrl;
              byId("qr-image").src=order.qrImageUrl;
            }
            updateCountdown();
          }
          function updateCountdown(){
            if(!latestOrder||latestOrder.status!=="PENDING"){
              byId("countdown").textContent="--";
              return;
            }
            const seconds=Math.max(0,Math.floor((new Date(latestOrder.expiresAt).getTime()-Date.now())/1000));
            const minutes=Math.floor(seconds/60);
            byId("countdown").textContent=minutes+":"+String(seconds%60).padStart(2,"0");
            if(seconds===0)load();
          }
          async function load(){
            try{
              const response=await fetch(api,{cache:"no-store",headers:{Accept:"application/json"}});
              if(!response.ok)throw new Error("HTTP "+response.status);
              render(await response.json());
              byId("error").hidden=true;
            }catch(error){
              byId("error").textContent="订单信息加载失败，请刷新页面重试";
              byId("error").hidden=false;
            }
          }
          byId("qr-image").addEventListener("error",()=>{
            byId("error").textContent="二维码加载失败，请刷新页面重试";
            byId("error").hidden=false;
          });
          load();
          setInterval(updateCountdown,1000);
          setInterval(load,3000);
        </script>
        </body>
        </html>
        """;

    private final PaymentOrderService service;

    @GetMapping(value = "/api/public/payment-orders/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicPaymentOrderVo order(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        return service.queryPublic(token);
    }

    @GetMapping(value = "/api/public/payment-orders/{token}/qr.svg", produces = "image/svg+xml")
    public ResponseEntity<String> qr(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePrivate())
            .contentType(MediaType.parseMediaType("image/svg+xml"))
            .body(QrCodeSvgRenderer.render(service.qrContent(token)));
    }

    @GetMapping(value = "/pay/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> page(
        @Pattern(regexp = "[A-Za-z0-9_-]{32,64}") @PathVariable String token
    ) {
        String html = PAYMENT_PAGE_TEMPLATE.replace("__TOKEN__", token);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy",
                "default-src 'none'; img-src 'self'; connect-src 'self'; "
                    + "style-src 'unsafe-inline'; script-src 'unsafe-inline'")
            .header("Referrer-Policy", "no-referrer")
            .header("X-Frame-Options", "DENY")
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(html.getBytes(StandardCharsets.UTF_8));
    }
}
