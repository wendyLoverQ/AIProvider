package com.aiprovider.service;

import com.aiprovider.repository.RemoteCodexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteCodexServiceTest {
    @Test
    void createsAStoredConversationAndReturnsItsMessages() {
        RemoteCodexRepository repository = mock(RemoteCodexRepository.class);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("title", "新对话"); row.put("status", "READY");
        when(repository.get(anyString())).thenAnswer(invocation -> {
            Map<String, Object> value = new LinkedHashMap<String, Object>(row);
            value.put("id", invocation.getArgument(0)); return value;
        });
        when(repository.messages(anyString())).thenReturn(Collections.<Map<String, Object>>emptyList());
        RemoteCodexService service = service(repository);
        try {
            Map<String, Object> created = service.create();
            assertThat(created.get("title")).isEqualTo("新对话");
            assertThat(created.get("messages")).isEqualTo(Collections.emptyList());
            verify(repository).create(anyString(), anyString(), any());
        } finally { service.shutdown(); }
    }

    @Test
    void grantsFullServerAccessForNewAndResumedTurns() {
        RemoteCodexService service = service(mock(RemoteCodexRepository.class));
        try {
            assertThat(service.turnCommand(null)).contains("--dangerously-bypass-approvals-and-sandbox").doesNotContain("--sandbox", "workspace-write");
            assertThat(service.turnCommand("thread-id")).contains("resume", "--dangerously-bypass-approvals-and-sandbox", "thread-id");
        } finally { service.shutdown(); }
    }

    private static RemoteCodexService service(RemoteCodexRepository repository) {
        return new RemoteCodexService(repository, new ObjectMapper(), "missing-codex-command", ".");
    }
}
