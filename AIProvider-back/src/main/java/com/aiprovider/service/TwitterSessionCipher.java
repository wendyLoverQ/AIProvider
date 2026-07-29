package com.aiprovider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class TwitterSessionCipher {
    private static final int IV_LENGTH = 12;
    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();

    public TwitterSessionCipher(@Value("${twitter.session-encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    public void ensureConfigured() {
        com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterSessionCipher.ensureConfigured", new String[] {}, new Object[] {});
        key(); com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterSessionCipher.ensureConfigured", null);
}

    public String encrypt(String plaintext) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterSessionCipher.encrypt", new String[] { "plaintext" }, new Object[] { plaintext });
    try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterSessionCipher.encrypt", Base64.getEncoder().encodeToString(payload));
        } catch (TwitterAutomationException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterAutomationException("Twitter 会话加密失败", e);
        }
    }

    public String decrypt(String encoded) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TwitterSessionCipher.decrypt", new String[] { "encoded" }, new Object[] { encoded });
    try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("invalid encrypted payload");
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TwitterSessionCipher.decrypt", new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        } catch (TwitterAutomationException e) {
            throw e;
        } catch (Exception e) {
            throw new TwitterAutomationException("Twitter 登录会话无法解密，请重新连接账号", true);
        }
    }

    private SecretKeySpec key() {
        if (configuredKey.isEmpty()) {
            throw new TwitterAutomationException("未配置 TWITTER_SESSION_ENCRYPTION_KEY，无法安全保存登录会话");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length != 32) throw new IllegalArgumentException("key must contain 32 bytes");
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException e) {
            throw new TwitterAutomationException("TWITTER_SESSION_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥");
        }
    }
}
