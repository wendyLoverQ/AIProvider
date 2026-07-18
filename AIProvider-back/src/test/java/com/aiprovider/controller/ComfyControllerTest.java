package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.ComfyPresetDTO;
import com.aiprovider.model.vo.ComfyPresetVO;
import com.aiprovider.model.vo.ComfyWorkflowVO;
import com.aiprovider.model.vo.PromptCatalogVO;
import com.aiprovider.service.ComfyPresetService;
import com.aiprovider.service.ComfyWorkflowService;
import com.aiprovider.service.PromptCatalogService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComfyControllerTest {
    @Test void presetEndpointsDelegateToService() {
        ComfyPresetService service = mock(ComfyPresetService.class);
        ComfyPresetController controller = new ComfyPresetController(service);
        ComfyPresetVO preset = new ComfyPresetVO(1L, "A", Collections.emptyMap(), "", "", "p", "n", null, false);
        when(service.list()).thenReturn(Collections.singletonList(preset)); when(service.create(any())).thenReturn(5L);
        assertThat(controller.list().getData()).containsExactly(preset);
        ComfyPresetDTO dto = new ComfyPresetDTO();
        assertThat(controller.create(dto).getData()).containsEntry("id", 5L);
        assertThat(controller.delete(7).getCode()).isEqualTo(200);
        assertThat(controller.update(5, dto).getCode()).isEqualTo(200); assertThat(controller.setDefault(5).getCode()).isEqualTo(200);
        verify(service).create(dto); verify(service).delete(7); verify(service).update(5, dto); verify(service).setDefault(5);
    }

    @Test void promptCatalogEndpointDelegatesToService() {
        PromptCatalogService service = mock(PromptCatalogService.class); PromptCatalogController controller = new PromptCatalogController(service);
        PromptCatalogVO catalog = new PromptCatalogVO(Collections.emptyList(), "negative"); when(service.get()).thenReturn(catalog);
        assertThat(controller.get().getData()).isSameAs(catalog);
    }

    @Test void workflowEndpointDelegatesToService() {
        ComfyWorkflowService service = mock(ComfyWorkflowService.class);
        ComfyWorkflowController controller = new ComfyWorkflowController(service);
        ComfyWorkflowVO workflow = new ComfyWorkflowVO("futa01", "Futa", null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        when(service.list()).thenReturn(Collections.singletonList(workflow));
        assertThat(controller.list().getData()).containsExactly(workflow);
    }

    @Test void mapsValidationErrorsToTheUnifiedEnvelope() {
        Result<Void> result = new ApiExceptionHandler().badRequest(new IllegalArgumentException("bad input"));
        assertThat(result.getCode()).isEqualTo(400); assertThat(result.getMessage()).isEqualTo("bad input");
    }
}
