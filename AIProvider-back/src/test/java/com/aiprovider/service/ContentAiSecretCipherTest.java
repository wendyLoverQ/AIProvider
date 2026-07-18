package com.aiprovider.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ContentAiSecretCipherTest {
    @Test void encryptsWithRandomIvAndDecrypts(){
        String key=Base64.getEncoder().encodeToString(new byte[32]);ContentAiSecretCipher cipher=new ContentAiSecretCipher(key);
        String first=cipher.encrypt("gemini-secret-value-123456");String second=cipher.encrypt("gemini-secret-value-123456");
        assertNotEquals(first,second);assertEquals("gemini-secret-value-123456",cipher.decrypt(first));
    }
    @Test void refusesPlaintextStorageWithoutServerKey(){
        ContentAiException error=assertThrows(ContentAiException.class,()->new ContentAiSecretCipher("").encrypt("gemini-secret-value-123456"));
        assertEquals("SECRET_KEY_MISSING",error.getCode());
    }
}
