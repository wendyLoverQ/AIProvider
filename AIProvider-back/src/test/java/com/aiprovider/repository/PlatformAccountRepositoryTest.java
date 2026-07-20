package com.aiprovider.repository;

import com.aiprovider.mapper.PlatformAccountMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAccountRepositoryTest {
    @Test
    void createsAccountAndReturnsDatabaseNumericId() {
        PlatformAccountMapper mapper = mock(PlatformAccountMapper.class);
        doAnswer(invocation -> {
            PlatformAccountMapper.AccountRecord record = invocation.getArgument(0);
            record.setId(41L);
            return 1;
        }).when(mapper).insertAccount(any());

        PlatformAccountRepository repository = new PlatformAccountRepository(mapper);
        PlatformAccountMapper.AccountRecord record = new PlatformAccountMapper.AccountRecord();
        record.setPlatform("X");
        record.setAccountKind("SOCIAL");
        record.setDisplayName("主 X 账号");

        assertEquals(41L, repository.insertAccount(record));
    }

    @Test
    void refusesSecretUpdateWhenAffectedRowsMismatch() {
        PlatformAccountMapper mapper = mock(PlatformAccountMapper.class);
        when(mapper.findSecret(7L, "COOKIE")).thenReturn(Map.of("id", 9L, "secretVersion", 2));
        when(mapper.updateSecret(eq(9L), eq("ciphertext"), eq("Cookie 会话"), eq(3))).thenReturn(0);
        PlatformAccountRepository repository = new PlatformAccountRepository(mapper);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.upsertSecret(7L, "COOKIE", "ciphertext", "Cookie 会话"));

        assertTrue(error.getMessage().contains("影响行数"));
        verify(mapper, never()).insertSecret(any());
    }

    @Test
    void refusesToArchiveAnAccountStillUsedByBusinessModules() {
        PlatformAccountMapper mapper = mock(PlatformAccountMapper.class);
        when(mapper.findUsages(5L)).thenReturn(Collections.singletonList(Map.of(
                "consumerType", "CONTENT_ACCOUNT", "consumerId", 17L, "consumerName", "小红书主账号")));
        PlatformAccountRepository repository = new PlatformAccountRepository(mapper);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> repository.archiveAccount(5L));

        assertEquals("ACCOUNT_IN_USE", error.getMessage());
        verify(mapper, never()).archiveAccount(anyLong());
    }
}
