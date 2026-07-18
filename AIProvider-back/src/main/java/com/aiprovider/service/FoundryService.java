package com.aiprovider.service;

import com.aiprovider.model.dto.FoundryCallDTO;
import com.aiprovider.model.vo.FoundryQueryVO;
import com.aiprovider.model.vo.FoundryStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
public class FoundryService {
    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final Pattern SIGNATURE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\([A-Za-z0-9_\\[\\],()]*\\)(?:\\([A-Za-z0-9_\\[\\],()]*\\))?$");
    private static final int MAX_ARGUMENTS = 32;
    private static final int MAX_ARGUMENT_LENGTH = 512;

    private final FoundryCommandRunner runner;
    private final String rpcUrl;
    private final String rpcHost;
    private final long timeoutMs;
    private final String forgeCommand;
    private final String castCommand;
    private final String anvilCommand;
    private final String chiselCommand;

    @Autowired
    public FoundryService(FoundryCommandRunner runner,
                          @Value("${foundry.rpc-url:}") String rpcUrl,
                          @Value("${foundry.command-timeout-ms:10000}") long timeoutMs,
                          @Value("${foundry.forge-command:forge}") String forgeCommand,
                          @Value("${foundry.cast-command:cast}") String castCommand,
                          @Value("${foundry.anvil-command:anvil}") String anvilCommand,
                          @Value("${foundry.chisel-command:chisel}") String chiselCommand) {
        this.runner = runner;
        this.rpcUrl = normalizeRpcUrl(rpcUrl);
        this.rpcHost = this.rpcUrl.isEmpty() ? "" : URI.create(this.rpcUrl).getHost();
        if (timeoutMs < 1000 || timeoutMs > 30000) throw new IllegalArgumentException("Foundry 超时必须在 1000 到 30000 毫秒之间");
        this.timeoutMs = timeoutMs;
        this.forgeCommand = requireCommand(forgeCommand, "forge");
        this.castCommand = requireCommand(castCommand, "cast");
        this.anvilCommand = requireCommand(anvilCommand, "anvil");
        this.chiselCommand = requireCommand(chiselCommand, "chisel");
    }

    public FoundryStatusVO status() {
        List<FoundryStatusVO.Tool> tools = Arrays.asList(
            inspect("Forge", forgeCommand), inspect("Cast", castCommand),
            inspect("Anvil", anvilCommand), inspect("Chisel", chiselCommand)
        );
        return new FoundryStatusVO(!rpcUrl.isEmpty(), rpcHost, true, tools, OffsetDateTime.now());
    }

    public FoundryQueryVO blockNumber() {
        return query("block-number", command("block-number"));
    }

    public FoundryQueryVO balance(String address) {
        return query("balance", command("balance", requireAddress(address), "--ether"));
    }

    public FoundryQueryVO code(String address) {
        return query("code", command("code", requireAddress(address)));
    }

    public FoundryQueryVO call(FoundryCallDTO dto) {
        if (dto == null) throw new IllegalArgumentException("调用参数不能为空");
        String signature = dto.getSignature() == null ? "" : dto.getSignature().trim();
        if (!SIGNATURE.matcher(signature).matches()) throw new IllegalArgumentException("Solidity 函数签名格式不正确");
        List<String> arguments = dto.getArguments() == null ? Collections.emptyList() : dto.getArguments();
        if (arguments.size() > MAX_ARGUMENTS) throw new IllegalArgumentException("调用参数最多 32 个");
        List<String> command = new ArrayList<>();
        command.add("call");
        command.add(requireAddress(dto.getAddress()));
        command.add(signature);
        for (String value : arguments) command.add(requireArgument(value));
        return query("call", command(command.toArray(new String[0])));
    }

    private FoundryStatusVO.Tool inspect(String name, String executable) {
        try {
            FoundryCommandRunner.CommandResult result = runner.run(Arrays.asList(executable, "--version"), Math.min(timeoutMs, 5000));
            if (result.getExitCode() != 0) return new FoundryStatusVO.Tool(name, false, "");
            String output = result.getOutput();
            int newline = output.indexOf('\n');
            return new FoundryStatusVO.Tool(name, true, newline < 0 ? output : output.substring(0, newline));
        } catch (IOException | TimeoutException exception) {
            return new FoundryStatusVO.Tool(name, false, "");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new FoundryStatusVO.Tool(name, false, "");
        }
    }

    private FoundryQueryVO query(String operation, List<String> command) {
        if (rpcUrl.isEmpty()) throw new FoundryUnavailableException("服务器未配置 FOUNDRY_RPC_URL");
        List<String> fullCommand = new ArrayList<>(command);
        fullCommand.add("--rpc-url");
        fullCommand.add(rpcUrl);
        try {
            FoundryCommandRunner.CommandResult result = runner.run(fullCommand, timeoutMs);
            if (result.getExitCode() != 0) {
                throw new FoundryUnavailableException("Foundry 查询失败：" + safeOutput(result.getOutput()).replace(rpcUrl, "[RPC]"));
            }
            if (result.getOutput().isEmpty()) throw new FoundryUnavailableException("Foundry 查询没有返回结果");
            return new FoundryQueryVO(operation, result.getOutput(), OffsetDateTime.now());
        } catch (TimeoutException exception) {
            throw new FoundryUnavailableException("Foundry 查询超时", exception);
        } catch (IOException exception) {
            throw new FoundryUnavailableException("服务器无法启动 Foundry Cast", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FoundryUnavailableException("Foundry 查询被中断", exception);
        }
    }

    private List<String> command(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(castCommand);
        command.addAll(Arrays.asList(arguments));
        return command;
    }

    private static String requireAddress(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!ADDRESS.matcher(normalized).matches()) throw new IllegalArgumentException("EVM 地址必须是 0x 开头的 40 位十六进制地址");
        return normalized;
    }

    private static String requireArgument(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_ARGUMENT_LENGTH || normalized.startsWith("-")
            || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0 || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("合约调用参数为空、过长或包含非法控制字符");
        }
        return normalized;
    }

    private static String normalizeRpcUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "";
        URI uri;
        try { uri = URI.create(normalized); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("FOUNDRY_RPC_URL 格式不正确"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("FOUNDRY_RPC_URL 必须是无用户信息和片段的 HTTPS 地址");
        }
        return normalized;
    }

    private static String requireCommand(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " 命令配置不正确");
        return normalized;
    }

    private static String safeOutput(String output) {
        String normalized = output == null ? "未知错误" : output.replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.isEmpty()) return "未知错误";
        return normalized.length() > 400 ? normalized.substring(0, 400) + "…" : normalized;
    }
}
