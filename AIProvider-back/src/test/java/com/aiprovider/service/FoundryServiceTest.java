package com.aiprovider.service;

import com.aiprovider.model.dto.FoundryCallDTO;
import com.aiprovider.model.vo.FoundryQueryVO;
import com.aiprovider.model.vo.FoundryStatusVO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoundryServiceTest {
    @Test
    void executesOnlyWhitelistedReadOnlyCastCommandsAgainstConfiguredRpc() throws Exception {
        FoundryCommandRunner runner = mock(FoundryCommandRunner.class);
        when(runner.run(anyList(), anyLong())).thenReturn(new FoundryCommandRunner.CommandResult(0, "123456"));
        FoundryService service = service(runner);

        FoundryQueryVO block = service.blockNumber();
        FoundryQueryVO balance = service.balance("0x0000000000000000000000000000000000000001");

        assertThat(block.getResult()).isEqualTo("123456");
        assertThat(balance.getOperation()).isEqualTo("balance");
        verify(runner).run(Arrays.asList("cast", "block-number", "--rpc-url", "https://rpc.example/eth"), 10000);
        verify(runner).run(Arrays.asList("cast", "balance", "0x0000000000000000000000000000000000000001", "--ether", "--rpc-url", "https://rpc.example/eth"), 10000);
    }

    @Test
    void passesContractArgumentsAsProcessArgumentsWithoutShell() throws Exception {
        FoundryCommandRunner runner = mock(FoundryCommandRunner.class);
        when(runner.run(anyList(), anyLong())).thenReturn(new FoundryCommandRunner.CommandResult(0, "42"));
        FoundryService service = service(runner);
        FoundryCallDTO dto = new FoundryCallDTO();
        dto.setAddress("0x0000000000000000000000000000000000000002");
        dto.setSignature("balanceOf(address)(uint256)");
        dto.setArguments(Collections.singletonList("0x0000000000000000000000000000000000000001"));

        assertThat(service.call(dto).getResult()).isEqualTo("42");
        verify(runner).run(Arrays.asList("cast", "call", "0x0000000000000000000000000000000000000002",
            "balanceOf(address)(uint256)", "0x0000000000000000000000000000000000000001",
            "--rpc-url", "https://rpc.example/eth"), 10000);
    }

    @Test
    void rejectsInvalidAddressesSignaturesAndFlagInjection() {
        FoundryCommandRunner runner = mock(FoundryCommandRunner.class);
        FoundryService service = service(runner);
        FoundryCallDTO dto = new FoundryCallDTO();
        dto.setAddress("0x0000000000000000000000000000000000000002");
        dto.setSignature("balanceOf(address)(uint256)");
        dto.setArguments(Collections.singletonList("--private-key"));

        assertThatThrownBy(() -> service.balance("vitalik.eth")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.call(dto)).isInstanceOf(IllegalArgumentException.class);
        dto.setArguments(Collections.singletonList("0x1"));
        dto.setSignature("bad signature");
        assertThatThrownBy(() -> service.call(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsToolAvailabilityWithoutFailingWholeStatus() throws Exception {
        FoundryCommandRunner runner = mock(FoundryCommandRunner.class);
        when(runner.run(Arrays.asList("forge", "--version"), 5000)).thenReturn(new FoundryCommandRunner.CommandResult(0, "forge 1.3.0\nbuild"));
        when(runner.run(Arrays.asList("cast", "--version"), 5000)).thenReturn(new FoundryCommandRunner.CommandResult(0, "cast 1.3.0"));
        when(runner.run(Arrays.asList("anvil", "--version"), 5000)).thenThrow(new IOException("missing"));
        when(runner.run(Arrays.asList("chisel", "--version"), 5000)).thenReturn(new FoundryCommandRunner.CommandResult(1, "failed"));

        FoundryStatusVO status = service(runner).status();
        assertThat(status.isRpcConfigured()).isTrue();
        assertThat(status.getRpcHost()).isEqualTo("rpc.example");
        assertThat(status.getTools()).extracting(FoundryStatusVO.Tool::isAvailable)
            .containsExactly(true, true, false, false);
        assertThat(status.getTools().get(0).getVersion()).isEqualTo("forge 1.3.0");
    }

    @Test
    void refusesMissingOrInsecureRpcConfiguration() {
        FoundryCommandRunner runner = mock(FoundryCommandRunner.class);
        FoundryService missing = new FoundryService(runner, "", 10000, "forge", "cast", "anvil", "chisel");
        assertThatThrownBy(missing::blockNumber).isInstanceOf(FoundryUnavailableException.class);
        assertThatThrownBy(() -> new FoundryService(runner, "http://rpc.example", 10000, "forge", "cast", "anvil", "chisel"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static FoundryService service(FoundryCommandRunner runner) {
        return new FoundryService(runner, "https://rpc.example/eth", 10000, "forge", "cast", "anvil", "chisel");
    }
}
