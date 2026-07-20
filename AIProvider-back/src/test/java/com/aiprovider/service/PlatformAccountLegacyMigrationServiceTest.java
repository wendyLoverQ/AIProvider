package com.aiprovider.service;

import com.aiprovider.repository.PlatformAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAccountLegacyMigrationServiceTest {
    @Test
    void migratesLegacyTwitterSessionOnceAndLinksConsumer() throws Exception {
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);
        TwitterSessionCipher twitterCipher=mock(TwitterSessionCipher.class);
        ContentAiSecretCipher aiCipher=mock(ContentAiSecretCipher.class);
        ContentPlatformSecretCipher platformCipher=mock(ContentPlatformSecretCipher.class);
        when(repository.findLegacyTwitterAccounts()).thenReturn(Collections.singletonList(Map.of("id",3L,"username","alice","encryptedStorageState","old-cipher","sessionStatus","CONNECTED")));
        when(repository.findLegacyCollectionAccounts()).thenReturn(Collections.emptyList());
        when(repository.findLegacyContentAccounts()).thenReturn(Collections.emptyList());
        when(repository.findLegacyGeminiConfigs()).thenReturn(Collections.emptyList());
        when(repository.findByLegacy("TWITTER_ACCOUNT",3L)).thenReturn(null,Map.of("id",31L));
        when(repository.insertAccount(any())).thenReturn(31L);
        when(twitterCipher.decrypt("old-cipher")).thenReturn("storage-json");
        when(platformCipher.encrypt("storage-json")).thenReturn("new-cipher");
        PlatformAccountLegacyMigrationService service=new PlatformAccountLegacyMigrationService(repository,platformCipher,twitterCipher,aiCipher);

        service.run(null);service.run(null);

        verify(repository,times(1)).upsertSecret(31L,"STORAGE_STATE","new-cipher","浏览器会话");
        verify(repository,times(2)).linkLegacyConsumer("TWITTER_ACCOUNT",3L,31L);
    }
}
