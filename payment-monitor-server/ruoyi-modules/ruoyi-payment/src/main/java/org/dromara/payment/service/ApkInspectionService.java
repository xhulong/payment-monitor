package org.dromara.payment.service;

import com.android.apksig.ApkVerifier;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.security.PaymentCrypto;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;

@Service
public class ApkInspectionService {

    public ApkInspection inspect(File file) {
        try {
            ApkVerifier.Result verification = new ApkVerifier.Builder(file).build().verify();
            if (!verification.isVerified()) {
                throw new ServiceException("APK 签名校验失败");
            }
            List<X509Certificate> certificates = verification.getSignerCertificates();
            if (certificates.size() != 1) {
                throw new ServiceException("APK 必须且只能包含一个当前签名证书");
            }
            ApkMeta meta;
            try (ApkFile apkFile = new ApkFile(file)) {
                meta = apkFile.getApkMeta();
            }
            if (meta.getPackageName() == null || meta.getPackageName().isBlank()) {
                throw new ServiceException("APK 包名无法读取");
            }
            long versionCode = meta.getVersionCode();
            if (versionCode <= 0 || versionCode > Integer.MAX_VALUE) {
                throw new ServiceException("APK versionCode 无效");
            }
            String versionName = meta.getVersionName();
            if (versionName == null || versionName.isBlank()) {
                throw new ServiceException("APK versionName 无效");
            }
            return new ApkInspection(
                meta.getPackageName(),
                Math.toIntExact(versionCode),
                versionName,
                PaymentCrypto.sha256Hex(certificates.getFirst().getEncoded()),
                sha256(file)
            );
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("APK 解析失败：" + exception.getMessage());
        }
    }

    public record ApkInspection(
        String packageName,
        Integer versionCode,
        String versionName,
        String signingCertificateSha256,
        String sha256
    ) {
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
