package com.aiprovider.service;

import com.aiprovider.mapper.PlatformAccountMapper;
import com.aiprovider.model.dto.PlatformAccountCreateDTO;
import com.aiprovider.model.dto.PlatformSecretUpdateDTO;
import com.aiprovider.model.dto.PlatformAccountUpdateDTO;
import com.aiprovider.model.vo.PlatformAccountVO;
import com.aiprovider.repository.PlatformAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAccountServiceTest {
    @Test
    void storesEncryptedSecretAndNeverReturnsItsValue() {
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);
        ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);
        when(repository.findAccount(7L)).thenReturn(account(7L,"X","SOCIAL"));
        when(repository.findSecretSummaries(7L)).thenReturn(Collections.singletonList(Map.of("secretType","COOKIE","secretHint","Cookie 会话","secretVersion",1)));
        when(cipher.encrypt("auth_token=secret; ct0=csrf")).thenReturn("encrypted-value");
        PlatformAccountService service=new PlatformAccountService(repository,cipher);

        PlatformSecretUpdateDTO dto=new PlatformSecretUpdateDTO();dto.setValue("auth_token=secret; ct0=csrf");dto.setHint("Cookie 会话");
        PlatformAccountVO result=service.updateSecret(7L,"COOKIE",dto);

        verify(repository).upsertSecret(7L,"COOKIE","encrypted-value","Cookie 会话");
        assertTrue(result.getCredentialTypes().contains("COOKIE"));
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains("encrypted-value"));
    }

    @Test
    void credentialServiceRejectsPlatformMismatchWithoutTryingAnotherSecret() {
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);
        ContentPlatformSecretCipher cipher=mock(ContentPlatformSecretCipher.class);
        when(repository.findAccount(7L)).thenReturn(account(7L,"X","SOCIAL"));
        PlatformAccountCredentialService service=new PlatformAccountCredentialService(repository,cipher);

        IllegalStateException error=assertThrows(IllegalStateException.class,()->service.requireSecret(7L,"XIAOHONGSHU","STORAGE_STATE"));

        assertEquals("PLATFORM_MISMATCH",error.getMessage());
        verify(repository,never()).findSecret(anyLong(),anyString());
        verify(cipher,never()).decrypt(anyString());
    }

    @Test
    void createsMultipleAccountsForTheSamePlatform() {
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);
        when(repository.insertAccount(any())).thenReturn(9L);
        when(repository.findAccount(9L)).thenReturn(account(9L,"XIAOHONGSHU","SOCIAL"));
        when(repository.findSecretSummaries(9L)).thenReturn(Collections.emptyList());
        PlatformAccountService service=new PlatformAccountService(repository,mock(ContentPlatformSecretCipher.class));
        PlatformAccountCreateDTO dto=new PlatformAccountCreateDTO();dto.setPlatform("XIAOHONGSHU");dto.setDisplayName("小红书备用账号");dto.setAdapterType("XIAOHONGSHU_WEB");

        assertEquals(9L,service.create(dto).getId());
        verify(repository).insertAccount(argThat(record->"XIAOHONGSHU".equals(record.getPlatform())&&"SOCIAL".equals(record.getAccountKind())));
    }

    @Test
    void updatesOnlyEditableAccountFieldsWithoutChangingPlatform() {
        PlatformAccountRepository repository=mock(PlatformAccountRepository.class);
        when(repository.findAccount(7L)).thenReturn(account(7L,"X","SOCIAL"));
        when(repository.findSecretSummaries(7L)).thenReturn(Collections.emptyList());
        PlatformAccountService service=new PlatformAccountService(repository,mock(ContentPlatformSecretCipher.class));
        PlatformAccountUpdateDTO dto=new PlatformAccountUpdateDTO();dto.setDisplayName("X 主账号");dto.setAccountHandle("@owner");dto.setAdapterType("X_WEB");dto.setEnabled(true);

        PlatformAccountVO result=service.update(7L,dto);

        verify(repository).updateAccount(argThat(record->record.getId()==7L&&"X 主账号".equals(record.getDisplayName())&&"X".equals(record.getPlatform())));
        assertEquals(7L,result.getId());
    }

    private static Map<String,Object> account(long id,String platform,String kind){return Map.of("id",id,"platform",platform,"accountKind",kind,"displayName","测试账号","adapterType",platform+"_WEB","enabled",true,"connectionStatus","NOT_CONFIGURED");}
}
