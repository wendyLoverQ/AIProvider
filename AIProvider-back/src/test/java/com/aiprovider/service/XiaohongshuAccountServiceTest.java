package com.aiprovider.service;

import com.aiprovider.repository.ContentOperationsRepository;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class XiaohongshuAccountServiceTest {
    @Test void storesSessionInAccountCenterAfterQrLogin(){ContentOperationsRepository repository=mock(ContentOperationsRepository.class);PlatformAccountService accounts=mock(PlatformAccountService.class);XiaohongshuWebAdapter adapter=mock(XiaohongshuWebAdapter.class);Map<String,Object> linked=new HashMap<>();linked.put("platformAccountId",14L);when(repository.findAccount(4)).thenReturn(linked);when(adapter.poll(4,"session")).thenReturn(new XiaohongshuWebAdapter.LoginSnapshot("session","CONNECTED",null,"成功","storage-json"));assertEquals("CONNECTED",new XiaohongshuAccountService(repository,accounts,adapter).poll(4,"session").getStatus());verify(accounts).storeConnectedSecret(14L,"XIAOHONGSHU","STORAGE_STATE","storage-json","扫码会话");verify(repository,never()).updateAccountSession(anyLong(),anyString(),anyString());}
}
