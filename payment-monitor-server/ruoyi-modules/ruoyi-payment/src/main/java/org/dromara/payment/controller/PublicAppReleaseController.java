package org.dromara.payment.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.payment.domain.vo.AppReleaseVo;
import org.dromara.payment.service.AppReleaseService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/app-releases")
public class PublicAppReleaseController {
    private final AppReleaseService service;

    @GetMapping("/latest")
    public R<AppReleaseVo> latest(@RequestParam(defaultValue = "ANDROID") String platform) {
        if (!"ANDROID".equalsIgnoreCase(platform)) {
            return R.fail("不支持的平台");
        }
        return R.ok(service.latest());
    }

    @GetMapping(value = "/{id}/download", produces = "application/vnd.android.package-archive")
    public ResponseEntity<InputStreamResource> download(
        @PathVariable Long id,
        @RequestParam long expires,
        @RequestParam String token
    ) {
        AppReleaseService.AppReleaseDownload download = service.openDownload(id, expires, token);
        String disposition = "attachment; filename*=UTF-8''"
            + java.net.URLEncoder.encode(download.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
            .contentLength(download.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header("X-APK-SHA256", download.sha256())
            .body(new InputStreamResource(download.inputStream()));
    }
}
