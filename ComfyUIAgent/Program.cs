using System.Diagnostics;
using System.Net;
using System.Security.Cryptography;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
using System.Text.Json;
using System.Buffers.Binary;

var builderArgs = args
    .Where(argument => !string.Equals(argument.TrimEnd('/'), "aiprovider-bridge://start", StringComparison.OrdinalIgnoreCase))
    .ToArray();
var builder = WebApplication.CreateBuilder(new WebApplicationOptions {
    Args = builderArgs,
    ContentRootPath = AppContext.BaseDirectory
});
builder.WebHost.UseUrls("http://127.0.0.1:32145");
var settings = builder.Configuration.Get<BridgeSettings>() ?? throw new InvalidOperationException("Missing bridge configuration");
settings.Validate();
builder.Services.AddSingleton(settings);
builder.Services.AddSingleton<TwitterLocalPublisher>();
builder.Services.AddSingleton<WallpaperService>();
builder.Services.AddHttpClient("comfy", client => {
    client.BaseAddress = new Uri(settings.ComfyUiBaseUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromMinutes(15);
});
var app = builder.Build();
Process? comfyProcess = null;
var bridgeInstanceId = Guid.NewGuid().ToString("N");
var bridgeExitMode = 0; // 0 = keep running, 1 = stop, 2 = restart
var agentLogLock = new SemaphoreSlim(1, 1);
var bridgeUserSettingsLock = new SemaphoreSlim(1, 1);
var generationQueueLock = new SemaphoreSlim(1, 1);
var generationQueueSignal = new SemaphoreSlim(0);
var generationQueue = ReadBridgeGenerationQueue();
const int ComfySubmissionWindow = 2;

app.UseExceptionHandler(errorApp => errorApp.Run(async context => {
    var error = context.Features.Get<Microsoft.AspNetCore.Diagnostics.IExceptionHandlerFeature>()?.Error;
    if (error != null) app.Logger.LogError(error, "Local ComfyUI bridge request failed: {Path}", context.Request.Path);
    await AppendAgentLog("error", "request.exception", error?.Message ?? "Unknown exception", new { path = context.Request.Path.Value, exception = error?.ToString() });
    var origin = context.Request.Headers.Origin.ToString();
    if (!string.IsNullOrEmpty(origin) && settings.AllowedOrigins.Contains(origin, StringComparer.OrdinalIgnoreCase)) {
        context.Response.Headers.AccessControlAllowOrigin = origin;
        context.Response.Headers["Access-Control-Allow-Private-Network"] = "true";
        context.Response.Headers.Vary = "Origin";
    }
    context.Response.StatusCode = StatusCodes.Status502BadGateway;
    context.Response.ContentType = "application/json";
    await context.Response.WriteAsJsonAsync(new { success = false, message = error?.Message ?? "Local ComfyUI bridge request failed" });
}));

app.Use(async (context, next) => {
    var origin = context.Request.Headers.Origin.ToString();
    if (!string.IsNullOrEmpty(origin) && !settings.AllowedOrigins.Contains(origin, StringComparer.OrdinalIgnoreCase)) {
        context.Response.StatusCode = StatusCodes.Status403Forbidden;
        await context.Response.WriteAsJsonAsync(new { success = false, message = "Origin is not allowed" });
        return;
    }
    if (!string.IsNullOrEmpty(origin)) {
        context.Response.Headers.AccessControlAllowOrigin = origin;
        context.Response.Headers.AccessControlAllowMethods = "GET, POST, DELETE, OPTIONS";
        context.Response.Headers.AccessControlAllowHeaders = "Content-Type, X-Local-Token, Access-Control-Request-Private-Network";
        context.Response.Headers["Access-Control-Allow-Private-Network"] = "true";
        context.Response.Headers.Vary = "Origin";
    }
    if (HttpMethods.IsOptions(context.Request.Method)) { context.Response.StatusCode = StatusCodes.Status204NoContent; return; }
    await next();
});

app.Use(async (context, next) => {
    var protectedPath = context.Request.Path.StartsWithSegments("/comfy") ||
                        context.Request.Path.StartsWithSegments("/api/comfy/start") ||
                        context.Request.Path.StartsWithSegments("/api/comfy/stop") ||
                        context.Request.Path.StartsWithSegments("/api/comfy/restart") ||
                        context.Request.Path.StartsWithSegments("/api/history") ||
                        context.Request.Path.StartsWithSegments("/api/tasks") ||
                        context.Request.Path.StartsWithSegments("/api/gallery") ||
                        context.Request.Path.StartsWithSegments("/api/assets") ||
                        context.Request.Path.StartsWithSegments("/api/maid-ai") ||
                        context.Request.Path.StartsWithSegments("/api/migration") ||
                        context.Request.Path.StartsWithSegments("/api/folders") ||
                        context.Request.Path.StartsWithSegments("/api/generate") ||
                        context.Request.Path.StartsWithSegments("/api/lora-models") ||
                        context.Request.Path.StartsWithSegments("/api/main-models") ||
                        context.Request.Path.StartsWithSegments("/api/local-workflows") ||
                        context.Request.Path.StartsWithSegments("/api/logs") ||
                        context.Request.Path.StartsWithSegments("/api/twitter") ||
                        context.Request.Path.StartsWithSegments("/api/wallpaper") ||
                        context.Request.Path.StartsWithSegments("/api/workflows");
    if (protectedPath && !CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.UTF8.GetBytes(settings.LocalToken),
            System.Text.Encoding.UTF8.GetBytes(context.Request.Headers["X-Local-Token"].ToString()))) {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await context.Response.WriteAsJsonAsync(new { success = false, message = "Invalid local token" }); return;
    }
    await next();
});

app.MapGet("/api/config", () => Results.Ok(new {
    success = true,
    token = settings.LocalToken,
    instanceId = bridgeInstanceId,
    platform = settings.PlatformName,
    configured = settings.IsPlatformConfigured,
    expectedComfyDirectory = settings.ExpectedComfyDirectory
}));
app.MapGet("/api/wallpaper/monitors", (WallpaperService wallpaper) =>
    Results.Ok(new { success = true, monitors = wallpaper.Monitors() }));
app.MapPost("/api/wallpaper/apply", async (HttpRequest request, WallpaperService wallpaper) => {
    if (!request.HasFormContentType) return Results.BadRequest(new { success = false, message = "壁纸请求必须使用表单上传" });
    var form = await request.ReadFormAsync();
    var file = form.Files.GetFile("file");
    var monitorId = form["monitorId"].ToString();
    if (file == null) return Results.BadRequest(new { success = false, message = "缺少壁纸文件" });
    var applied = await wallpaper.ApplyAsync(file, monitorId);
    return Results.Ok(new { success = true, monitorId, provider = applied.Provider, path = applied.Path });
}).DisableAntiforgery();
app.MapPost("/api/logs/client", async (ClientLogRequest request) => {
    await AppendAgentLog("error", $"frontend.{request.Scope}", request.Message, new { request.PromptId, request.Path, request.Details });
    return Results.Ok(new { success = true });
}).DisableAntiforgery();
app.MapGet("/api/logs/path", () => Results.Ok(new { success = true, directory = AgentLogDirectory(), current = AgentLogPath() }));
app.MapGet("/api/comfy/status", async (IHttpClientFactory factory) => {
    var running = await IsComfyRunning(factory);
    return Results.Ok(new { success = true, running, launcher = "ready", platform = settings.PlatformName,
        configured = settings.IsPlatformConfigured, expectedComfyDirectory = settings.ExpectedComfyDirectory,
        message = running ? "ComfyUI is running" : settings.IsPlatformConfigured ? "ComfyUI is stopped" : "ComfyUI paths are not configured" });
});
app.MapGet("/api/lora-models", async (IHttpClientFactory factory) => {
    using var response = await factory.CreateClient("comfy").GetAsync("object_info");
    var text = await response.Content.ReadAsStringAsync();
    if (!response.IsSuccessStatusCode) return Results.Problem($"读取 ComfyUI LoRA 模型失败（HTTP {(int)response.StatusCode}）", statusCode: 502);
    var root = JsonNode.Parse(text) as JsonObject ?? throw new InvalidOperationException("ComfyUI object_info 返回无效 JSON");
    var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    foreach (var node in root) {
        if (!node.Key.Contains("lora", StringComparison.OrdinalIgnoreCase) || node.Value?["input"] is not JsonObject input) continue;
        foreach (var groupName in new[] { "required", "optional" }) {
            if (input[groupName] is not JsonObject group) continue;
            foreach (var field in group) {
                if (!field.Key.Contains("lora", StringComparison.OrdinalIgnoreCase) && !field.Key.Contains("model_name", StringComparison.OrdinalIgnoreCase)) continue;
                if (field.Value is not JsonArray spec || spec.Count == 0 || spec[0] is not JsonArray choices) continue;
                foreach (var choice in choices.OfType<JsonValue>()) if (choice.TryGetValue<string>(out var name) &&
                    !string.IsNullOrWhiteSpace(name) && !IsLoraPlaceholder(name)) names.Add(name);
            }
        }
    }
    var models = names.OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
        .Select(name => new { name, displayName = CompactLoraDisplayName(name) });
    return Results.Ok(new { success = true, models });
});
app.MapGet("/api/main-models", async (string nodeType, string input, IHttpClientFactory factory) => {
    if (string.IsNullOrWhiteSpace(nodeType) || string.IsNullOrWhiteSpace(input))
        return Results.BadRequest(new { success = false, message = "缺少主模型加载节点类型或输入名称" });
    using var response = await factory.CreateClient("comfy").GetAsync("object_info");
    var text = await response.Content.ReadAsStringAsync();
    if (!response.IsSuccessStatusCode) return Results.Problem($"读取 ComfyUI 主模型失败（HTTP {(int)response.StatusCode}）", statusCode: 502);
    var root = JsonNode.Parse(text) as JsonObject ?? throw new InvalidOperationException("ComfyUI object_info 返回无效 JSON");
    if (root[nodeType]?["input"] is not JsonObject nodeInputs)
        return Results.Problem($"ComfyUI 未注册主模型加载节点 {nodeType}", statusCode: 422);
    JsonArray? choices = null;
    foreach (var groupName in new[] { "required", "optional" }) {
        if (nodeInputs[groupName]?[input] is JsonArray spec && spec.Count > 0 && spec[0] is JsonArray inputChoices) {
            choices = inputChoices;
            break;
        }
    }
    if (choices == null)
        return Results.Problem($"节点 {nodeType} 的 {input} 不是可选择的模型输入", statusCode: 422);
    var models = choices.OfType<JsonValue>()
        .Select(value => value.TryGetValue<string>(out var name) ? name : null)
        .Where(name => !string.IsNullOrWhiteSpace(name))
        .Select(name => name!)
        .Distinct(StringComparer.OrdinalIgnoreCase)
        .OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
        .Select(name => new { name, displayName = CompactModelDisplayName(name) });
    return Results.Ok(new {
        success = true,
        kind = input.Equals("ckpt_name", StringComparison.OrdinalIgnoreCase) ? "checkpoint" : "diffusion",
        models
    });
});
app.MapPost("/api/comfy/start", async (IHttpClientFactory factory) => {
    if (!settings.IsPlatformConfigured)
        return Results.BadRequest(new { success = false, running = false, message = $"请先把 ComfyUI 放到 {settings.ExpectedComfyDirectory} 并完成 {settings.PlatformName} 配置" });
    if (await IsComfyRunning(factory)) return Results.Ok(new { success = true, running = true, message = "ComfyUI is already running" });
    var startInfo = new ProcessStartInfo(settings.ActiveProfile.PythonPath) {
        WorkingDirectory = settings.ActiveProfile.WorkingDirectory, UseShellExecute = false, CreateNoWindow = true
    };
    startInfo.ArgumentList.Add("-s");
    startInfo.ArgumentList.Add(settings.ActiveProfile.MainPyPath);
    foreach (var argument in settings.ActiveProfile.StartArguments) startInfo.ArgumentList.Add(argument);
    comfyProcess = Process.Start(startInfo);
    for (var i = 0; i < settings.StartTimeoutSeconds; i += 2) {
        await Task.Delay(2000);
        if (await IsComfyRunning(factory)) return Results.Ok(new { success = true, running = true, message = "ComfyUI started" });
        if (comfyProcess?.HasExited == true) break;
    }
    return Results.Problem("ComfyUI failed to start before timeout", statusCode: 504);
});
app.MapPost("/api/comfy/stop", async (HttpContext context, IHttpClientFactory factory, IHostApplicationLifetime lifetime) => {
    if (!await IsComfyRunning(factory)) {
        ScheduleBridgeExit(context, lifetime, restart: false);
        return Results.Ok(new { success = true, running = false, bridgeStopping = true, message = "ComfyUI is already stopped; bridge is stopping" });
    }

    // The cmd process launched from a .bat file can exit while its Python child keeps
    // serving ComfyUI. The bridge may also be restarted while ComfyUI stays alive.
    // Resolve the process that currently owns the configured local listening port
    // instead of relying only on the in-memory Process instance.
    var processToStop = comfyProcess is { HasExited: false }
        ? comfyProcess
        : await FindComfyListenerProcess();
    if (processToStop == null)
        return Results.Problem("Could not find the local ComfyUI process", statusCode: 500);

    processToStop.Kill(true);
    processToStop.Dispose();
    comfyProcess = null;
    for (var i = 0; i < 15; i++) {
        if (!await IsComfyRunning(factory)) {
            ScheduleBridgeExit(context, lifetime, restart: false);
            return Results.Ok(new { success = true, running = false, bridgeStopping = true, message = "ComfyUI and bridge stopped" });
        }
        await Task.Delay(500);
    }
    return Results.Problem("ComfyUI process was stopped but the API is still reachable", statusCode: 500);
});

app.MapPost("/api/comfy/restart", async (HttpContext context, IHttpClientFactory factory, IHostApplicationLifetime lifetime) => {
    if (!settings.IsPlatformConfigured)
        return Results.BadRequest(new { success = false, running = false, message = $"请先把 ComfyUI 放到 {settings.ExpectedComfyDirectory} 并完成 {settings.PlatformName} 配置" });

    if (await IsComfyRunning(factory)) {
        var processToStop = comfyProcess is { HasExited: false }
            ? comfyProcess
            : await FindComfyListenerProcess();
        if (processToStop == null)
            return Results.Problem("Could not find the local ComfyUI process", statusCode: 500);
        processToStop.Kill(true);
        processToStop.Dispose();
        comfyProcess = null;
        for (var i = 0; i < 30 && await IsComfyRunning(factory); i++) await Task.Delay(500);
        if (await IsComfyRunning(factory))
            return Results.Problem("ComfyUI process was stopped but the API is still reachable", statusCode: 500);
    }

    var startInfo = new ProcessStartInfo(settings.ActiveProfile.PythonPath) {
        WorkingDirectory = settings.ActiveProfile.WorkingDirectory, UseShellExecute = false, CreateNoWindow = true
    };
    startInfo.ArgumentList.Add("-s");
    startInfo.ArgumentList.Add(settings.ActiveProfile.MainPyPath);
    foreach (var argument in settings.ActiveProfile.StartArguments) startInfo.ArgumentList.Add(argument);
    comfyProcess = Process.Start(startInfo);
    for (var i = 0; i < settings.StartTimeoutSeconds; i += 2) {
        await Task.Delay(2000);
        if (await IsComfyRunning(factory)) {
            ScheduleBridgeExit(context, lifetime, restart: true);
            return Results.Ok(new { success = true, running = true, bridgeRestarting = true, message = "ComfyUI and bridge restarted" });
        }
        if (comfyProcess?.HasExited == true) break;
    }
    return Results.Problem("ComfyUI failed to restart before timeout", statusCode: 504);
});

app.MapPost("/api/tasks/{promptId}/cancel", async (string promptId) => {
    promptId = (promptId ?? "").Trim();
    if (promptId.Length == 0 || promptId.Length > 200)
        return Results.BadRequest(new { success = false, message = "任务 ID 不合法" });
    if (await CancelPendingBridgeGeneration(promptId))
        return Results.Json(new { success = true, promptId, state = "CANCELLED", cancelled = true, cancellationRequested = false, queueOwner = "BRIDGE" }, statusCode: StatusCodes.Status202Accepted);
    var bridgeTask = await FindBridgeQueuedGeneration(promptId);
    if (bridgeTask?.State is "FAILED" or "SUCCEEDED" or "CANCELLED")
        return Results.Ok(new { success = true, promptId, cancelled = false, wasPending = false, wasRunning = false, queueOwner = "BRIDGE" });
    if (bridgeTask == null) {
        await EnqueueBridgeGeneration(new BridgeQueuedGeneration {
            PromptId = promptId,
            CreatedAt = DateTimeOffset.Now,
            State = "CANCEL_REQUESTED"
        });
    } else {
        await SetBridgeGenerationState(promptId, "CANCEL_REQUESTED");
        generationQueueSignal.Release();
    }
    return Results.Json(new { success = true, promptId, state = "CANCEL_REQUESTED", cancelled = false, cancellationRequested = true, queueOwner = "BRIDGE" }, statusCode: StatusCodes.Status202Accepted);
}).DisableAntiforgery();

app.MapPost("/api/tasks/cancel-all", async () => {
    var result = await CancelAllBridgeGenerations();
    return Results.Json(new {
        success = true,
        total = result.Total,
        cancelled = result.Cancelled,
        cancellationRequested = result.CancellationRequested,
        promptIds = result.PromptIds,
        queueOwner = "BRIDGE"
    }, statusCode: StatusCodes.Status202Accepted);
}).DisableAntiforgery();

app.MapGet("/api/tasks/{promptId}/state", async (string promptId, IHttpClientFactory factory) => {
    promptId = (promptId ?? "").Trim();
    if (promptId.Length == 0 || promptId.Length > 200)
        return Results.BadRequest(new { success = false, message = "生成任务 ID 无效" });
    var bridgeQueued = await FindBridgeQueuedGeneration(promptId);
    if (bridgeQueued != null) {
        return Results.Ok(new { success = true, promptId, state = bridgeQueued.State, tracked = true,
            createdAt = bridgeQueued.CreatedAt, filesConfirmed = false, queueOwner = "BRIDGE", message = bridgeQueued.Error });
    }
    var client = factory.CreateClient("comfy");
    using var historyResponse = await client.GetAsync($"history/{Uri.EscapeDataString(promptId)}");
    historyResponse.EnsureSuccessStatusCode();
    var history = JsonNode.Parse(await historyResponse.Content.ReadAsStringAsync()) as JsonObject
        ?? throw new InvalidOperationException("ComfyUI history 返回格式无效");
    using var queueResponse = await client.GetAsync("queue");
    queueResponse.EnsureSuccessStatusCode();
    var queue = JsonNode.Parse(await queueResponse.Content.ReadAsStringAsync()) as JsonObject
        ?? throw new InvalidOperationException("ComfyUI queue 返回格式无效");
    bool Contains(string key) => queue[key] is JsonArray rows && rows.Any(row =>
        row is JsonArray cells && cells.Count > 1 && string.Equals(cells[1]?.ToString(), promptId, StringComparison.Ordinal));
    var historyItem = history[promptId];
    var historyFailed = string.Equals(historyItem?["status"]?["status_str"]?.ToString(), "error", StringComparison.OrdinalIgnoreCase) ||
        historyItem?["status"]?["messages"] is JsonArray messages && messages.Any(message => message is JsonArray cells && cells[0]?.ToString() == "execution_error");
    var state = historyFailed ? "FAILED" : historyItem != null ? "COMPLETED" : Contains("queue_running") ? "RUNNING" : Contains("queue_pending") ? "QUEUED" : "UNKNOWN";
    return Results.Ok(new { success = true, promptId, state, tracked = false, filesConfirmed = false });
});

app.MapGet("/api/twitter/status", (TwitterLocalPublisher twitter) => Results.Ok(new { success = true, data = twitter.Status() }));
app.MapPost("/api/twitter/connect", async (TwitterLocalLoginRequest request, TwitterLocalPublisher twitter) => {
    try { return Results.Ok(new { success = true, data = await twitter.ConnectAsync(request) }); }
    catch (Exception exception) { return Results.Problem(exception.Message, statusCode: 502); }
}).DisableAntiforgery();
app.MapPost("/api/twitter/publish", async (HttpRequest request, TwitterLocalPublisher twitter) => {
    if (!request.HasFormContentType) return Results.BadRequest(new { success = false, message = "发布请求必须使用 multipart/form-data" });
    var form = await request.ReadFormAsync(request.HttpContext.RequestAborted);
    var tempDirectory = Path.Combine(Path.GetTempPath(), "aiprovider-twitter", Guid.NewGuid().ToString("N"));
    Directory.CreateDirectory(tempDirectory);
    var paths = new List<string>();
    try {
        if (form.Files.Count > 4) return Results.BadRequest(new { success = false, message = "一次最多发布 4 张图片" });
        foreach (var file in form.Files) {
            if (file.Length <= 0 || file.Length > 15L * 1024 * 1024)
                return Results.BadRequest(new { success = false, message = "单张图片大小必须在 15MB 以内" });
            var extension = Path.GetExtension(file.FileName).ToLowerInvariant();
            if (extension is not (".png" or ".jpg" or ".jpeg" or ".webp" or ".gif"))
                return Results.BadRequest(new { success = false, message = "图片格式不支持" });
            var path = Path.Combine(tempDirectory, Guid.NewGuid().ToString("N") + extension);
            await using var output = File.Create(path);
            await file.CopyToAsync(output, request.HttpContext.RequestAborted);
            paths.Add(path);
        }
        var result = await twitter.PublishAsync(form["content"].ToString(), paths);
        return Results.Ok(new { success = true, data = result });
    }
    catch (Exception exception) { return Results.Problem(exception.Message, statusCode: 502); }
    finally { try { Directory.Delete(tempDirectory, true); } catch { } }
}).DisableAntiforgery();
app.MapGet("/api/workflows/workflow_api.json", () => Results.File(settings.ResolveWorkflowPath(), "application/json"));
app.MapGet("/api/local-workflows/settings", () => Results.Ok(new { success = true, directory = GetLocalWorkflowDirectory(), exists = Directory.Exists(GetLocalWorkflowDirectory()) }));
app.MapPost("/api/local-workflows/settings", async (LocalWorkflowSettingsRequest request) => {
    var directory = (request.Directory ?? "").Trim();
    if (!Path.IsPathFullyQualified(directory)) return Results.BadRequest(new { success = false, message = "工作流目录必须是完整路径" });
    directory = Path.GetFullPath(directory);
    if (!Directory.Exists(directory)) return Results.BadRequest(new { success = false, message = "工作流目录不存在" });
    await File.WriteAllTextAsync(LocalWorkflowSettingsPath(), JsonSerializer.Serialize(new { directory }, GalleryJsonOptions(writeIndented: true)));
    return Results.Ok(new { success = true, directory });
}).DisableAntiforgery();
app.MapGet("/api/local-workflows", async (IHttpClientFactory factory) => {
    var directory = GetLocalWorkflowDirectory();
    if (!Directory.Exists(directory)) return Results.NotFound(new { success = false, message = "本机工作流目录不存在", directory });
    var result = await ScanLocalWorkflows(factory, directory);
    return Results.Ok(new { success = true, directory, workflows = result.Workflows, rejected = result.Rejected });
});
app.MapGet("/api/workflows", async () => {
    var directory = Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI");
    var profiles = JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(directory, "workflows.json")))?.AsArray()
        ?? throw new InvalidOperationException("workflows.json 无效");
    var result = new JsonArray();
    foreach (var item in profiles.OfType<JsonObject>()) {
        var binding = JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(directory, item["binding"]!.GetValue<string>())))?.AsObject();
        var workflow = JsonNode.Parse(await File.ReadAllTextAsync(Path.Combine(directory, item["workflow"]!.GetValue<string>())))?.AsObject();
        var defaults = new JsonObject();
        var exposed = new HashSet<string> { "positivePrompt", "negativePrompt", "loras", "width", "height", "batchSize", "seed", "steps", "cfg", "sampler", "scheduler", "denoise", "styleStrength", "openPoseStrength", "secondPassSteps", "secondPassDenoise", "faceDetailerSteps", "faceDetailerDenoise" };
        if (binding?["fields"] is JsonObject bindingFields && workflow != null)
            foreach (var field in bindingFields.Where(x => exposed.Contains(x.Key) && x.Value is JsonObject)) {
                var spec = field.Value!.AsObject();
                var title = spec["nodeTitle"]?.GetValue<string>();
                var input = spec["input"]?.GetValue<string>();
                var node = workflow.FirstOrDefault(x => x.Value?["_meta"]?["title"]?.GetValue<string>() == title).Value;
                if (node?["inputs"]?[input ?? ""] is JsonNode value) defaults[field.Key] = value.DeepClone();
            }
        defaults["randomSeed"] = false;
        result.Add(new JsonObject { ["id"] = item["id"]?.DeepClone(), ["name"] = item["name"]?.DeepClone(), ["capabilities"] = binding?["capabilities"]?.DeepClone(), ["defaults"] = defaults });
    }
    return Results.Ok(new { success = true, workflows = result });
});
app.MapPost("/api/generate", async (HttpRequest request, IHttpClientFactory factory) => {
    if (!request.HasFormContentType) return Results.BadRequest(new { success = false, message = "生成请求必须使用 multipart/form-data" });
    var form = await request.ReadFormAsync(request.HttpContext.RequestAborted);
    var prepared = await PrepareGeneration(form, factory);
    if (prepared.Error != null) return prepared.Error;
    await EnqueueBridgeGenerations(new[] { prepared.Value!.QueueItem });
    return GenerationAccepted(prepared.Value);
}).DisableAntiforgery();

app.MapPost("/api/generate/batch", async (HttpRequest request, IHttpClientFactory factory) => {
    if (!request.HasFormContentType) return Results.BadRequest(new { success = false, message = "批量生成请求必须使用 multipart/form-data" });
    var form = await request.ReadFormAsync(request.HttpContext.RequestAborted);
    var sourceFiles = form.Files.GetFiles("sourceImages");
    var hashes = JsonSerializer.Deserialize<string[]>(form["inputSha256List"].ToString(), GalleryJsonOptions()) ?? Array.Empty<string>();
    if (sourceFiles.Count == 0 || sourceFiles.Count > 1000 || hashes.Length != sourceFiles.Count)
        return Results.BadRequest(new { success = false, message = "批量图片和 SHA-256 列表数量不一致，或超出 1000 张限制" });
    if (hashes.Any(hash => hash == null || !System.Text.RegularExpressions.Regex.IsMatch(hash, "^[0-9a-fA-F]{64}$")))
        return Results.BadRequest(new { success = false, message = "批量图片 SHA-256 无效" });

    var commonFields = form.ToDictionary(entry => entry.Key, entry => entry.Value);
    var preparedItems = new PreparedGeneration?[sourceFiles.Count];
    var preparationErrors = new IResult?[sourceFiles.Count];
    await Parallel.ForEachAsync(Enumerable.Range(0, sourceFiles.Count), new ParallelOptions {
        MaxDegreeOfParallelism = Math.Min(4, sourceFiles.Count),
        CancellationToken = request.HttpContext.RequestAborted
    }, async (index, _) => {
        var source = sourceFiles[index];
        var hash = hashes[index].ToLowerInvariant();
        var extension = Path.GetExtension(source.FileName);
        var uniqueName = $"{Path.GetFileNameWithoutExtension(source.FileName)}_{hash[..12]}{extension}";
        await using var sourceStream = source.OpenReadStream();
        var childFile = new FormFile(sourceStream, 0, source.Length, "sourceImage", uniqueName) {
            Headers = source.Headers,
            ContentType = source.ContentType
        };
        var childFiles = new FormFileCollection { childFile };
        var childFields = new Dictionary<string, Microsoft.Extensions.Primitives.StringValues>(commonFields) {
            ["clientId"] = Guid.NewGuid().ToString("N")
        };
        var prepared = await PrepareGeneration(new FormCollection(childFields, childFiles), factory);
        preparationErrors[index] = prepared.Error;
        if (prepared.Value != null) preparedItems[index] = prepared.Value with { InputSha256 = hash, InputFileName = source.FileName };
    });
    var preparationError = preparationErrors.FirstOrDefault(error => error != null);
    if (preparationError != null) return preparationError;
    var completedItems = preparedItems.Select(item => item!).ToArray();
    await EnqueueBridgeGenerations(completedItems.Select(item => item.QueueItem));
    return Results.Json(new { success = true, tasks = completedItems.Select(item => new {
        item.PromptId, state = "PENDING", item.FinalOutputNodeId, queueOwner = "BRIDGE",
        item.ActualSeed, item.Width, item.Height, item.InputSha256, item.InputFileName
    }) }, statusCode: StatusCodes.Status202Accepted);
}).DisableAntiforgery();

app.MapPost("/api/generate/batch-configs", async (HttpRequest request, IHttpClientFactory factory) => {
    if (!request.HasFormContentType) return Results.BadRequest(new { success = false, message = "批量生成请求必须使用 multipart/form-data" });
    var form = await request.ReadFormAsync(request.HttpContext.RequestAborted);
    JsonArray items;
    try {
        items = JsonNode.Parse(form["itemsJson"].ToString())?.AsArray()
            ?? throw new InvalidDataException("itemsJson 不是 JSON 数组");
    } catch (Exception exception) {
        return Results.BadRequest(new { success = false, message = $"批量生成参数无效：{exception.Message}" });
    }
    if (items.Count == 0 || items.Count > 1000 || items.Any(item => item is not JsonObject))
        return Results.BadRequest(new { success = false, message = "批量生成数量必须在 1 到 1000 之间，且每项必须是参数对象" });

    var commonFields = form.ToDictionary(entry => entry.Key, entry => entry.Value);
    commonFields.Remove("itemsJson");
    var preparedItems = new PreparedGeneration?[items.Count];
    var preparationErrors = new IResult?[items.Count];
    await Parallel.ForEachAsync(Enumerable.Range(0, items.Count), new ParallelOptions {
        MaxDegreeOfParallelism = Math.Min(4, items.Count),
        CancellationToken = request.HttpContext.RequestAborted
    }, async (index, _) => {
        var childFields = new Dictionary<string, Microsoft.Extensions.Primitives.StringValues>(commonFields) {
            ["clientId"] = Guid.NewGuid().ToString("N")
        };
        foreach (var entry in items[index]!.AsObject()) {
            childFields[entry.Key] = entry.Value is JsonValue scalar && scalar.TryGetValue<string>(out var text)
                ? text
                : entry.Value?.ToJsonString() ?? "";
        }
        var openedStreams = new List<Stream>();
        try {
            var childFiles = new FormFileCollection();
            foreach (var file in form.Files) {
                var stream = file.OpenReadStream();
                openedStreams.Add(stream);
                var extension = Path.GetExtension(file.FileName);
                var uniqueName = $"{Path.GetFileNameWithoutExtension(file.FileName)}_batch{index + 1:D4}_{Guid.NewGuid():N}{extension}";
                childFiles.Add(new FormFile(stream, 0, file.Length, file.Name, uniqueName) {
                    Headers = file.Headers,
                    ContentType = file.ContentType
                });
            }
            var prepared = await PrepareGeneration(new FormCollection(childFields, childFiles), factory);
            preparationErrors[index] = prepared.Error;
            preparedItems[index] = prepared.Value;
        } finally {
            foreach (var stream in openedStreams) await stream.DisposeAsync();
        }
    });
    var preparationError = preparationErrors.FirstOrDefault(error => error != null);
    if (preparationError != null) return preparationError;
    var completedItems = preparedItems.Select(item => item!).ToArray();
    await EnqueueBridgeGenerations(completedItems.Select(item => item.QueueItem));
    return Results.Json(new { success = true, tasks = completedItems.Select(item => new {
        item.PromptId, state = "PENDING", item.FinalOutputNodeId, queueOwner = "BRIDGE",
        item.ActualSeed, item.Width, item.Height, item.InputSha256, item.InputFileName
    }) }, statusCode: StatusCodes.Status202Accepted);
}).DisableAntiforgery();

async Task<GenerationPreparation> PrepareGeneration(IFormCollection form, IHttpClientFactory factory) {
    var directory = Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI");
    var postedWorkflow = form["workflowDefinition"].ToString();
    var postedBinding = form["workflowBinding"].ToString();
    JsonObject prompt;
    JsonObject bindings;
    if (!string.IsNullOrWhiteSpace(postedWorkflow) && !string.IsNullOrWhiteSpace(postedBinding)) {
        try {
            prompt = JsonNode.Parse(postedWorkflow)?.AsObject()
                ?? throw new InvalidOperationException("不是 JSON 对象");
        } catch (Exception ex) {
            throw new InvalidOperationException($"workflowDefinition 无效：{ex.Message}", ex);
        }
        try {
            bindings = JsonNode.Parse(postedBinding)?.AsObject()
                ?? throw new InvalidOperationException("不是 JSON 对象");
        } catch (Exception ex) {
            throw new InvalidOperationException($"workflowBinding 无效：{ex.Message}", ex);
        }
    } else {
        prompt = JsonNode.Parse(await File.ReadAllTextAsync(settings.ResolveWorkflowPath()))?.AsObject()
            ?? throw new InvalidOperationException("workflow_api.json 不是有效对象");
        bindings = JsonNode.Parse(await File.ReadAllTextAsync(settings.ResolveBindingPath()))?.AsObject()
            ?? throw new InvalidOperationException("workflow_binding.json 不是有效对象");
    }
    if (prompt.ContainsKey("nodes") || prompt.ContainsKey("links"))
        return new(null, Results.BadRequest(new { success = false, message = "workflow_api.json 必须是 API Format" }));

    var fields = bindings["fields"] as JsonObject ?? throw new InvalidOperationException("工作流绑定缺少 fields");
    bool Has(string key) => fields.ContainsKey(key);
    (JsonObject Node, string Input) Field(string key) {
        var binding = fields[key] as JsonObject ?? throw new InvalidOperationException($"工作流绑定缺少字段 {key}");
        var nodeId = binding["nodeId"]?.GetValue<string>();
        var title = binding["nodeTitle"]?.GetValue<string>() ?? throw new InvalidOperationException($"字段 {key} 缺少 nodeTitle");
        var input = binding["input"]?.GetValue<string>() ?? throw new InvalidOperationException($"字段 {key} 缺少 input");
        var match = nodeId != null && prompt[nodeId] is JsonObject nodeById
            ? new KeyValuePair<string, JsonNode?>(nodeId, nodeById)
            : prompt.FirstOrDefault(x => x.Value?["_meta"]?["title"]?.GetValue<string>() == title);
        return (match.Value as JsonObject ?? throw new InvalidOperationException($"工作流中找不到标题为“{title}”的节点（绑定：{key}）"), input);
    }
    static void SetInput(JsonObject node, string name, JsonNode? value, string binding) {
        var inputs = node["inputs"] as JsonObject ?? throw new InvalidOperationException($"绑定节点 {binding} 缺少 inputs");
        if (!inputs.ContainsKey(name)) throw new InvalidOperationException($"绑定节点 {binding} 缺少输入 {name}");
        inputs[name] = value;
    }
    void Write(string key, JsonNode? value) { var field = Field(key); SetInput(field.Node, field.Input, value, key); }
    void WriteIf(string key, JsonNode? value) { if (Has(key)) Write(key, value); }
    JsonNode PostedScalar(string key) {
        var field = Field(key);
        var original = field.Node["inputs"]?[field.Input];
        var raw = form[key].ToString();
        if (original is JsonValue value) {
            if (value.TryGetValue<bool>(out _) && bool.TryParse(raw, out var boolValue)) return JsonValue.Create(boolValue)!;
            if (value.TryGetValue<long>(out _) && long.TryParse(raw, System.Globalization.NumberStyles.Integer, System.Globalization.CultureInfo.InvariantCulture, out var longValue)) return JsonValue.Create(longValue)!;
            if (value.TryGetValue<double>(out _) && double.TryParse(raw, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var doubleValue)) return JsonValue.Create(doubleValue)!;
        }
        // Do not reject malformed frontend values here. Keeping the original value
        // lets ComfyUI perform the authoritative node/input validation.
        return JsonValue.Create(raw)!;
    }
    int? PostedInt(string key) => int.TryParse(form[key].ToString(), System.Globalization.NumberStyles.Integer,
        System.Globalization.CultureInfo.InvariantCulture, out var value) ? value : null;
    double? PostedDouble(string key) => double.TryParse(form[key].ToString(), System.Globalization.NumberStyles.Float,
        System.Globalization.CultureInfo.InvariantCulture, out var value) ? value : null;
    foreach (var key in new[] { "sourceImage", "styleReference1", "styleReference2", "styleReference3", "styleReference4", "poseReference" }) {
        if (!Has(key)) continue;
        var file = form.Files.GetFile(key);
        if (file == null) continue;
        using var content = new MultipartFormDataContent();
        using var stream = file.OpenReadStream();
        using var image = new StreamContent(stream);
        if (!string.IsNullOrWhiteSpace(file.ContentType)) image.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(file.ContentType);
        content.Add(image, "image", Path.GetFileName(file.FileName));
        content.Add(new StringContent("true"), "overwrite");
        using var uploadResponse = await factory.CreateClient("comfy").PostAsync("upload/image", content);
        var uploadText = await uploadResponse.Content.ReadAsStringAsync();
        if (!uploadResponse.IsSuccessStatusCode)
            return new(null, Results.Content(uploadText, uploadResponse.Content.Headers.ContentType?.ToString() ?? "application/json",
                statusCode: (int)uploadResponse.StatusCode));
        JsonNode? uploadJson;
        try { uploadJson = JsonNode.Parse(uploadText); }
        catch (Exception ex) { throw new InvalidOperationException($"上传参考图 {key} 失败（HTTP {(int)uploadResponse.StatusCode}）：{uploadText[..Math.Min(uploadText.Length, 300)]}", ex); }
        if (uploadJson?["name"] == null) throw new InvalidOperationException($"ComfyUI 上传成功响应缺少图片名称：{uploadText[..Math.Min(uploadText.Length, 300)]}");
        Write(key, uploadJson["name"]!.GetValue<string>());
    }
    if (Has("positivePrompt")) Write("positivePrompt", PostedScalar("positivePrompt"));
    if (Has("negativePrompt")) Write("negativePrompt", PostedScalar("negativePrompt"));
    if (Has("loras")) ApplyDynamicLoras(prompt, form["loras"].ToString());
    var outputWidth = Has("width") ? PostedInt("width") : null;
    var outputHeight = Has("height") ? PostedInt("height") : null;
    var tileUpscaleNodeIds = prompt
        .Where(entry => entry.Value?["class_type"]?.GetValue<string>()?.Contains("UltimateSDUpscale", StringComparison.OrdinalIgnoreCase) == true &&
                        entry.Value?["inputs"]?["upscale_by"] != null)
        .Select(entry => entry.Key)
        .ToHashSet(StringComparer.Ordinal);
    var usesTileUpscale = tileUpscaleNodeIds.Count > 0 && Has("width") && Has("height");
    if (Has("width")) Write("width", usesTileUpscale && outputWidth.HasValue ? outputWidth.Value * 3 / 4 : PostedScalar("width"));
    if (Has("height")) Write("height", usesTileUpscale && outputHeight.HasValue ? outputHeight.Value * 3 / 4 : PostedScalar("height"));
    if (Has("batchSize")) Write("batchSize", PostedScalar("batchSize"));
    if (usesTileUpscale && outputWidth.HasValue && outputHeight.HasValue) {
        foreach (var entry in prompt.Where(entry => tileUpscaleNodeIds.Contains(entry.Key)))
            if (entry.Value?["inputs"] is JsonObject tileInputs) tileInputs["upscale_by"] = 4.0 / 3.0;
        foreach (var entry in prompt) {
            if (entry.Value?["class_type"]?.GetValue<string>()?.Equals("ImageScale", StringComparison.OrdinalIgnoreCase) != true ||
                entry.Value?["inputs"] is not JsonObject scaleInputs ||
                scaleInputs["image"] is not JsonArray imageLink || imageLink.Count == 0 ||
                imageLink[0] is not JsonValue sourceValue || !sourceValue.TryGetValue<string>(out var sourceId) ||
                !tileUpscaleNodeIds.Contains(sourceId)) continue;
            if (scaleInputs.ContainsKey("width")) scaleInputs["width"] = outputWidth.Value;
            if (scaleInputs.ContainsKey("height")) scaleInputs["height"] = outputHeight.Value;
        }
    }
    if (Has("width")) WriteIf("finalWidth", PostedScalar("width"));
    if (Has("height")) WriteIf("finalHeight", PostedScalar("height"));
    JsonNode? actualSeed = Has("seed")
        ? (string.Equals(form["randomSeed"], "true", StringComparison.OrdinalIgnoreCase)
            ? JsonValue.Create(Random.Shared.NextInt64(0, long.MaxValue)) : PostedScalar("seed"))
        : null;
    if (actualSeed != null) {
        WriteIf("seed", actualSeed.DeepClone());
        WriteIf("secondPassSeed", actualSeed.DeepClone());
        WriteIf("faceDetailerSeed", actualSeed.DeepClone());
    }
    foreach (var key in new[] { "steps", "cfg", "sampler", "scheduler", "denoise", "styleStrength", "openPoseStrength",
                 "secondPassSteps", "secondPassDenoise", "faceDetailerSteps", "faceDetailerDenoise" })
        if (Has(key)) Write(key, PostedScalar(key));
    if (Has("checkpoint") && fields["checkpoint"]?["fixedValue"] == null) Write("checkpoint", PostedScalar("checkpoint"));
    if (Has("cutoutTarget")) {
        var target = form["cutoutTarget"].ToString();
        var binding = fields["cutoutTarget"] as JsonObject
            ?? throw new InvalidOperationException("抠图目标绑定无效");
        var field = Field("cutoutTarget");
        var linkKey = target == "person" ? "personAlpha" : target == "background" ? "backgroundAlpha" : null;
        SetInput(field.Node, field.Input, linkKey != null && binding[linkKey] is JsonArray alphaLink
            ? alphaLink.DeepClone() : JsonValue.Create(target), "cutoutTarget");
    }
    foreach (var dynamicField in fields.Where(field => field.Key.StartsWith("node_", StringComparison.Ordinal)))
        Write(dynamicField.Key, PostedScalar(dynamicField.Key));
    if (Has("openPoseStrength") && form["controlMode"].ToString() == "none") {
        var controlNode = Field("openPoseStrength").Node;
        var controlEntry = prompt.First(x => ReferenceEquals(x.Value, controlNode));
        var controlInputs = controlNode["inputs"]!.AsObject();
        foreach (var outputIndex in new[] { 0, 1 }) {
            var source = controlInputs[outputIndex == 0 ? "positive" : "negative"]?.DeepClone()
                ?? throw new InvalidOperationException("OpenPose 节点缺少旁路输入");
            foreach (var target in prompt.Select(x => x.Value).OfType<JsonObject>())
                if (target["inputs"] is JsonObject targetInputs)
                    foreach (var input in targetInputs.ToList())
                        if (input.Value is JsonArray link && link[0]?.ToString() == controlEntry.Key && link[1]?.GetValue<int>() == outputIndex)
                            targetInputs[input.Key] = source.DeepClone();
        }
        prompt.Remove(controlEntry.Key);
    }
    var checkpoint = fields["checkpoint"] as JsonObject;
    if (checkpoint?["fixedValue"] is JsonNode fixedCheckpoint) WriteIf("checkpoint", fixedCheckpoint.DeepClone());
    var selectedKrea2Model = prompt.Select(entry => entry.Value).OfType<JsonObject>().Any(node =>
        node["class_type"]?.GetValue<string>() == "UNETLoader" &&
        node["inputs"]?["unet_name"]?.GetValue<string>()?.Contains("krea2", StringComparison.OrdinalIgnoreCase) == true);
    var usesKrea2TextEncoder = prompt.Select(entry => entry.Value).OfType<JsonObject>().Any(node =>
        node["class_type"]?.GetValue<string>() == "CLIPLoader" &&
        node["inputs"]?["type"]?.GetValue<string>()?.Equals("krea2", StringComparison.OrdinalIgnoreCase) == true);
    if (selectedKrea2Model && !usesKrea2TextEncoder)
        return new(null, Results.UnprocessableEntity(new { success = false, message = "当前工作流使用的不是 Krea2 文本编码器，不能加载 Krea2 主模型；请选择 Krea2 工作流" }));
    var requestedPrefix = form["filenamePrefix"].ToString().Trim();
    var safePrefix = Path.GetFileName(requestedPrefix.Replace('\\', '/'));
    if (string.IsNullOrWhiteSpace(safePrefix)) safePrefix = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();
    var filenamePrefix = $"{(form["folder"].ToString().Trim() is { Length: > 0 } f ? f : "aimaid")}/{safePrefix}";
    Write("filenamePrefix", filenamePrefix);
    if (!string.Equals(form["generateTransparent"].ToString(), "true", StringComparison.OrdinalIgnoreCase) &&
        bindings["optionalOutputs"]?["transparent"]?.GetValue<string>() is { Length: > 0 } transparentOutputId)
        prompt.Remove(transparentOutputId);
    var output = bindings["outputNode"] as JsonObject ?? throw new InvalidOperationException("工作流绑定缺少 outputNode");
    var outputId = output["nodeId"]?.GetValue<string>();
    var outputTitle = output["title"]?.GetValue<string>() ?? "最终输出";
    var finalEntry = outputId != null && prompt[outputId]?["class_type"]?.GetValue<string>() == "SaveImage"
        ? new KeyValuePair<string, JsonNode?>(outputId, prompt[outputId])
        : prompt.FirstOrDefault(x => x.Value?["_meta"]?["title"]?.GetValue<string>() == outputTitle && x.Value?["class_type"]?.GetValue<string>() == "SaveImage");
    if (finalEntry.Value == null) throw new InvalidOperationException("找不到标题为“最终输出”的 SaveImage 节点");
    var finalId = finalEntry.Key;
    var promptId = Guid.NewGuid().ToString("D");
    var returnedSeed = actualSeed is JsonValue returnedSeedValue && returnedSeedValue.TryGetValue<long>(out var parsedSeed) ? parsedSeed : (long?)null;
    var queueItem = new BridgeQueuedGeneration {
        PromptId = promptId,
        Prompt = prompt,
        ClientId = form["clientId"].ToString(),
        CreatedAt = DateTimeOffset.Now,
        State = "PENDING"
    };
    return new(new PreparedGeneration(queueItem, promptId, finalId, returnedSeed,
        Has("width") ? PostedInt("width") : null, Has("height") ? PostedInt("height") : null, null, null), null);
}

IResult GenerationAccepted(PreparedGeneration item) => Results.Json(new {
    success = true, promptId = item.PromptId, state = "PENDING", finalOutputNodeId = item.FinalOutputNodeId, queueOwner = "BRIDGE",
    actualSeed = item.ActualSeed, width = item.Width, height = item.Height
}, statusCode: StatusCodes.Status202Accepted);

app.MapGet("/api/gallery/file", (string path) => {
    var file = ResolveGalleryPath(path, mustExist: true);
    var contentType = Path.GetExtension(file).ToLowerInvariant() switch {
        ".png" => "image/png", ".jpg" or ".jpeg" => "image/jpeg", ".webp" => "image/webp", _ => "application/octet-stream"
    };
    return Results.File(file, contentType, enableRangeProcessing: true);
});
app.MapPost("/api/gallery/open-folder", (AssetPathRequest request) => {
    var file = ResolveGalleryPath(request.Path ?? "", mustExist: true);
    OpenFileInFolder(file);
    return Results.Ok(new { success = true, directory = Path.GetDirectoryName(file) });
}).DisableAntiforgery();

app.MapGet("/api/assets/file", (string path) => {
    var file = ResolveAssetPath(path);
    var contentType = Path.GetExtension(file).ToLowerInvariant() switch { ".png" => "image/png", ".jpg" or ".jpeg" => "image/jpeg", ".webp" => "image/webp", _ => "application/octet-stream" };
    return Results.File(file, contentType, enableRangeProcessing: true);
});
app.MapPost("/api/assets/open-folder", (AssetPathRequest request) => {
    var file = ResolveAssetPath(request.Path ?? "");
    var directory = Path.GetDirectoryName(file) ?? throw new InvalidOperationException("资产所在文件夹不存在");
    OpenFileInFolder(file);
    return Results.Ok(new { success = true, directory });
}).DisableAntiforgery();
app.MapPost("/api/assets/delete", (GalleryPathsRequest request) => {
    var paths = request.Paths.Select(path => (path ?? "").Trim()).Where(path => path.Length > 0).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
    if (paths.Length == 0) return Results.BadRequest(new { success = false, message = "请选择要删除的资产" });
    var deleted = 0;
    foreach (var path in paths) {
        var file = ResolveAssetPath(path, mustExist: false);
        if (File.Exists(file) && IsGalleryImage(file)) { File.Delete(file); deleted++; }
    }
    return Results.Ok(new { success = true, deleted });
}).DisableAntiforgery();

app.MapPost("/api/assets/move", (GalleryPathsRequest request) => {
    var paths = request.Paths.Select(path => (path ?? "").Trim()).Where(path => path.Length > 0).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
    if (paths.Length == 0) return Results.BadRequest(new { success = false, message = "请选择要迁移的资产" });
    var destination = Path.GetFullPath(GetMigrationDirectory());
    Directory.CreateDirectory(destination);
    var sources = paths.Select(path => {
        var source = Path.GetFullPath(path);
        if (!File.Exists(source) || !IsGalleryImage(source)) throw new InvalidOperationException($"资产图片不存在：{path}");
        return source;
    }).ToArray();
    var migrationPlan = PlanImageMigrations(sources, destination);
    if (migrationPlan.Error != null) return Results.Conflict(new { success = false, message = migrationPlan.Error });
    var assets = new List<object>();
    foreach (var (source, target, _) in migrationPlan.Files) {
        File.Move(source, target);
        var image = ToGalleryImage(new GalleryFile { FullPath = target, Path = target, Filename = Path.GetFileName(target), CreatedAt = new DateTimeOffset(File.GetLastWriteTime(target)) });
        assets.Add(new { oldPath = source, localPath = target,
            localUrl = $"http://127.0.0.1:32145/api/assets/file?path={Uri.EscapeDataString(target)}",
            fileName = image.Filename, fileSize = image.SizeBytes, width = image.Width, height = image.Height });
    }
    return Results.Ok(new { success = true, platform = settings.PlatformName, assets });
}).DisableAntiforgery();

app.MapGet("/api/maid-ai/settings", () => Results.Ok(new { success = true, directory = GetMaidAiDirectory() }));
app.MapPost("/api/maid-ai/settings", async (MaidAiSettingsRequest request) => {
    var directory = (request.Directory ?? "").Trim();
    if (!Path.IsPathFullyQualified(directory)) return Results.BadRequest(new { success = false, message = "女仆AI 图片目录必须是完整路径" });
    directory = Path.GetFullPath(directory);
    Directory.CreateDirectory(directory);
    var saved = await UpdateBridgeUserSettings(userSettings => userSettings.MaidAiDirectory = directory);
    return Results.Ok(new { success = true, directory = saved.MaidAiDirectory });
}).DisableAntiforgery();
app.MapPost("/api/maid-ai/copy", (GalleryPathsRequest request) => {
    var paths = request.Paths.Select(path => (path ?? "").Trim()).Where(path => path.Length > 0).Distinct(PathComparer()).ToArray();
    if (paths.Length == 0) return Results.BadRequest(new { success = false, message = "请选择要迁移到女仆AI的资产" });
    var destination = Path.GetFullPath(GetMaidAiDirectory());
    Directory.CreateDirectory(destination);
    var copied = new List<string>();
    var existing = new List<string>();
    foreach (var path in paths) {
        var source = ResolveAssetPath(path);
        var sourceHash = FileSha256(source);
        var target = ResolveMaidAiCopyTarget(source, sourceHash, destination, out var alreadyExists);
        if (alreadyExists) {
            existing.Add(target);
            continue;
        }
        File.Copy(source, target, overwrite: false);
        copied.Add(target);
    }
    return Results.Ok(new { success = true, directory = destination, copied = copied.Count, existing = existing.Count, files = copied.Concat(existing) });
}).DisableAntiforgery();

app.MapGet("/api/migration/settings", () => Results.Ok(new { success = true, directory = GetMigrationDirectory() }));
app.MapPost("/api/migration/settings", async (MigrationSettingsRequest request) => {
    var directory = (request.Directory ?? "").Trim();
    if (!Path.IsPathFullyQualified(directory)) return Results.BadRequest(new { success = false, message = "迁移目录必须是完整路径" });
    directory = Path.GetFullPath(directory);
    Directory.CreateDirectory(directory);
    var saved = await UpdateBridgeUserSettings(userSettings => userSettings.Directory = directory);
    return Results.Ok(new { success = true, directory = saved.Directory });
}).DisableAntiforgery();
app.MapPost("/api/migration/folders", async (FolderRequest request) => {
    var name = (request.Name ?? "").Trim();
    if (name.Length == 0 || name.Length > 80 || name is "." or ".." || name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0)
        return Results.BadRequest(new { success = false, message = "迁移文件夹名称不合法" });
    var root = Path.GetFullPath(GetMigrationDirectory());
    var directory = Path.GetFullPath(Path.Combine(root, name));
    var prefix = root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
    if (!directory.StartsWith(prefix, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal))
        return Results.BadRequest(new { success = false, message = "迁移文件夹路径不合法" });
    Directory.CreateDirectory(directory);
    await UpdateBridgeUserSettings(userSettings => userSettings.Directory = directory);
    return Results.Ok(new { success = true, directory });
}).DisableAntiforgery();

app.MapPost("/api/gallery/delete", async (GalleryPathsRequest request) => {
    var paths = request.Paths.Select(NormalizeRelativePath).Where(path => path.Length > 0).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
    if (paths.Length == 0) return Results.BadRequest(new { success = false, message = "请选择要删除的图片" });
    var deleted = 0;
    foreach (var path in paths) {
        var file = ResolveGalleryPath(path, mustExist: false);
        if (File.Exists(file) && IsGalleryImage(file)) { File.Delete(file); deleted++; }
    }
    return Results.Ok(new { success = true, deleted });
}).DisableAntiforgery();

app.MapPost("/api/gallery/move", async (GalleryMoveRequest request) => {
    var paths = request.Paths.Select(NormalizeRelativePath).Where(path => path.Length > 0).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
    if (paths.Length == 0) return Results.BadRequest(new { success = false, message = "请选择要迁移的图片" });
    var root = GalleryRoot();
    var folder = (request.Folder ?? "").Trim();
    var externalDestination = folder.Length == 0;
    var destination = externalDestination ? GetMigrationDirectory() : ResolveGalleryPath(folder, mustExist: false);
    Directory.CreateDirectory(destination);
    var sourceFiles = new List<(string OldPath, string Source)>();
    foreach (var oldPath in paths) {
        var source = ResolveGalleryPath(oldPath, mustExist: true);
        if (!IsGalleryImage(source)) throw new InvalidOperationException($"不是可迁移的图片：{oldPath}");
        sourceFiles.Add((oldPath, source));
    }
    var migrationPlan = PlanImageMigrations(sourceFiles.Select(file => file.Source), destination);
    if (migrationPlan.Error != null) return Results.Conflict(new { success = false, message = migrationPlan.Error });
    var oldPathBySource = sourceFiles.GroupBy(file => file.Source, PathComparer())
        .ToDictionary(group => group.Key, group => group.First().OldPath, PathComparer());
    var planned = migrationPlan.Files.Select(file => {
        var newPath = externalDestination ? file.Target : NormalizeRelativePath(Path.GetRelativePath(root, file.Target));
        return (OldPath: oldPathBySource[file.Source], NewPath: newPath, file.Source, file.Target);
    }).ToList();
    var moved = new List<(string OldPath, string NewPath, string Source, string Target)>();
    try {
        foreach (var (oldPath, newPath, source, target) in planned) {
            File.Move(source, target);
            moved.Add((oldPath, newPath, source, target));
        }
        if (moved.Count == 0) return Results.BadRequest(new { success = false, message = "图片已经在目标文件夹" });
        var assets = externalDestination ? moved.Select(file => {
            var image = ToGalleryImage(new GalleryFile { FullPath = file.Target, Path = file.Target, Filename = Path.GetFileName(file.Target), CreatedAt = new DateTimeOffset(File.GetLastWriteTime(file.Target)) });
            return new {
                oldPath = file.OldPath,
                localPath = file.Target, localUrl = $"http://127.0.0.1:32145/api/assets/file?path={Uri.EscapeDataString(file.Target)}", fileName = image.Filename, fileSize = image.SizeBytes,
                width = image.Width, height = image.Height
            };
        }).ToArray() : Array.Empty<object>();
        return Results.Ok(new { success = true, moved = moved.Count, folder = destination, platform = settings.PlatformName, assets });
    } catch {
        foreach (var file in moved.AsEnumerable().Reverse()) if (File.Exists(file.Target) && !File.Exists(file.Source)) File.Move(file.Target, file.Source);
        throw;
    }
}).DisableAntiforgery();
app.MapGet("/api/folders", () => {
    var output = settings.ActiveProfile.OutputDirectory;
    var folders = Directory.Exists(output) ? Directory.GetDirectories(output).Select(Path.GetFileName).Where(name => !string.IsNullOrWhiteSpace(name)).OrderBy(name => name).ToArray() : Array.Empty<string?>();
    return Results.Ok(new { success = true, folders });
});
app.MapPost("/api/folders", (FolderRequest request) => {
    var name = (request.Name ?? "").Trim();
    if (name.Length == 0 || name.Length > 60 || name == "." || name == ".." || name.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0)
        return Results.BadRequest(new { success = false, message = "Invalid folder name" });
    var root = Path.GetFullPath(settings.ActiveProfile.OutputDirectory); var target = Path.GetFullPath(Path.Combine(root, name));
    if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) return Results.BadRequest(new { success = false, message = "Invalid folder path" });
    Directory.CreateDirectory(target); return Results.Ok(new { success = true, name });
});
app.MapPost("/api/folders/open", (FolderRequest request) => {
    var name = (request.Name ?? "").Trim(); var root = Path.GetFullPath(settings.ActiveProfile.OutputDirectory); var target = Path.GetFullPath(Path.Combine(root, name));
    if (name.Length == 0 || !target.StartsWith(root, StringComparison.OrdinalIgnoreCase) || !Directory.Exists(target)) return Results.BadRequest(new { success = false, message = "Folder does not exist" });
    var opener = OperatingSystem.IsMacOS() ? "open" : "explorer.exe";
    Process.Start(new ProcessStartInfo(opener, $"\"{target}\"") { UseShellExecute = true });
    return Results.Ok(new { success = true, message = "Folder opened" });
});
app.MapPost("/api/history/move", async (HistoryMoveRequest request, IHttpClientFactory factory) => {
    var promptIds = request.PromptIds.Where(id => Guid.TryParse(id, out _)).Distinct().ToArray();
    if (promptIds.Length == 0 || promptIds.Length != request.PromptIds.Length)
        return Results.BadRequest(new { success = false, message = "请选择有效的生成任务" });
    var folder = (request.Folder ?? "").Trim();
    if (folder.Length == 0 || folder != Path.GetFileName(folder))
        return Results.BadRequest(new { success = false, message = "目标文件夹不合法" });
    var root = Path.GetFullPath(settings.ActiveProfile.OutputDirectory);
    var destination = Path.GetFullPath(Path.Combine(root, folder));
    var rootPrefix = root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
    if (!destination.StartsWith(rootPrefix, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal) || !Directory.Exists(destination))
        return Results.BadRequest(new { success = false, message = "目标文件夹不存在" });

    var client = factory.CreateClient("comfy");
    var sourceFiles = new List<string>();
    foreach (var promptId in promptIds) {
        var history = await client.GetFromJsonAsync<JsonObject>($"history/{promptId}");
        var item = history?[promptId];
        if (item == null) return Results.NotFound(new { success = false, message = $"任务 {promptId} 不存在" });
        if (item["outputs"] is JsonObject outputs) foreach (var output in outputs) {
            if (output.Value?["images"] is not JsonArray images) continue;
            foreach (var image in images) {
                var filename = image?["filename"]?.GetValue<string>();
                var subfolder = image?["subfolder"]?.GetValue<string>() ?? "";
                if (string.IsNullOrWhiteSpace(filename)) continue;
                var source = Path.GetFullPath(Path.Combine(root, subfolder, filename));
                if (!source.StartsWith(rootPrefix, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal) || !File.Exists(source)) continue;
                sourceFiles.Add(source);
            }
        }
    }
    if (sourceFiles.Count == 0) return Results.BadRequest(new { success = false, message = "选中任务没有可迁移的图片" });
    var migrationPlan = PlanImageMigrations(sourceFiles.Distinct(PathComparer()), destination);
    if (migrationPlan.Error != null) return Results.Conflict(new { success = false, message = migrationPlan.Error });

    var moved = new List<(string Source, string Target)>();
    try {
        foreach (var file in migrationPlan.Files) { File.Move(file.Source, file.Target); moved.Add((file.Source, file.Target)); }
        using var deleteResponse = await client.PostAsJsonAsync("history", new { delete = promptIds });
        deleteResponse.EnsureSuccessStatusCode();
    } catch {
        foreach (var file in moved.AsEnumerable().Reverse()) if (File.Exists(file.Target) && !File.Exists(file.Source)) File.Move(file.Target, file.Source);
        throw;
    }
    return Results.Ok(new { success = true, moved = moved.Count, tasks = promptIds.Length, folder });
}).DisableAntiforgery();
app.MapDelete("/api/history/{promptId}", async (string promptId, IHttpClientFactory factory) => {
    if (!Guid.TryParse(promptId, out _)) return Results.BadRequest(new { success = false, message = "Invalid prompt id" });
    var client = factory.CreateClient("comfy");
    var history = await client.GetFromJsonAsync<JsonObject>($"history/{promptId}");
    var item = history?[promptId];
    if (item == null) return Results.NotFound(new { success = false, message = "Local history item not found" });
    var deleted = 0;
    if (item["outputs"] is JsonObject outputs) foreach (var output in outputs) {
        if (output.Value?["images"] is not JsonArray images) continue;
        foreach (var image in images) {
            var filename = image?["filename"]?.GetValue<string>();
            var subfolder = image?["subfolder"]?.GetValue<string>() ?? "";
            if (string.IsNullOrWhiteSpace(filename)) continue;
            var root = Path.GetFullPath(settings.ActiveProfile.OutputDirectory);
            var target = Path.GetFullPath(Path.Combine(root, subfolder, filename));
            if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) continue;
            if (File.Exists(target)) { File.Delete(target); deleted++; }
        }
    }
    using var response = await client.PostAsJsonAsync("history", new { delete = new[] { promptId } });
    response.EnsureSuccessStatusCode();
    return Results.Ok(new { success = true, deleted, message = "Local history deleted" });
});

app.MapMethods("/comfy/{**path}", new[] { "GET", "POST" }, async (string? path, HttpContext context, IHttpClientFactory factory) => {
    var target = (path ?? "") + context.Request.QueryString;
    using var request = new HttpRequestMessage(new HttpMethod(context.Request.Method), target);
    if (context.Request.ContentLength > 0 || context.Request.Headers.ContainsKey("Transfer-Encoding")) request.Content = new StreamContent(context.Request.Body);
    // Never forward browser security context headers (Origin, Referer, Sec-Fetch-*,
    // PNA or local auth) to ComfyUI. The bridge already validated them. Forward
    // only the two headers required by the ComfyUI HTTP API.
    if (context.Request.Headers.TryGetValue("Accept", out var accept))
        request.Headers.TryAddWithoutValidation("Accept", accept.ToArray());
    if (request.Content != null && context.Request.Headers.TryGetValue("Content-Type", out var contentType))
        request.Content.Headers.TryAddWithoutValidation("Content-Type", contentType.ToArray());
    using var response = await factory.CreateClient("comfy").SendAsync(request, HttpCompletionOption.ResponseHeadersRead, context.RequestAborted);
    context.Response.StatusCode = (int)response.StatusCode;
    foreach (var header in response.Headers) CopySafeResponseHeader(context.Response, header.Key, header.Value);
    foreach (var header in response.Content.Headers) CopySafeResponseHeader(context.Response, header.Key, header.Value);
    context.Response.Headers.Remove("transfer-encoding");
    await response.Content.CopyToAsync(context.Response.Body, context.RequestAborted);
});

_ = ProcessBridgeGenerationQueue(app.Lifetime.ApplicationStopping);
app.Run();

if (Volatile.Read(ref bridgeExitMode) == 2) {
    var startInfo = CreateBridgeStartInfo();
    _ = Process.Start(startInfo) ?? throw new InvalidOperationException("Failed to restart Local ComfyUI Bridge");
}

string BridgeGenerationQueuePath() => Path.Combine(AppContext.BaseDirectory, "data", "generation-queue.json");

List<BridgeQueuedGeneration> ReadBridgeGenerationQueue() {
    var path = BridgeGenerationQueuePath();
    if (!File.Exists(path)) return new List<BridgeQueuedGeneration>();
    try {
        return JsonSerializer.Deserialize<List<BridgeQueuedGeneration>>(File.ReadAllText(path), GalleryJsonOptions())
            ?? throw new InvalidDataException($"Bridge 生成队列为空：{path}");
    } catch (JsonException exception) {
        throw new InvalidDataException($"Bridge 生成队列损坏，已停止启动以避免丢失任务：{path}", exception);
    }
}

async Task WriteBridgeGenerationQueue() {
    var path = BridgeGenerationQueuePath();
    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
    var temporary = path + ".tmp";
    await File.WriteAllTextAsync(temporary, JsonSerializer.Serialize(generationQueue, GalleryJsonOptions(writeIndented: true)));
    File.Move(temporary, path, true);
}

async Task EnqueueBridgeGeneration(BridgeQueuedGeneration item) {
    await EnqueueBridgeGenerations(new[] { item });
}

async Task EnqueueBridgeGenerations(IEnumerable<BridgeQueuedGeneration> items) {
    var batch = items.ToArray();
    if (batch.Length == 0 || batch.Length > 1000) throw new InvalidOperationException("Bridge 批量生成数量必须在 1 到 1000 之间");
    await generationQueueLock.WaitAsync();
    try {
        generationQueue.RemoveAll(entry => entry.State is "FAILED" or "SUCCEEDED" or "CANCELLED" &&
            DateTimeOffset.Now - (entry.CompletedAt ?? entry.CreatedAt) > TimeSpan.FromDays(7));
        if (generationQueue.Count(entry => entry.State == "PENDING") + batch.Length > 2000)
            throw new InvalidOperationException("Bridge 待生成队列已达到 2000 个任务，请先处理现有队列");
        var promptIds = batch.Select(item => item.PromptId).ToHashSet(StringComparer.OrdinalIgnoreCase);
        if (promptIds.Count != batch.Length || generationQueue.Any(entry => promptIds.Contains(entry.PromptId)))
            throw new InvalidOperationException("Bridge 批量队列中存在重复任务 ID");
        generationQueue.AddRange(batch);
        await WriteBridgeGenerationQueue();
    } finally { generationQueueLock.Release(); }
    generationQueueSignal.Release();
}

async Task<BridgeQueuedGeneration?> FindBridgeQueuedGeneration(string promptId) {
    await generationQueueLock.WaitAsync();
    try {
        var item = generationQueue.FirstOrDefault(entry => string.Equals(entry.PromptId, promptId, StringComparison.OrdinalIgnoreCase));
        return item == null ? null : new BridgeQueuedGeneration {
            PromptId = item.PromptId, ClientId = item.ClientId, CreatedAt = item.CreatedAt,
            SubmittedAt = item.SubmittedAt, CompletedAt = item.CompletedAt, State = item.State, Error = item.Error
        };
    } finally { generationQueueLock.Release(); }
}

async Task<bool> CancelPendingBridgeGeneration(string promptId) {
    await generationQueueLock.WaitAsync();
    try {
        var item = generationQueue.FirstOrDefault(entry => string.Equals(entry.PromptId, promptId, StringComparison.OrdinalIgnoreCase));
        if (item?.State != "PENDING") return false;
        item.State = "CANCELLED";
        item.CompletedAt = DateTimeOffset.Now;
        item.Error = null;
        await WriteBridgeGenerationQueue();
        return true;
    } finally { generationQueueLock.Release(); }
}

async Task<(int Total, int Cancelled, int CancellationRequested, string[] PromptIds)> CancelAllBridgeGenerations() {
    var cancelled = 0;
    var cancellationRequested = 0;
    string[] promptIds;
    await generationQueueLock.WaitAsync();
    try {
        var active = generationQueue
            .Where(entry => entry.State is "PENDING" or "SUBMITTED" or "QUEUED" or "RUNNING" or "CANCEL_REQUESTED")
            .ToArray();
        promptIds = active.Select(entry => entry.PromptId).ToArray();
        foreach (var item in active) {
            if (item.State == "PENDING") {
                item.State = "CANCELLED";
                item.CompletedAt = DateTimeOffset.Now;
                item.Error = null;
                cancelled += 1;
            } else {
                item.State = "CANCEL_REQUESTED";
                cancellationRequested += 1;
            }
        }
        if (active.Length > 0) await WriteBridgeGenerationQueue();
    } finally { generationQueueLock.Release(); }
    if (cancellationRequested > 0) generationQueueSignal.Release();
    return (promptIds.Length, cancelled, cancellationRequested, promptIds);
}

async Task SetBridgeGenerationState(string promptId, string state, string? error = null) {
    await generationQueueLock.WaitAsync();
    try {
        var item = generationQueue.FirstOrDefault(entry => string.Equals(entry.PromptId, promptId, StringComparison.OrdinalIgnoreCase));
        if (item == null || item.State == state && item.Error == error) return;
        item.State = state;
        item.Error = error;
        if (state == "SUBMITTED" && item.SubmittedAt == null) item.SubmittedAt = DateTimeOffset.Now;
        if (state is "FAILED" or "SUCCEEDED" or "CANCELLED") item.CompletedAt = DateTimeOffset.Now;
        await WriteBridgeGenerationQueue();
    } finally { generationQueueLock.Release(); }
}

async Task FailBridgeQueuedGeneration(string promptId, string error) {
    await SetBridgeGenerationState(promptId, "FAILED", error);
    await AppendAgentLog("error", "generation.queue.failed", error, new { promptId });
}

string? ReadComfyExecutionFailure(JsonNode? historyItem) {
    if (historyItem == null) return null;
    if (historyItem["status"]?["messages"] is JsonArray messages)
        foreach (var message in messages)
            if (message is JsonArray cells && cells.Count > 1 && cells[0]?.ToString() == "execution_error")
                return cells[1]?["exception_message"]?.ToString() ?? cells[1]?["exception_type"]?.ToString() ?? "ComfyUI 执行失败";
    return string.Equals(historyItem["status"]?["status_str"]?.ToString(), "error", StringComparison.OrdinalIgnoreCase)
        ? "ComfyUI 执行失败" : null;
}

async Task ProcessBridgeGenerationQueue(CancellationToken cancellationToken) {
    var factory = app.Services.GetRequiredService<IHttpClientFactory>();
    while (!cancellationToken.IsCancellationRequested) {
        List<BridgeQueuedGeneration> active;
        await generationQueueLock.WaitAsync(cancellationToken);
        try {
            active = generationQueue
                .Where(entry => entry.State is "PENDING" or "SUBMITTED" or "QUEUED" or "RUNNING" or "CANCEL_REQUESTED")
                .OrderBy(entry => entry.CreatedAt)
                .ToList();
        } finally { generationQueueLock.Release(); }
        if (active.Count == 0) {
            await generationQueueSignal.WaitAsync(TimeSpan.FromSeconds(1), cancellationToken);
            continue;
        }
        try {
            var client = factory.CreateClient("comfy");
            using var queueResponse = await client.GetAsync("queue", cancellationToken);
            queueResponse.EnsureSuccessStatusCode();
            var queue = JsonNode.Parse(await queueResponse.Content.ReadAsStringAsync(cancellationToken)) as JsonObject
                ?? throw new InvalidOperationException("ComfyUI queue 返回格式无效");
            bool Contains(JsonObject source, string key, string id) => source[key] is JsonArray rows && rows.Any(row =>
                row is JsonArray cells && cells.Count > 1 && string.Equals(cells[1]?.ToString(), id, StringComparison.Ordinal));
            foreach (var tracked in active.Where(entry => entry.State != "PENDING")) {
                if (tracked.State == "CANCEL_REQUESTED") {
                    if (Contains(queue, "queue_pending", tracked.PromptId)) {
                        using var deleteResponse = await client.PostAsJsonAsync("queue", new { delete = new[] { tracked.PromptId } }, cancellationToken);
                        deleteResponse.EnsureSuccessStatusCode();
                        await SetBridgeGenerationState(tracked.PromptId, "CANCELLED");
                        continue;
                    }
                    if (Contains(queue, "queue_running", tracked.PromptId)) {
                        using var interruptResponse = await client.PostAsJsonAsync("interrupt", new { prompt_id = tracked.PromptId }, cancellationToken);
                        interruptResponse.EnsureSuccessStatusCode();
                        continue;
                    }
                    using var cancelledHistoryResponse = await client.GetAsync($"history/{Uri.EscapeDataString(tracked.PromptId)}", cancellationToken);
                    cancelledHistoryResponse.EnsureSuccessStatusCode();
                    var cancelledHistory = JsonNode.Parse(await cancelledHistoryResponse.Content.ReadAsStringAsync(cancellationToken)) as JsonObject
                        ?? throw new InvalidOperationException("ComfyUI history 返回格式无效");
                    var cancelledHistoryItem = cancelledHistory[tracked.PromptId];
                    if (cancelledHistoryItem != null && ReadComfyExecutionFailure(cancelledHistoryItem) == null)
                        await SetBridgeGenerationState(tracked.PromptId, "SUCCEEDED");
                    else
                        await SetBridgeGenerationState(tracked.PromptId, "CANCELLED");
                    continue;
                }
                if (Contains(queue, "queue_running", tracked.PromptId)) {
                    await SetBridgeGenerationState(tracked.PromptId, "RUNNING");
                    continue;
                }
                if (Contains(queue, "queue_pending", tracked.PromptId)) {
                    await SetBridgeGenerationState(tracked.PromptId, "QUEUED");
                    continue;
                }
                using var historyResponse = await client.GetAsync($"history/{Uri.EscapeDataString(tracked.PromptId)}", cancellationToken);
                historyResponse.EnsureSuccessStatusCode();
                var history = JsonNode.Parse(await historyResponse.Content.ReadAsStringAsync(cancellationToken)) as JsonObject
                    ?? throw new InvalidOperationException("ComfyUI history 返回格式无效");
                var historyItem = history[tracked.PromptId];
                if (historyItem == null) continue;
                var failure = ReadComfyExecutionFailure(historyItem);
                if (failure != null) await FailBridgeQueuedGeneration(tracked.PromptId, failure);
                else await SetBridgeGenerationState(tracked.PromptId, "SUCCEEDED");
            }
            var activeCount = (queue["queue_running"] as JsonArray)?.Count ?? 0;
            activeCount += (queue["queue_pending"] as JsonArray)?.Count ?? 0;
            if (activeCount >= ComfySubmissionWindow) {
                await Task.Delay(750, cancellationToken);
                continue;
            }
            var item = active.FirstOrDefault(entry => entry.State == "PENDING");
            if (item == null) {
                await Task.Delay(750, cancellationToken);
                continue;
            }
            if (Contains(queue, "queue_running", item.PromptId)) {
                await SetBridgeGenerationState(item.PromptId, "RUNNING");
                continue;
            }
            if (Contains(queue, "queue_pending", item.PromptId)) {
                await SetBridgeGenerationState(item.PromptId, "QUEUED");
                continue;
            }
            if (await FindBridgeQueuedGeneration(item.PromptId) is not { State: "PENDING" }) continue;
            using var submit = await client.PostAsJsonAsync("prompt", new {
                prompt = item.Prompt,
                client_id = item.ClientId,
                prompt_id = item.PromptId
            }, cancellationToken);
            var submitText = await submit.Content.ReadAsStringAsync(cancellationToken);
            if (!submit.IsSuccessStatusCode) {
                await FailBridgeQueuedGeneration(item.PromptId, $"ComfyUI 拒绝任务（HTTP {(int)submit.StatusCode}）：{submitText[..Math.Min(submitText.Length, 500)]}");
                continue;
            }
            var result = JsonNode.Parse(submitText) as JsonObject
                ?? throw new InvalidOperationException($"ComfyUI 提交接口返回非 JSON：{submitText[..Math.Min(submitText.Length, 500)]}");
            if (!string.Equals(result["prompt_id"]?.ToString(), item.PromptId, StringComparison.OrdinalIgnoreCase)) {
                await FailBridgeQueuedGeneration(item.PromptId, "ComfyUI 返回的任务 ID 与 Bridge 预分配 ID 不一致");
                continue;
            }
            await SetBridgeGenerationState(item.PromptId, "SUBMITTED");
        } catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) {
            return;
        } catch (HttpRequestException) {
            await Task.Delay(1000, cancellationToken);
        } catch (TaskCanceledException) when (!cancellationToken.IsCancellationRequested) {
            await Task.Delay(1000, cancellationToken);
        } catch (Exception exception) {
            await AppendAgentLog("error", "generation.queue.reconcile", exception.Message, new { exception = exception.ToString() });
            await Task.Delay(1000, cancellationToken);
        }
    }
}

void ScheduleBridgeExit(HttpContext context, IHostApplicationLifetime lifetime, bool restart) {
    Interlocked.Exchange(ref bridgeExitMode, restart ? 2 : 1);
    context.Response.OnCompleted(() => {
        lifetime.StopApplication();
        return Task.CompletedTask;
    });
}

ProcessStartInfo CreateBridgeStartInfo() {
    var processPath = Environment.ProcessPath
        ?? throw new InvalidOperationException("Cannot resolve the Local ComfyUI Bridge executable path");
    var startInfo = new ProcessStartInfo(processPath) {
        WorkingDirectory = AppContext.BaseDirectory,
        UseShellExecute = false,
        CreateNoWindow = true
    };
    var commandLine = Environment.GetCommandLineArgs();
    if (string.Equals(Path.GetFileNameWithoutExtension(processPath), "dotnet", StringComparison.OrdinalIgnoreCase)) {
        if (commandLine.Length == 0 || !File.Exists(commandLine[0]))
            throw new InvalidOperationException("Cannot resolve the Local ComfyUI Bridge assembly path");
        startInfo.ArgumentList.Add(commandLine[0]);
    }
    foreach (var argument in commandLine.Skip(1)) startInfo.ArgumentList.Add(argument);
    return startInfo;
}

void CopySafeResponseHeader(HttpResponse target, string name, IEnumerable<string> values) {
    var safeValues = values.Where(value => value.All(character => character == '\t' || character is >= ' ' and <= '~')).ToArray();
    if (safeValues.Length > 0) target.Headers[name] = safeValues;
}

string AgentLogDirectory() => Path.Combine(AppContext.BaseDirectory, "logs");
string AgentLogPath() => Path.Combine(AgentLogDirectory(), $"aiprovider-{DateTime.Now:yyyyMMdd}.jsonl");
async Task AppendAgentLog(string level, string eventName, string message, object? details = null) {
    try {
        Directory.CreateDirectory(AgentLogDirectory());
        var line = JsonSerializer.Serialize(new { timestamp = DateTimeOffset.Now, level, eventName, message, details });
        await agentLogLock.WaitAsync();
        try { await File.AppendAllTextAsync(AgentLogPath(), line + Environment.NewLine); }
        finally { agentLogLock.Release(); }
    } catch { }
}

string LocalWorkflowSettingsPath() => Path.Combine(AppContext.BaseDirectory, "local-workflow-settings.json");
string GetLocalWorkflowDirectory() {
    try {
        var path = LocalWorkflowSettingsPath();
        if (File.Exists(path)) {
            var configured = JsonNode.Parse(File.ReadAllText(path))?["directory"]?.GetValue<string>();
            if (!string.IsNullOrWhiteSpace(configured)) return Path.GetFullPath(configured);
        }
    } catch { }
    var comfyRoot = Path.GetDirectoryName(settings.ActiveProfile.MainPyPath) ?? settings.ActiveProfile.WorkingDirectory;
    return Path.GetFullPath(Path.Combine(comfyRoot, "user", "default", "workflows"));
}

async Task<LocalWorkflowScanResult> ScanLocalWorkflows(IHttpClientFactory factory, string directory) {
    var result = new LocalWorkflowScanResult();
    JsonObject? cachedObjectInfo = null;
    async Task<JsonObject> GetObjectInfo() {
        if (cachedObjectInfo != null) return cachedObjectInfo;
        using var infoResponse = await factory.CreateClient("comfy").GetAsync("object_info");
        if (!infoResponse.IsSuccessStatusCode)
            throw new InvalidOperationException($"读取 ComfyUI 节点定义失败（HTTP {(int)infoResponse.StatusCode}）");
        cachedObjectInfo = JsonNode.Parse(await infoResponse.Content.ReadAsStringAsync()) as JsonObject
            ?? throw new InvalidOperationException("ComfyUI 节点定义不是有效 JSON");
        return cachedObjectInfo;
    }
    var bundledDirectory = Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI", "BuiltInWorkflows");
    var files = Directory.EnumerateFiles(directory, "*.json", SearchOption.AllDirectories)
        .Select(path => (Path: path, Root: directory, Prefix: ""))
        .Concat(Directory.Exists(bundledDirectory)
            ? Directory.EnumerateFiles(bundledDirectory, "*.json", SearchOption.AllDirectories).Select(path => (Path: path, Root: bundledDirectory, Prefix: "内置/"))
            : Enumerable.Empty<(string Path, string Root, string Prefix)>())
        .OrderBy(item => item.Prefix, StringComparer.OrdinalIgnoreCase)
        .ThenBy(item => item.Path, StringComparer.OrdinalIgnoreCase)
        .Take(200).ToArray();
    foreach (var item in files) {
        var path = item.Path;
        var relative = item.Prefix + NormalizeRelativePath(Path.GetRelativePath(item.Root, path));
        try {
            var info = new FileInfo(path);
            if (info.Length > 5 * 1024 * 1024) throw new InvalidOperationException("文件超过 5 MB");
            var raw = await File.ReadAllTextAsync(path);
            var source = JsonNode.Parse(raw) as JsonObject ?? throw new InvalidOperationException("JSON 根节点不是对象");
            var prompt = IsApiWorkflow(source) ? source : await ConvertLocalWorkflow(factory, raw, GetObjectInfo);
            foreach (var node in prompt.Select(entry => entry.Value).OfType<JsonObject>().Where(node => node["class_type"]?.GetValue<string>() == "CheckpointLoaderSimple")) {
                if (node["inputs"]?["ckpt_name"]?.GetValue<string>() != "__AIPROVIDER_FIRST_CHECKPOINT__") continue;
                var choices = (await GetObjectInfo())["CheckpointLoaderSimple"]?["input"]?["required"]?["ckpt_name"]?[0] as JsonArray;
                var checkpointName = choices?.FirstOrDefault()?.GetValue<string>();
                if (string.IsNullOrWhiteSpace(checkpointName)) throw new InvalidOperationException("ComfyUI 没有可用的 checkpoint 模型");
                node["inputs"]!["ckpt_name"] = checkpointName;
            }
            result.Workflows.Add(BuildLocalWorkflow(relative, info.LastWriteTimeUtc, prompt));
        } catch (Exception ex) {
            result.Rejected.Add(new LocalWorkflowRejection { Path = relative, Message = ex.Message });
        }
    }
    return result;
}

bool IsApiWorkflow(JsonObject workflow) => workflow.Count > 0 &&
    !workflow.ContainsKey("nodes") && !workflow.ContainsKey("links") &&
    workflow.All(entry => entry.Value is JsonObject node && node["class_type"] != null && node["inputs"] is JsonObject);

async Task<JsonObject> ConvertLocalWorkflow(IHttpClientFactory factory, string raw, Func<Task<JsonObject>> getObjectInfo) {
    using var content = new StringContent(raw, System.Text.Encoding.UTF8, "application/json");
    using var response = await factory.CreateClient("comfy").PostAsync("workflow/convert", content);
    var converted = await response.Content.ReadAsStringAsync();
    JsonObject prompt;
    if (response.IsSuccessStatusCode) {
        prompt = JsonNode.Parse(converted) as JsonObject ?? throw new InvalidOperationException("转换器返回了无效 JSON");
    } else if (response.StatusCode is HttpStatusCode.NotFound or HttpStatusCode.MethodNotAllowed) {
        var uiWorkflow = JsonNode.Parse(raw) as JsonObject ?? throw new InvalidOperationException("界面工作流不是有效 JSON 对象");
        prompt = WorkflowConverter.Convert(uiWorkflow, await getObjectInfo());
    } else {
        throw new InvalidOperationException(response.StatusCode == HttpStatusCode.NotFound
            ? "ComfyUI 未安装工作流转换器"
            : $"转换失败（HTTP {(int)response.StatusCode}）");
    }
    var source = JsonNode.Parse(raw) as JsonObject;
    if (source?["nodes"] is JsonArray sourceNodes) {
        foreach (var entry in prompt.Where(item => item.Value?["class_type"]?.GetValue<string>() == "Prompt (LoraManager)")) {
            if (entry.Value?["inputs"] is not JsonObject inputs) continue;
            var sourceNode = sourceNodes.OfType<JsonObject>().FirstOrDefault(node => node["id"]?.ToString() == entry.Key);
            string? widgetText = sourceNode?["widgets_values"] switch {
                JsonValue value when value.TryGetValue<string>(out var text) => text,
                JsonArray values => values.OfType<JsonValue>().Select(value => value.TryGetValue<string>(out var text) ? text : null).FirstOrDefault(text => text != null),
                _ => null
            };
            if (!inputs.ContainsKey("text")) inputs["text"] = widgetText ?? "";
            if (inputs["seed"] is JsonValue seedValue && seedValue.TryGetValue<string>(out var seedText) && !long.TryParse(seedText, out _))
                inputs.Remove("seed");
        }

        var legacyPointEditors = sourceNodes.OfType<JsonObject>()
            .Where(node => node["type"]?.GetValue<string>() == "PointsMaskEditor")
            .ToArray();
        if (legacyPointEditors.Length > 0) {
            using var infoResponse = await factory.CreateClient("comfy").GetAsync("object_info/MaskEditMEC");
            var info = JsonNode.Parse(await infoResponse.Content.ReadAsStringAsync());
            var required = info?["MaskEditMEC"]?["input"]?["required"] as JsonObject
                ?? throw new InvalidOperationException("当前节点包缺少 MaskEditMEC，无法兼容旧 PointsMaskEditor");
            foreach (var sourceNode in legacyPointEditors) {
                var id = sourceNode["id"]?.ToString();
                if (id == null || prompt[id] is not JsonObject apiNode || apiNode["inputs"] is not JsonObject inputs) continue;
                foreach (var field in required) {
                    if (inputs.ContainsKey(field.Key) || field.Value is not JsonArray spec) continue;
                    JsonNode? defaultValue = spec.Count > 1 ? spec[1]?["default"] : null;
                    if (defaultValue == null && spec[0] is JsonArray choices && choices.Count > 0) defaultValue = choices[0];
                    if (defaultValue != null) inputs[field.Key] = defaultValue.DeepClone();
                }
                var widgets = sourceNode["widgets_values"] as JsonArray;
                inputs["mode"] = "points_bbox";
                if (widgets is { Count: > 0 }) inputs["width"] = widgets[0]?.DeepClone();
                if (widgets is { Count: > 1 }) inputs["height"] = widgets[1]?.DeepClone();
                if (widgets is { Count: > 2 }) inputs["editor_data"] = widgets[2]?.DeepClone();
                if (widgets is { Count: > 3 }) inputs["default_radius"] = widgets[3]?.DeepClone();
                if (widgets is { Count: > 4 }) inputs["softness"] = widgets[4]?.DeepClone();
                if (widgets is { Count: > 5 }) inputs["normalize"] = widgets[5]?.DeepClone();
                apiNode["class_type"] = "MaskEditMEC";
            }
        }

        var legacySamGenerators = sourceNodes.OfType<JsonObject>()
            .Where(node => node["type"]?.GetValue<string>() == "SAMMaskGeneratorMEC" &&
                           node["widgets_values"] is JsonArray { Count: <= 8 })
            .ToArray();
        if (legacySamGenerators.Length > 0) {
            using var infoResponse = await factory.CreateClient("comfy").GetAsync("object_info/SAMMaskGeneratorMEC");
            var info = JsonNode.Parse(await infoResponse.Content.ReadAsStringAsync());
            var required = info?["SAMMaskGeneratorMEC"]?["input"]?["required"] as JsonObject
                ?? throw new InvalidOperationException("当前节点包缺少 SAMMaskGeneratorMEC，无法兼容旧版参数");
            foreach (var sourceNode in legacySamGenerators) {
                var id = sourceNode["id"]?.ToString();
                if (id == null || prompt[id]?["inputs"] is not JsonObject inputs ||
                    sourceNode["widgets_values"] is not JsonArray widgets) continue;
                foreach (var fieldName in new[] { "text_prompt", "negative_text_prompt", "grounding_model", "text_threshold", "text_box_threshold" }) {
                    if (required[fieldName] is not JsonArray spec) continue;
                    JsonNode? defaultValue = spec.Count > 1 ? spec[1]?["default"] : null;
                    if (defaultValue == null && spec[0] is JsonArray choices && choices.Count > 0) defaultValue = choices[0];
                    if (defaultValue != null) inputs[fieldName] = defaultValue.DeepClone();
                }
                if (widgets.Count > 2) inputs["multimask_output"] = widgets[2]?.DeepClone();
                if (widgets.Count > 3) inputs["mask_index"] = widgets[3]?.DeepClone();
                if (widgets.Count > 4) inputs["score_threshold"] = widgets[4]?.DeepClone();
                if (widgets.Count > 5) inputs["apply_bbox_crop"] = widgets[5]?.DeepClone();
                if (widgets.Count > 6) inputs["refine_iterations"] = widgets[6]?.DeepClone();
                if (widgets.Count > 7) inputs["auto_negative_points"] = widgets[7]?.DeepClone();
            }
        }
    }
    foreach (var entry in prompt.Where(item => item.Value?["class_type"]?.GetValue<string>() == "MaskPreviewOverlay").ToArray()) {
        if (entry.Value is not JsonObject node || node["inputs"]?["mask"] is not JsonNode mask) continue;
        node["class_type"] = "MaskToImage";
        node["inputs"] = new JsonObject { ["mask"] = mask.DeepClone() };
    }
    return prompt;
}

JsonObject BuildLocalWorkflow(string relative, DateTime modifiedAtUtc, JsonObject prompt) {
    var fields = new JsonObject();
    var defaults = new JsonObject { ["randomSeed"] = true };
    var exposed = new JsonArray();
    var entries = prompt.Where(entry => entry.Value is JsonObject).ToDictionary(entry => entry.Key, entry => (JsonObject)entry.Value!);
    string Title(string id, JsonObject node) => node["_meta"]?["title"]?.GetValue<string>() ?? $"{node["class_type"]?.GetValue<string>() ?? "节点"} {id}";
    void Bind(string key, KeyValuePair<string, JsonObject>? entry, string input, string? label = null) {
        if (entry == null || entry.Value.Value["inputs"] is not JsonObject inputs || !inputs.ContainsKey(input)) return;
        fields[key] = new JsonObject {
            ["nodeId"] = entry.Value.Key,
            ["nodeTitle"] = Title(entry.Value.Key, entry.Value.Value),
            ["nodeType"] = entry.Value.Value["class_type"]?.GetValue<string>(),
            ["input"] = input,
            ["label"] = label
        };
        defaults[key] = inputs[input]?.DeepClone();
        exposed.Add(key);
    }
    KeyValuePair<string, JsonObject>? Find(Func<JsonObject, bool> predicate) {
        foreach (var entry in entries) if (predicate(entry.Value)) return entry;
        return null;
    }
    bool TitleHas(JsonObject node, params string[] values) {
        var title = node["_meta"]?["title"]?.GetValue<string>() ?? "";
        return values.Any(value => title.Contains(value, StringComparison.OrdinalIgnoreCase));
    }
    string? TextInput(JsonObject node) {
        if (node["inputs"] is not JsonObject inputs) return null;
        foreach (var preferred in new[] { "text", "prompt" })
            if (inputs[preferred] is JsonValue preferredValue && preferredValue.TryGetValue<string>(out _)) return preferred;
        foreach (var input in inputs)
            if (input.Value is JsonValue value && value.TryGetValue<string>(out _)) return input.Key;
        return null;
    }

    var positive = Find(node => TitleHas(node, "正向", "positive"));
    var negative = Find(node => TitleHas(node, "负向", "negative"));
    var positiveInput = positive == null ? null : TextInput(positive.Value.Value);
    var negativeInput = negative == null ? null : TextInput(negative.Value.Value);
    if (positiveInput != null) Bind("positivePrompt", positive, positiveInput);
    if (negativeInput != null) Bind("negativePrompt", negative, negativeInput);

    var latent = Find(node => node["class_type"]?.GetValue<string>() is string type &&
        (type.Equals("EmptyLatentImage", StringComparison.OrdinalIgnoreCase) ||
         type.Equals("EmptySD3LatentImage", StringComparison.OrdinalIgnoreCase)) &&
        node["inputs"]?["width"] != null && node["inputs"]?["height"] != null);
    Bind("width", latent, "width");
    Bind("height", latent, "height");
    if (latent?.Value["inputs"]?["batch_size"] != null) Bind("batchSize", latent, "batch_size");

    var sampler = Find(node => node["class_type"]?.GetValue<string>() is string type &&
        (type.Equals("KSampler", StringComparison.OrdinalIgnoreCase) || type.Equals("KSamplerAdvanced", StringComparison.OrdinalIgnoreCase)));
    var randomNoise = Find(node => node["class_type"]?.GetValue<string>()?.Equals("RandomNoise", StringComparison.OrdinalIgnoreCase) == true &&
        node["inputs"]?["noise_seed"] != null);
    var seedNode = sampler ?? randomNoise;
    Bind("seed", seedNode, sampler != null && sampler.Value.Value["inputs"]?["seed"] != null ? "seed" : "noise_seed");
    Bind("steps", sampler, "steps");
    Bind("cfg", sampler, "cfg");
    Bind("sampler", sampler, "sampler_name");
    Bind("scheduler", sampler, "scheduler");
    if (sampler?.Value["inputs"]?["denoise"] != null) Bind("denoise", sampler, "denoise");

    var checkpoint = Find(node => node["class_type"]?.GetValue<string>() is string type &&
        type.Contains("Checkpoint", StringComparison.OrdinalIgnoreCase) && node["inputs"]?["ckpt_name"] != null);
    var diffusionModel = Find(node => node["class_type"]?.GetValue<string>() is string type &&
        type.Contains("UNET", StringComparison.OrdinalIgnoreCase) && node["inputs"]?["unet_name"] != null);
    var primaryModel = checkpoint ?? diffusionModel;
    Bind("checkpoint", primaryModel, checkpoint != null ? "ckpt_name" : "unet_name", "主模型");
    var loraText = Find(node => TitleHas(node, "LoRA") && node["inputs"]?["text"] is JsonValue);
    var loraNode = Find(node => node["class_type"]?.GetValue<string>()?.Contains("lora", StringComparison.OrdinalIgnoreCase) == true || TitleHas(node, "LoRA"));
    if (loraText != null) {
        Bind("loras", loraText, "text");
        defaults["loras"] = new JsonArray();
    } else if (loraNode != null && checkpoint != null) {
        fields["loras"] = new JsonObject {
            ["nodeId"] = loraNode.Value.Key,
            ["nodeTitle"] = Title(loraNode.Value.Key, loraNode.Value.Value),
            ["nodeType"] = "DynamicLoraChain",
            ["input"] = "loras",
            ["label"] = "动态 LoRA"
        };
        defaults["loras"] = new JsonArray();
        exposed.Add("loras");
    }
    var sourceImage = Find(node => node["class_type"]?.GetValue<string>() == "LoadImage" && node["inputs"]?["image"] != null);
    if (sourceImage != null) Bind("sourceImage", sourceImage, "image");
    var cutoutJoin = Find(node => node["class_type"]?.GetValue<string>()?.Contains("JoinImageWithAlpha", StringComparison.OrdinalIgnoreCase) == true);
    var supportsCutoutTarget = false;
    if (cutoutJoin is { } join && join.Value["inputs"]?["alpha"] is JsonArray personAlpha && personAlpha.Count >= 2 &&
        personAlpha[0] is JsonValue invertIdValue && invertIdValue.TryGetValue<string>(out var invertId) &&
        entries.TryGetValue(invertId, out var invertNode) &&
        invertNode["class_type"]?.GetValue<string>()?.Equals("InvertMask", StringComparison.OrdinalIgnoreCase) == true &&
        invertNode["inputs"]?["mask"] is JsonArray backgroundAlpha && backgroundAlpha.Count >= 2 &&
        backgroundAlpha[0] is JsonValue removalIdValue && removalIdValue.TryGetValue<string>(out var removalId) &&
        entries.TryGetValue(removalId, out var removalNode) &&
        removalNode["class_type"]?.GetValue<string>()?.Contains("RemoveBackground", StringComparison.OrdinalIgnoreCase) == true) {
        fields["cutoutTarget"] = new JsonObject {
            ["nodeId"] = join.Key,
            ["nodeTitle"] = Title(join.Key, join.Value),
            ["nodeType"] = "CutoutTarget",
            ["input"] = "alpha",
            ["label"] = "抠图目标",
            ["personAlpha"] = personAlpha.DeepClone(),
            ["backgroundAlpha"] = backgroundAlpha.DeepClone()
        };
        defaults["cutoutTarget"] = "person";
        exposed.Add("cutoutTarget");
        supportsCutoutTarget = true;
    }
    var secondPass = Find(node => node["class_type"]?.GetValue<string>()?.Contains("UltimateSDUpscale", StringComparison.OrdinalIgnoreCase) == true);
    KeyValuePair<string, JsonObject>? exactOutputScale = null;
    if (secondPass != null) {
        var tileNodeId = secondPass.Value.Key;
        exactOutputScale = Find(node =>
            node["class_type"]?.GetValue<string>()?.Equals("ImageScale", StringComparison.OrdinalIgnoreCase) == true &&
            node["inputs"]?["width"] != null && node["inputs"]?["height"] != null &&
            node["inputs"]?["image"] is JsonArray imageLink && imageLink.Count > 0 &&
            imageLink[0] is JsonValue sourceValue && sourceValue.TryGetValue<string>(out var sourceId) && sourceId == tileNodeId);
        if (exactOutputScale?.Value["inputs"] is JsonObject exactInputs) {
            defaults["width"] = exactInputs["width"]?.DeepClone();
            defaults["height"] = exactInputs["height"]?.DeepClone();
        }
    }
    if (secondPass != null) {
        Bind("secondPassSteps", secondPass, "steps");
        Bind("secondPassDenoise", secondPass, "denoise");
        if (secondPass.Value.Value["inputs"]?["seed"] != null) Bind("secondPassSeed", secondPass, "seed");
    }

    var output = Find(node => node["class_type"]?.GetValue<string>() == "SaveImage" && TitleHas(node, "最终", "final"))
        ?? Find(node => node["class_type"]?.GetValue<string>() == "SaveImage");
    var transparentOutput = Find(node => node["class_type"]?.GetValue<string>() == "SaveImage" && TitleHas(node, "透明", "transparent", "抠图"));
    Bind("filenamePrefix", output, "filename_prefix");

    // Preserve every remaining editable scalar from the converted API workflow.
    // Semantic fields above keep stable names; automatic fields use stable node/input keys.
    var boundInputs = fields.Select(entry => entry.Value).OfType<JsonObject>()
        .Select(spec => $"{spec["nodeId"]}::{spec["input"]}")
        .ToHashSet(StringComparer.Ordinal);
    string SafeKeyPart(string value) => new(value.Select(ch => char.IsLetterOrDigit(ch) ? ch : '_').ToArray());
    foreach (var entry in entries.OrderBy(item => item.Key, StringComparer.Ordinal)) {
        if (entry.Value["inputs"] is not JsonObject inputs) continue;
        foreach (var input in inputs) {
            if (input.Value is not JsonValue) continue;
            if (boundInputs.Contains($"{entry.Key}::{input.Key}")) continue;
            if (secondPass != null && entry.Key == secondPass.Value.Key && input.Key == "upscale_by") continue;
            if (exactOutputScale != null && entry.Key == exactOutputScale.Value.Key && (input.Key == "width" || input.Key == "height")) continue;
            var nodeType = entry.Value["class_type"]?.GetValue<string>() ?? "Node";
            var key = $"node_{SafeKeyPart(entry.Key)}_{SafeKeyPart(input.Key)}";
            Bind(key, entry, input.Key, $"{nodeType} {entry.Key} · {input.Key}");
            boundInputs.Add($"{entry.Key}::{input.Key}");
        }
    }

    var isTextGeneration = latent != null || sampler != null;
    var usesZeroNegative = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Equals("ConditioningZeroOut", StringComparison.OrdinalIgnoreCase) == true);
    // Workflow discovery is deliberately permissive: a valid ComfyUI graph may omit
    // semantic fields (for example ConditioningZeroOut has no negative text input)
    // and should still be listed. Bind only the fields that can be identified.
    var primaryOutput = output;

    var id = "local-" + Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(relative.ToLowerInvariant())))[..16].ToLowerInvariant();
    defaults["workflowId"] = id;
    var hasControlNet = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("ControlNet", StringComparison.OrdinalIgnoreCase) == true);
    var needsSourceImage = sourceImage != null;
    var hasInpaintPipeline = needsSourceImage && entries.Values.Any(node => {
        var classType = node["class_type"]?.GetValue<string>() ?? "";
        return classType.Contains("VAEEncodeForInpaint", StringComparison.OrdinalIgnoreCase)
            || classType.Contains("InpaintModelConditioning", StringComparison.OrdinalIgnoreCase)
            || classType.Contains("SetLatentNoiseMask", StringComparison.OrdinalIgnoreCase);
    });
    var hasBackgroundModel = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("BackgroundRemovalModel", StringComparison.OrdinalIgnoreCase) == true);
    var hasBackgroundRemoval = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("RemoveBackground", StringComparison.OrdinalIgnoreCase) == true);
    var hasAlphaJoin = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("JoinImageWithAlpha", StringComparison.OrdinalIgnoreCase) == true);
    var autoCutout = hasBackgroundModel && hasBackgroundRemoval && hasAlphaJoin && transparentOutput != null;
    var transparentOutputId = transparentOutput?.Key;
    var generationAndCutout = primaryOutput != null && isTextGeneration && autoCutout && transparentOutputId != primaryOutput.Value.Key;
    defaults["generateTransparent"] = generationAndCutout;
    return new JsonObject {
        ["id"] = id,
        ["name"] = Path.ChangeExtension(relative, null)?.Replace("/", " › "),
        ["relativePath"] = relative,
        ["modifiedAt"] = new DateTimeOffset(modifiedAtUtc, TimeSpan.Zero),
        ["definition"] = prompt.DeepClone(),
        ["binding"] = new JsonObject {
            ["fields"] = fields,
            ["outputNode"] = primaryOutput == null ? null : new JsonObject { ["nodeId"] = primaryOutput.Value.Key, ["title"] = Title(primaryOutput.Value.Key, primaryOutput.Value.Value) },
            ["optionalOutputs"] = generationAndCutout ? new JsonObject { ["transparent"] = transparentOutputId } : null,
            ["capabilities"] = new JsonObject { ["controlNet"] = hasControlNet, ["styleReference"] = false, ["poseReference"] = false, ["inputImage"] = needsSourceImage, ["inpaint"] = hasInpaintPipeline, ["outpaint"] = hasInpaintPipeline, ["autoCutout"] = autoCutout, ["generationAndCutout"] = generationAndCutout, ["cutoutTarget"] = supportsCutoutTarget }
        },
        ["defaults"] = defaults,
        ["fields"] = exposed,
        ["models"] = defaults["checkpoint"] is JsonNode primaryModelName ? new JsonArray(primaryModelName.DeepClone()) : new JsonArray(),
        ["capabilities"] = new JsonObject { ["controlNet"] = hasControlNet, ["styleReference"] = false, ["poseReference"] = false, ["inputImage"] = needsSourceImage, ["inpaint"] = hasInpaintPipeline, ["outpaint"] = hasInpaintPipeline, ["autoCutout"] = autoCutout, ["generationAndCutout"] = generationAndCutout, ["cutoutTarget"] = supportsCutoutTarget }
    };
}

string GalleryRoot() => Path.GetFullPath(settings.ActiveProfile.OutputDirectory);
string MigrationSettingsPath() => Path.Combine(AppContext.BaseDirectory, "bridge-user-settings.json");
BridgeUserSettings ReadBridgeUserSettings() {
    if (!File.Exists(MigrationSettingsPath())) return new BridgeUserSettings();
    return JsonSerializer.Deserialize<BridgeUserSettings>(File.ReadAllText(MigrationSettingsPath()), GalleryJsonOptions())
        ?? throw new InvalidOperationException("Bridge 用户配置文件内容无效");
}
async Task<BridgeUserSettings> UpdateBridgeUserSettings(Action<BridgeUserSettings> update) {
    await bridgeUserSettingsLock.WaitAsync();
    try {
        var userSettings = ReadBridgeUserSettings();
        update(userSettings);
        var path = MigrationSettingsPath();
        var temporaryPath = path + ".tmp";
        await File.WriteAllTextAsync(temporaryPath, JsonSerializer.Serialize(userSettings, GalleryJsonOptions(writeIndented: true)));
        File.Move(temporaryPath, path, overwrite: true);
        return userSettings;
    } finally {
        bridgeUserSettingsLock.Release();
    }
}
string GetMigrationDirectory() {
    var saved = ReadBridgeUserSettings().Directory;
    if (!string.IsNullOrWhiteSpace(saved) && Path.IsPathFullyQualified(saved)) return Path.GetFullPath(saved);
    return Path.GetFullPath(settings.MigrationDirectory);
}
string GetMaidAiDirectory() {
    var saved = ReadBridgeUserSettings().MaidAiDirectory;
    if (!string.IsNullOrWhiteSpace(saved) && Path.IsPathFullyQualified(saved)) return Path.GetFullPath(saved);
    return Path.GetFullPath(settings.MaidAiDirectory);
}
string NormalizeRelativePath(string? path) => (path ?? "").Replace('\\', '/').TrimStart('/');
bool IsGalleryImage(string path) => new[] { ".png", ".jpg", ".jpeg", ".webp" }.Contains(Path.GetExtension(path), StringComparer.OrdinalIgnoreCase);
StringComparer PathComparer() => OperatingSystem.IsWindows() ? StringComparer.OrdinalIgnoreCase : StringComparer.Ordinal;

ImageMigrationPlan PlanImageMigrations(IEnumerable<string> sourcePaths, string destination) {
    var comparer = PathComparer();
    var comparison = OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;
    var sources = sourcePaths.Select(Path.GetFullPath).Distinct(comparer).ToArray();
    var sourceSet = sources.ToHashSet(comparer);
    var existingHashes = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    foreach (var existing in Directory.EnumerateFiles(destination, "*", SearchOption.AllDirectories).Where(IsGalleryImage)) {
        var fullPath = Path.GetFullPath(existing);
        if (sourceSet.Contains(fullPath)) continue;
        var hash = FileSha256(fullPath);
        existingHashes.TryAdd(hash, fullPath);
    }

    var selectedHashes = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    var reservedTargets = new HashSet<string>(comparer);
    var files = new List<ImageMigrationFile>();
    foreach (var source in sources) {
        var sourceHash = FileSha256(source);
        if (!selectedHashes.Add(sourceHash))
            return new ImageMigrationPlan(files, $"选中的图片中存在内容重复项：{Path.GetFileName(source)}");
        if (existingHashes.TryGetValue(sourceHash, out var duplicate))
            return new ImageMigrationPlan(files, $"{Path.GetFileName(source)} 与迁移目录中的 {Path.GetFileName(duplicate)} 内容相同，不能重复迁移");

        var target = Path.Combine(destination, Path.GetFileName(source));
        if (string.Equals(source, target, comparison))
            return new ImageMigrationPlan(files, $"{Path.GetFileName(source)} 已经在目标文件夹");
        if (File.Exists(target) || reservedTargets.Contains(target)) {
            var extension = Path.GetExtension(source);
            var stem = Path.GetFileNameWithoutExtension(source);
            target = Path.Combine(destination, $"{stem}_{sourceHash[..12].ToLowerInvariant()}{extension}");
            if (File.Exists(target) || reservedTargets.Contains(target)) {
                target = Path.Combine(destination, $"{stem}_{sourceHash.ToLowerInvariant()}{extension}");
                if (File.Exists(target) || reservedTargets.Contains(target))
                    return new ImageMigrationPlan(files, $"无法为 {Path.GetFileName(source)} 创建唯一的 hash 文件名");
            }
        }
        reservedTargets.Add(target);
        files.Add(new ImageMigrationFile(source, target, sourceHash.ToLowerInvariant()));
    }
    return new ImageMigrationPlan(files, null);
}

string FileSha256(string path) {
    using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
    return Convert.ToHexString(SHA256.HashData(stream));
}

string ResolveGalleryPath(string relativePath, bool mustExist) {
    var root = GalleryRoot();
    var rootPrefix = root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
    var fullPath = Path.GetFullPath(Path.Combine(root, NormalizeRelativePath(relativePath).Replace('/', Path.DirectorySeparatorChar)));
    var comparison = OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;
    if (!fullPath.StartsWith(rootPrefix, comparison) && !string.Equals(fullPath, root, comparison))
        throw new InvalidOperationException("图片路径不合法");
    if (mustExist && !File.Exists(fullPath) && !Directory.Exists(fullPath))
        throw new FileNotFoundException("本机图片不存在", relativePath);
    return fullPath;
}

string ResolveAssetPath(string path, bool mustExist = true) {
    var root = Path.GetFullPath(GetMigrationDirectory());
    var prefix = root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
    var raw = (path ?? "").Trim();
    var absolute = Path.IsPathFullyQualified(raw);
    var full = absolute
        ? Path.GetFullPath(raw)
        : Path.GetFullPath(Path.Combine(root, NormalizeRelativePath(raw).Replace('/', Path.DirectorySeparatorChar)));
    var comparison = OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;
    if ((!absolute && !full.StartsWith(prefix, comparison) && !string.Equals(full, root, comparison)) || !IsGalleryImage(full) || (mustExist && !File.Exists(full)))
        throw new FileNotFoundException("迁移资产不存在", path);
    return full;
}

JsonSerializerOptions GalleryJsonOptions(bool writeIndented = false) => new() {
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    PropertyNameCaseInsensitive = true,
    WriteIndented = writeIndented
};

GalleryImage ToGalleryImage(GalleryFile file) {
    var (width, height) = ReadImageDimensions(file.FullPath);
    return new GalleryImage { Path = file.Path, FullPath = file.FullPath, Filename = file.Filename, SizeBytes = new FileInfo(file.FullPath).Length, Width = width, Height = height };
}

void OpenFileInFolder(string file) {
    if (OperatingSystem.IsMacOS())
        Process.Start(new ProcessStartInfo("open", $"-R \"{file}\"") { UseShellExecute = true });
    else {
        Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{file}\"") { UseShellExecute = true });
        ExplorerWindowActivator.Activate(Path.GetDirectoryName(file) ?? file);
    }
}

(int? Width, int? Height) ReadImageDimensions(string path) {
    try {
        using var stream = File.OpenRead(path);
        Span<byte> header = stackalloc byte[24];
        if (stream.Read(header) == header.Length && header[..8].SequenceEqual(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }))
            return (BinaryPrimitives.ReadInt32BigEndian(header[16..20]), BinaryPrimitives.ReadInt32BigEndian(header[20..24]));
    } catch { }
    return (null, null);
}

async Task<bool> IsComfyRunning(IHttpClientFactory factory) {
    try { using var response = await factory.CreateClient("comfy").GetAsync("system_stats"); return response.IsSuccessStatusCode; }
    catch { return false; }
}

async Task<Process?> FindComfyListenerProcess() {
    var uri = new Uri(settings.ComfyUiBaseUrl);
    var listenerCommand = OperatingSystem.IsMacOS()
        ? new ProcessStartInfo { FileName = "/usr/sbin/lsof", Arguments = $"-nP -iTCP:{uri.Port} -sTCP:LISTEN -t" }
        : new ProcessStartInfo { FileName = "powershell.exe", Arguments = $"-NoProfile -NonInteractive -Command \"Get-NetTCPConnection -State Listen -LocalPort {uri.Port} -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess\"" };
    listenerCommand.UseShellExecute = false;
    listenerCommand.CreateNoWindow = true;
    listenerCommand.RedirectStandardOutput = true;
    listenerCommand.RedirectStandardError = true;
    using var listener = Process.Start(listenerCommand);
    if (listener == null) return null;
    var output = await listener.StandardOutput.ReadToEndAsync();
    await listener.WaitForExitAsync();
    var firstProcessId = output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault();
    if (listener.ExitCode != 0 || !int.TryParse(firstProcessId, out var processId) || processId == Environment.ProcessId)
        return null;
    try {
        var candidate = Process.GetProcessById(processId);
        ProcessStartInfo commandQuery;
        if (OperatingSystem.IsMacOS()) {
            commandQuery = new ProcessStartInfo {
                FileName = "/bin/ps",
                Arguments = $"-p {processId} -o command="
            };
        } else {
            commandQuery = new ProcessStartInfo {
                FileName = "powershell.exe",
                Arguments = $"-NoProfile -NonInteractive -Command \"(Get-CimInstance Win32_Process -Filter 'ProcessId={processId}').CommandLine\""
            };
        }
        commandQuery.UseShellExecute = false;
        commandQuery.CreateNoWindow = true;
        commandQuery.RedirectStandardOutput = true;
        commandQuery.RedirectStandardError = true;
        using var query = Process.Start(commandQuery);
        if (query == null) { candidate.Dispose(); return null; }
        var commandLine = await query.StandardOutput.ReadToEndAsync();
        await query.WaitForExitAsync();
        var profile = settings.ActiveProfile;
        var relativeMainPath = Path.GetRelativePath(profile.WorkingDirectory, profile.MainPyPath);
        if ((!OperatingSystem.IsMacOS() && !string.Equals(candidate.MainModule?.FileName, profile.PythonPath, StringComparison.OrdinalIgnoreCase)) ||
            (!commandLine.Contains(profile.MainPyPath, StringComparison.OrdinalIgnoreCase) &&
             !commandLine.Contains(relativeMainPath, StringComparison.OrdinalIgnoreCase))) {
            candidate.Dispose(); return null;
        }
        return candidate;
    }
    catch (ArgumentException) { return null; }
}

void ApplyDynamicLoras(JsonObject prompt, string raw) {
    var selected = ReadSelectedLoras(raw);
    if (selected.Count == 0) return;
    var checkpoints = prompt.Where(entry => entry.Value?["class_type"]?.GetValue<string>() == "CheckpointLoaderSimple").ToArray();
    if (checkpoints.Length != 1)
        throw new InvalidOperationException(checkpoints.Length == 0
            ? "当前工作流没有可自动连接的 CheckpointLoaderSimple，请先在工作流中预留 LoRA 节点"
            : "当前工作流包含多个基础模型分支，无法安全自动连接 LoRA，请在工作流中预留 LoRA 节点");
    var checkpointId = checkpoints[0].Key;
    var originalNodes = prompt.Where(entry => entry.Value is JsonObject).ToArray();
    var nextNumericId = prompt.Select(entry => int.TryParse(entry.Key, out var value) ? value : 0).DefaultIfEmpty().Max();
    var previousId = checkpointId;
    foreach (var item in selected) {
        var id = (++nextNumericId).ToString();
        var modelStrength = Math.Clamp(item.ModelStrength, -10d, 10d);
        var clipStrength = Math.Clamp(item.ClipStrength, -10d, 10d);
        prompt[id] = new JsonObject {
            ["inputs"] = new JsonObject {
                ["lora_name"] = item.Name, ["strength_model"] = modelStrength, ["strength_clip"] = clipStrength,
                ["model"] = new JsonArray(previousId, 0), ["clip"] = new JsonArray(previousId, 1)
            },
            ["class_type"] = "LoraLoader", ["_meta"] = new JsonObject { ["title"] = $"AIProvider 动态 LoRA {id}" }
        };
        previousId = id;
    }
    foreach (var entry in originalNodes) {
        if (entry.Value?["inputs"] is not JsonObject inputs) continue;
        foreach (var input in inputs.ToArray()) {
            if (input.Value is not JsonArray connection || connection.Count < 2) continue;
            var sourceId = connection[0]?.GetValue<string>();
            if (sourceId != checkpointId) continue;
            var outputIndex = connection[1]?.GetValue<int>() ?? -1;
            if (outputIndex is 0 or 1) inputs[input.Key] = new JsonArray(previousId, outputIndex);
        }
    }
}

List<GenerationLora> ReadSelectedLoras(string raw) {
    if (string.IsNullOrWhiteSpace(raw) || !raw.TrimStart().StartsWith('[')) return new List<GenerationLora>();
    var requested = JsonNode.Parse(raw) as JsonArray ?? throw new InvalidOperationException("LoRA 参数不是有效数组");
    return requested.OfType<JsonObject>()
        .Where(item => item["enabled"]?.GetValue<bool>() != false && !string.IsNullOrWhiteSpace(item["name"]?.GetValue<string>()))
        .Select(item => new GenerationLora {
            Name = item["name"]!.GetValue<string>(),
            ModelStrength = item["modelStrength"]?.GetValue<double>() ?? 1d,
            ClipStrength = item["clipStrength"]?.GetValue<double>() ?? 1d
        }).ToList();
}

string CompactLoraDisplayName(string modelName) {
    var file = modelName.Replace('\\', '/').Split('/').Last();
    var name = Path.GetFileNameWithoutExtension(file).Trim();
    return string.IsNullOrWhiteSpace(name) ? "未命名 LoRA" : name;
}

string ResolveMaidAiCopyTarget(string source, string sourceHash, string destination, out bool alreadyExists) {
    var comparison = OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal;
    var extension = Path.GetExtension(source);
    var stem = Path.GetFileNameWithoutExtension(source);
    var candidates = new[] {
        Path.Combine(destination, Path.GetFileName(source)),
        Path.Combine(destination, $"{stem}_{sourceHash[..12].ToLowerInvariant()}{extension}"),
        Path.Combine(destination, $"{stem}_{sourceHash.ToLowerInvariant()}{extension}")
    };
    foreach (var candidate in candidates) {
        if (string.Equals(source, candidate, comparison)) {
            alreadyExists = true;
            return candidate;
        }
        if (!File.Exists(candidate)) {
            alreadyExists = false;
            return candidate;
        }
        if (string.Equals(FileSha256(candidate), sourceHash, StringComparison.OrdinalIgnoreCase)) {
            alreadyExists = true;
            return candidate;
        }
    }
    throw new IOException($"女仆AI 目录中无法为 {Path.GetFileName(source)} 创建唯一文件名");
}

string CompactModelDisplayName(string modelName) {
    var normalized = modelName.Replace('\\', '/');
    var file = normalized.Split('/').Last();
    var name = Path.GetFileNameWithoutExtension(file).Trim();
    var directory = string.Join(" / ", normalized.Split('/').SkipLast(1));
    if (string.IsNullOrWhiteSpace(name)) return modelName;
    return string.IsNullOrWhiteSpace(directory) ? name : $"{name} · {directory}";
}

bool IsLoraPlaceholder(string modelName) {
    var value = modelName.Trim().Replace("_", "").Replace("-", "").Replace(" ", "");
    return value.Equals("none", StringComparison.OrdinalIgnoreCase) ||
        value.Equals("noone", StringComparison.OrdinalIgnoreCase) ||
        value.Equals("null", StringComparison.OrdinalIgnoreCase) ||
        value.Equals("undefined", StringComparison.OrdinalIgnoreCase);
}

sealed class PlatformProfile {
    public string PythonPath { get; set; } = "";
    public string MainPyPath { get; set; } = "";
    public string WorkingDirectory { get; set; } = "";
    public string OutputDirectory { get; set; } = "";
    public string[] StartArguments { get; set; } = Array.Empty<string>();
    public bool PathsExist => File.Exists(PythonPath) && File.Exists(MainPyPath) && Directory.Exists(WorkingDirectory) && Directory.Exists(OutputDirectory);
}

sealed class BridgeSettings {
    public string ComfyUiBaseUrl { get; set; } = "http://127.0.0.1:8188";
    public string PythonPath { get; set; } = "";
    public string MainPyPath { get; set; } = "";
    public string WorkingDirectory { get; set; } = "";
    public string WorkflowPath { get; set; } = "";
    public string OutputDirectory { get; set; } = "";
    public string MigrationDirectory { get; set; } = @"C:\Users\49213\Desktop\A\ai成品";
    public string MaidAiDirectory { get; set; } = @"C:\Users\49213\Desktop\A\codex\AI_maid\Assets\image_tiles\动漫扶她";
    public PlatformProfile? Windows { get; set; }
    public PlatformProfile? MacOS { get; set; }
    public string LocalToken { get; set; } = "";
    public string[] AllowedOrigins { get; set; } = Array.Empty<string>();
    public int StartTimeoutSeconds { get; set; } = 120;
    public string TwitterChromePath { get; set; } = "";
    public string TwitterProfileDirectory { get; set; } = "";
    public bool TwitterHeadless { get; set; } = true;
    public string PlatformName => OperatingSystem.IsMacOS() ? "macOS" : "Windows";
    public string ExpectedComfyDirectory => OperatingSystem.IsMacOS() ? "/Users/Shared/ComfyUI" : ActiveProfile.WorkingDirectory;
    public PlatformProfile ActiveProfile {
        get {
            var configured = OperatingSystem.IsMacOS() ? MacOS : Windows;
            return configured ?? new PlatformProfile {
                PythonPath = PythonPath,
                MainPyPath = MainPyPath,
                WorkingDirectory = WorkingDirectory,
                OutputDirectory = OutputDirectory,
                StartArguments = OperatingSystem.IsMacOS() ? new[] { "--enable-manager" } : new[] { "--windows-standalone-build", "--enable-manager" }
            };
        }
    }
    public bool IsPlatformConfigured => ActiveProfile.PathsExist;
    public string ResolveWorkflowPath() => File.Exists(Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI", "workflow_api.json"))
        ? Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI", "workflow_api.json") : WorkflowPath;
    public string ResolveBindingPath() => Path.Combine(AppContext.BaseDirectory, "Resources", "ComfyUI", "workflow_binding.json");
    public void Validate() {
        if (!Uri.TryCreate(ComfyUiBaseUrl, UriKind.Absolute, out var uri) || !IPAddress.IsLoopback(Dns.GetHostAddresses(uri.Host)[0])) throw new InvalidOperationException("ComfyUiBaseUrl must be loopback");
        if (!File.Exists(ResolveWorkflowPath()) || !File.Exists(ResolveBindingPath())) throw new InvalidOperationException("Bundled workflow files do not exist");
        if (LocalToken.Length < 24 || AllowedOrigins.Length == 0) throw new InvalidOperationException("LocalToken and AllowedOrigins are required");
    }
}
sealed class FolderRequest { public string? Name { get; set; } }
sealed record ImageMigrationFile(string Source, string Target, string Sha256);
sealed record ImageMigrationPlan(List<ImageMigrationFile> Files, string? Error);
sealed record PreparedGeneration(BridgeQueuedGeneration QueueItem, string PromptId, string FinalOutputNodeId, long? ActualSeed, int? Width, int? Height, string? InputSha256, string? InputFileName);
sealed record GenerationPreparation(PreparedGeneration? Value, IResult? Error);
sealed class AssetPathRequest { public string? Path { get; set; } }
sealed class HistoryMoveRequest { public string[] PromptIds { get; set; } = Array.Empty<string>(); public string? Folder { get; set; } }
class GalleryPathsRequest { public string[] Paths { get; set; } = Array.Empty<string>(); }
sealed class ClientLogRequest { public string Scope { get; set; } = "unknown"; public string Message { get; set; } = ""; public string? PromptId { get; set; } public string? Path { get; set; } public string? Details { get; set; } }
sealed class GalleryMoveRequest : GalleryPathsRequest { public string? Folder { get; set; } }
sealed class MigrationSettingsRequest { public string? Directory { get; set; } }
sealed class MaidAiSettingsRequest { public string? Directory { get; set; } }
sealed class BridgeUserSettings { public string? Directory { get; set; } public string? MaidAiDirectory { get; set; } }
sealed class LocalWorkflowSettingsRequest { public string? Directory { get; set; } }
sealed class LocalWorkflowScanResult {
    public List<JsonObject> Workflows { get; set; } = new();
    public List<LocalWorkflowRejection> Rejected { get; set; } = new();
}
sealed class LocalWorkflowRejection {
    public string Path { get; set; } = "";
    public string Message { get; set; } = "";
}
sealed class BridgeQueuedGeneration {
    public string PromptId { get; set; } = "";
    public JsonObject Prompt { get; set; } = new();
    public string ClientId { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? SubmittedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }
    public string State { get; set; } = "PENDING";
    public string? Error { get; set; }
}
sealed class GalleryFile {
    public string FullPath { get; set; } = "";
    public string Path { get; set; } = "";
    public string Filename { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
}
sealed class GalleryImage {
    public string Path { get; set; } = "";
    public string FullPath { get; set; } = "";
    public string Filename { get; set; } = "";
    public long SizeBytes { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
}
sealed class GenerationLora {
    public string Name { get; set; } = "";
    public double ModelStrength { get; set; } = 1;
    public double ClipStrength { get; set; } = 1;
}

static class ExplorerWindowActivator {
    const int SwRestore = 9;

    public static void Activate(string directory) {
        if (!OperatingSystem.IsWindows()) return;
        var target = Path.GetFullPath(directory).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var thread = new Thread(() => ActivateOnStaThread(target)) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join(TimeSpan.FromSeconds(2));
    }

    static void ActivateOnStaThread(string target) {
        object? shell = null;
        try {
            var shellType = Type.GetTypeFromProgID("Shell.Application");
            if (shellType == null) return;
            shell = Activator.CreateInstance(shellType);
            if (shell == null) return;
            for (var attempt = 0; attempt < 12; attempt++) {
                dynamic windows = shellType.InvokeMember("Windows", System.Reflection.BindingFlags.InvokeMethod, null, shell, null)!;
                var count = (int)windows.Count;
                for (var index = 0; index < count; index++) {
                    dynamic window = windows.Item(index);
                    string? locationUrl = Convert.ToString((object?)window.LocationURL);
                    if (!TryGetLocalDirectory(locationUrl, out string current) ||
                        !string.Equals(current, target, StringComparison.OrdinalIgnoreCase)) continue;
                    var handle = new IntPtr(Convert.ToInt64(window.HWND));
                    ShowWindowAsync(handle, SwRestore);
                    BringWindowToTop(handle);
                    SetForegroundWindow(handle);
                    return;
                }
                Thread.Sleep(100);
            }
        } catch {
            // Explorer has already received the /select request. Foreground
            // activation is a best-effort enhancement for Windows shell reuse.
        } finally {
            if (shell != null && System.Runtime.InteropServices.Marshal.IsComObject(shell))
                System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shell);
        }
    }

    static bool TryGetLocalDirectory(string? locationUrl, out string directory) {
        directory = "";
        if (string.IsNullOrWhiteSpace(locationUrl) ||
            !Uri.TryCreate(locationUrl, UriKind.Absolute, out var uri) || !uri.IsFile) return false;
        try {
            directory = Path.GetFullPath(uri.LocalPath).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            return true;
        } catch { return false; }
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool ShowWindowAsync(IntPtr window, int command);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool BringWindowToTop(IntPtr window);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool SetForegroundWindow(IntPtr window);
}
