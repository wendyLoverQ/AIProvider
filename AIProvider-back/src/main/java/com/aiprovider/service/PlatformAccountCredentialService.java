package com.aiprovider.service;

import com.aiprovider.repository.PlatformAccountRepository;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PlatformAccountCredentialService {
    private final PlatformAccountRepository repository;private final ContentPlatformSecretCipher cipher;
    public PlatformAccountCredentialService(PlatformAccountRepository repository,ContentPlatformSecretCipher cipher){this.repository=repository;this.cipher=cipher;}
    public String requireSecret(long accountId,String platform,String secretType){Map<String,Object> account=repository.findAccount(accountId);if(account==null)throw new IllegalStateException("ACCOUNT_NOT_FOUND");if(!platform.equals(text(account.get("platform"))))throw new IllegalStateException("PLATFORM_MISMATCH");if(!truth(account.get("enabled")))throw new IllegalStateException("ACCOUNT_DISABLED");Map<String,Object> secret=repository.findSecret(accountId,secretType);if(secret==null)throw new IllegalStateException("CREDENTIAL_MISSING");String encrypted=text(secret.get("encryptedValue"));if(encrypted==null)throw new IllegalStateException("CREDENTIAL_MISSING");return cipher.decrypt(encrypted);}
    private String text(Object v){return v==null?null:String.valueOf(v);}private boolean truth(Object v){return v instanceof Boolean?(Boolean)v:Boolean.parseBoolean(String.valueOf(v));}
}
