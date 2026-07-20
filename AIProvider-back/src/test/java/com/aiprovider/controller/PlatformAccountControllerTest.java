package com.aiprovider.controller;

import com.aiprovider.model.dto.PlatformAccountUpdateDTO;
import com.aiprovider.model.vo.PlatformAccountVO;
import com.aiprovider.service.PlatformAccountService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class PlatformAccountControllerTest {
    @Test
    void exposesAccountUpdateWithoutReturningCredentialMaterial() {
        PlatformAccountService service=mock(PlatformAccountService.class);
        PlatformAccountVO account=new PlatformAccountVO();account.setId(8L);account.setDisplayName("Gemini 主服务");
        PlatformAccountUpdateDTO dto=new PlatformAccountUpdateDTO();dto.setDisplayName("Gemini 主服务");
        when(service.update(8L,dto)).thenReturn(account);

        PlatformAccountController controller=new PlatformAccountController(service);

        assertSame(account,controller.update(8L,dto).getData());
        verify(service).update(8L,dto);
    }
}
