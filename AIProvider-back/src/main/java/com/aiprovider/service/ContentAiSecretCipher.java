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
public class ContentAiSecretCipher {
    private static final int IV_LENGTH=12;
    private final String configuredKey; private final SecureRandom random=new SecureRandom();
    public ContentAiSecretCipher(@Value("${content-ai.secret-encryption-key:}") String key){this.configuredKey=key==null?"":key.trim();}
    public String encrypt(String plaintext){if(plaintext==null||plaintext.isEmpty())throw new IllegalArgumentException("Gemini API Key 不能为空");try{byte[] iv=new byte[IV_LENGTH];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));byte[] payload=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,payload,0,iv.length);System.arraycopy(encrypted,0,payload,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(payload);}catch(ContentAiException e){throw e;}catch(Exception e){throw new ContentAiException("SECRET_ENCRYPT_FAILED","Gemini API Key 加密失败",e);}}
    public String decrypt(String encoded){try{byte[] payload=Base64.getDecoder().decode(encoded);if(payload.length<=IV_LENGTH)throw new IllegalArgumentException("invalid payload");byte[] iv=Arrays.copyOfRange(payload,0,IV_LENGTH);byte[] encrypted=Arrays.copyOfRange(payload,IV_LENGTH,payload.length);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(ContentAiException e){throw e;}catch(Exception e){throw new ContentAiException("SECRET_DECRYPT_FAILED","Gemini API Key 无法解密，请重新配置",e);}}
    private SecretKeySpec key(){if(configuredKey.isEmpty())throw new ContentAiException("SECRET_KEY_MISSING","未配置 CONTENT_AI_SECRET_ENCRYPTION_KEY，禁止保存 Gemini API Key");try{byte[] bytes=Base64.getDecoder().decode(configuredKey);if(bytes.length!=32)throw new IllegalArgumentException("length");return new SecretKeySpec(bytes,"AES");}catch(IllegalArgumentException e){throw new ContentAiException("SECRET_KEY_INVALID","CONTENT_AI_SECRET_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥");}}
}
