package com.aiprovider.controller;

import com.aiprovider.model.dto.FoundryCallDTO;
import com.aiprovider.model.vo.FoundryQueryVO;
import com.aiprovider.model.vo.FoundryStatusVO;
import com.aiprovider.service.FoundryService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoundryControllerTest {
    @Test
    void endpointsDelegateToServiceAndUseUnifiedEnvelope() {
        FoundryService service = mock(FoundryService.class);
        FoundryStatusVO status = new FoundryStatusVO(true, "rpc.example", true, Collections.emptyList(), OffsetDateTime.now());
        FoundryQueryVO query = new FoundryQueryVO("block-number", "123", OffsetDateTime.now());
        FoundryCallDTO dto = new FoundryCallDTO();
        when(service.status()).thenReturn(status);
        when(service.blockNumber()).thenReturn(query);
        when(service.call(dto)).thenReturn(query);
        FoundryController controller = new FoundryController(service);

        assertThat(controller.status().getData()).isSameAs(status);
        assertThat(controller.blockNumber().getData().getResult()).isEqualTo("123");
        assertThat(controller.call(dto).getData()).isSameAs(query);
        verify(service).status();
        verify(service).blockNumber();
        verify(service).call(dto);
    }
}
