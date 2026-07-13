using System.Diagnostics;
using System.Net;
using System.Security.Cryptography;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
using System.Text.Json;
using System.Buffers.Binary;

var builder = WebApplication.CreateBuilder(new WebApplicationOptions {
    Args = args,
    ContentRootPath = AppContext.BaseDirectory
});
builder.WebHost.UseUrls("http://127.0.0.1:32145");
var settings = builder.Configuration.Get<BridgeSettings>() ?? throw new InvalidOperationException("Missing bridge configuration");
settings.Validate();
builder.Services.AddSingleton(settings);
builder.Services.AddSingleton<TwitterLocalPublisher>();
builder.Services.AddHttpClient("comfy", client => {
    client.BaseAddress = new Uri(settings.ComfyUiBaseUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromMinutes(15);
});
var app = builder.Build();
Process? comfyProcess = null;
var galleryIndexLock = new SemaphoreSlim(1, 1);
var agentLogLock = new SemaphoreSlim(1, 1);

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
                        context.Request.Path.StartsWithSegments("/api/migration") ||
                        context.Request.Path.StartsWithSegments("/api/folders") ||
                        context.Request.Path.StartsWithSegments("/api/generate") ||
                        context.Request.Path.StartsWithSegments("/api/lora-models") ||
                        context.Request.Path.StartsWithSegments("/api/local-workflows") ||
                        context.Request.Path.StartsWithSegments("/api/logs") ||
                        context.Request.Path.StartsWithSegments("/api/twitter") ||
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
    platform = settings.PlatformName,
    configured = settings.IsPlatformConfigured,
    expectedComfyDirectory = settings.ExpectedComfyDirectory
}));
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
app.MapPost("/api/comfy/stop", async (IHttpClientFactory factory) => {
    if (!await IsComfyRunning(factory))
        return Results.Ok(new { success = true, running = false, message = "ComfyUI is already stopped" });

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
    for (var i = 0; i < 15; i++) { if (!await IsComfyRunning(factory)) return Results.Ok(new { success = true, running = false, message = "ComfyUI stopped" }); await Task.Delay(500); }
    return Results.Problem("ComfyUI process was stopped but the API is still reachable", statusCode: 500);
});

app.MapPost("/api/comfy/restart", async (IHttpClientFactory factory) => {
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
        if (await IsComfyRunning(factory)) return Results.Ok(new { success = true, running = true, message = "ComfyUI restarted" });
        if (comfyProcess?.HasExited == true) break;
    }
    return Results.Problem("ComfyUI failed to restart before timeout", statusCode: 504);
});

app.MapPost("/api/tasks/{promptId}/cancel", async (string promptId, IHttpClientFactory factory) => {
    promptId = (promptId ?? "").Trim();
    if (promptId.Length == 0 || promptId.Length > 200)
        return Results.BadRequest(new { success = false, message = "任务 ID 不合法" });
    var client = factory.CreateClient("comfy");
    async Task<(bool Pending, bool Running)> FindTask() {
        using var response = await client.GetAsync("queue");
        response.EnsureSuccessStatusCode();
        var queue = JsonNode.Parse(await response.Content.ReadAsStringAsync()) as JsonObject
            ?? throw new InvalidOperationException("ComfyUI queue 返回格式无效");
        bool Contains(string key) => queue[key] is JsonArray rows && rows.Any(row =>
            row is JsonArray cells && cells.Count > 1 && string.Equals(cells[1]?.ToString(), promptId, StringComparison.Ordinal));
        return (Contains("queue_pending"), Contains("queue_running"));
    }
    var initial = await FindTask();
    var pending = initial.Pending;
    var runningTask = initial.Running;
    if (!pending && !runningTask)
        return Results.Ok(new { success = true, promptId, cancelled = false, wasPending = false, wasRunning = false });
    if (pending) {
        using var deleteResponse = await client.PostAsJsonAsync("queue", new { delete = new[] { promptId } });
        deleteResponse.EnsureSuccessStatusCode();
    }
    if (runningTask) {
        using var interruptResponse = await client.PostAsJsonAsync("interrupt", new { prompt_id = promptId });
        interruptResponse.EnsureSuccessStatusCode();
    }
    for (var attempt = 0; attempt < 120; attempt++) {
        await Task.Delay(250);
        var current = await FindTask();
        if (!current.Pending && !current.Running)
            return Results.Ok(new { success = true, promptId, cancelled = true, wasPending = pending, wasRunning = runningTask });
        if (current.Running && attempt % 8 == 7) {
            using var retryResponse = await client.PostAsJsonAsync("interrupt", new { prompt_id = promptId });
            retryResponse.EnsureSuccessStatusCode();
        }
    }
    return Results.Json(new { success = false, promptId, cancelled = false, wasPending = pending, wasRunning = runningTask,
        message = "任务已收到中断请求，但当前节点在 30 秒内没有停止，请稍后重试" }, statusCode: StatusCodes.Status409Conflict);
}).DisableAntiforgery();

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
        return Results.BadRequest(new { success = false, message = "workflow_api.json 必须是 API Format" });

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
    string Required(string key) => form[key].ToString().Trim() is { Length: > 0 } value
        ? value : throw new InvalidOperationException($"缺少生成参数 {key}");
    void Write(string key, JsonNode? value) { var field = Field(key); SetInput(field.Node, field.Input, value, key); }
    void WriteIf(string key, JsonNode? value) { if (Has(key)) Write(key, value); }
    foreach (var key in new[] { "sourceImage", "styleReference1", "styleReference2", "styleReference3", "styleReference4", "poseReference" }) {
        if (!Has(key)) continue;
        var file = form.Files.GetFile(key) ?? throw new InvalidOperationException($"当前工作流缺少参考图 {key}");
        using var content = new MultipartFormDataContent();
        using var stream = file.OpenReadStream();
        using var image = new StreamContent(stream);
        if (!string.IsNullOrWhiteSpace(file.ContentType)) image.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(file.ContentType);
        content.Add(image, "image", Path.GetFileName(file.FileName));
        content.Add(new StringContent("true"), "overwrite");
        using var uploadResponse = await factory.CreateClient("comfy").PostAsync("upload/image", content);
        var uploadText = await uploadResponse.Content.ReadAsStringAsync();
        JsonNode? uploadJson;
        try { uploadJson = JsonNode.Parse(uploadText); }
        catch (Exception ex) { throw new InvalidOperationException($"上传参考图 {key} 失败（HTTP {(int)uploadResponse.StatusCode}）：{uploadText[..Math.Min(uploadText.Length, 300)]}", ex); }
        if (!uploadResponse.IsSuccessStatusCode || uploadJson?["name"] == null) throw new InvalidOperationException($"上传参考图 {key} 失败");
        Write(key, uploadJson["name"]!.GetValue<string>());
    }
    if (Has("positivePrompt")) Write("positivePrompt", Required("positivePrompt"));
    if (Has("negativePrompt")) Write("negativePrompt", Required("negativePrompt"));
    if (Has("loras")) ApplyDynamicLoras(prompt, form["loras"].ToString());
    var outputWidth = Has("width") ? int.Parse(Required("width")) : 0;
    var outputHeight = Has("height") ? int.Parse(Required("height")) : 0;
    var tileUpscaleNodeIds = prompt
        .Where(entry => entry.Value?["class_type"]?.GetValue<string>()?.Contains("UltimateSDUpscale", StringComparison.OrdinalIgnoreCase) == true &&
                        entry.Value?["inputs"]?["upscale_by"] != null)
        .Select(entry => entry.Key)
        .ToHashSet(StringComparer.Ordinal);
    var usesTileUpscale = tileUpscaleNodeIds.Count > 0 && Has("width") && Has("height");
    if (usesTileUpscale && (outputWidth <= 0 || outputHeight <= 0 || outputWidth % 4 != 0 || outputHeight % 4 != 0))
        throw new InvalidOperationException("最终输出宽高必须是大于 0 且能被 4 整除的整数");
    if (Has("width")) Write("width", usesTileUpscale ? outputWidth * 3 / 4 : outputWidth);
    if (Has("height")) Write("height", usesTileUpscale ? outputHeight * 3 / 4 : outputHeight);
    if (Has("batchSize")) Write("batchSize", int.Parse(Required("batchSize")));
    if (usesTileUpscale) {
        foreach (var entry in prompt.Where(entry => tileUpscaleNodeIds.Contains(entry.Key)))
            if (entry.Value?["inputs"] is JsonObject tileInputs) tileInputs["upscale_by"] = 4.0 / 3.0;
        foreach (var entry in prompt) {
            if (entry.Value?["class_type"]?.GetValue<string>()?.Equals("ImageScale", StringComparison.OrdinalIgnoreCase) != true ||
                entry.Value?["inputs"] is not JsonObject scaleInputs ||
                scaleInputs["image"] is not JsonArray imageLink || imageLink.Count == 0 ||
                imageLink[0] is not JsonValue sourceValue || !sourceValue.TryGetValue<string>(out var sourceId) ||
                !tileUpscaleNodeIds.Contains(sourceId)) continue;
            if (scaleInputs.ContainsKey("width")) scaleInputs["width"] = outputWidth;
            if (scaleInputs.ContainsKey("height")) scaleInputs["height"] = outputHeight;
        }
    }
    if (Has("width")) WriteIf("finalWidth", outputWidth);
    if (Has("height")) WriteIf("finalHeight", outputHeight);
    var actualSeed = Has("seed")
        ? (string.Equals(form["randomSeed"], "true", StringComparison.OrdinalIgnoreCase)
            ? Random.Shared.NextInt64(0, long.MaxValue) : long.Parse(Required("seed")))
        : 0L;
    WriteIf("seed", actualSeed);
    WriteIf("secondPassSeed", actualSeed);
    WriteIf("faceDetailerSeed", actualSeed);
    if (Has("steps")) Write("steps", int.Parse(Required("steps")));
    if (Has("cfg")) Write("cfg", double.Parse(Required("cfg")));
    if (Has("sampler")) Write("sampler", Required("sampler"));
    if (Has("scheduler")) Write("scheduler", Required("scheduler"));
    if (Has("checkpoint") && fields["checkpoint"]?["fixedValue"] == null) Write("checkpoint", Required("checkpoint"));
    if (Has("denoise")) Write("denoise", double.Parse(Required("denoise")));
    if (Has("styleStrength")) Write("styleStrength", double.Parse(Required("styleStrength")));
    if (Has("openPoseStrength")) Write("openPoseStrength", double.Parse(Required("openPoseStrength")));
    if (Has("secondPassSteps")) Write("secondPassSteps", int.Parse(Required("secondPassSteps")));
    if (Has("secondPassDenoise")) Write("secondPassDenoise", double.Parse(Required("secondPassDenoise")));
    if (Has("faceDetailerSteps")) Write("faceDetailerSteps", int.Parse(Required("faceDetailerSteps")));
    if (Has("faceDetailerDenoise")) Write("faceDetailerDenoise", double.Parse(Required("faceDetailerDenoise")));
    JsonNode PostedScalar(string key) {
        var field = Field(key);
        var original = field.Node["inputs"]?[field.Input];
        var raw = form[key].ToString();
        if (original is JsonValue value) {
            if (value.TryGetValue<bool>(out _)) return JsonValue.Create(bool.Parse(string.IsNullOrWhiteSpace(raw) ? Required(key) : raw))!;
            if (value.TryGetValue<long>(out _)) return JsonValue.Create(long.Parse(string.IsNullOrWhiteSpace(raw) ? Required(key) : raw, System.Globalization.CultureInfo.InvariantCulture))!;
            if (value.TryGetValue<double>(out _)) return JsonValue.Create(double.Parse(string.IsNullOrWhiteSpace(raw) ? Required(key) : raw, System.Globalization.CultureInfo.InvariantCulture))!;
        }
        return JsonValue.Create(raw)!;
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
    using var submit = await factory.CreateClient("comfy").PostAsJsonAsync("prompt", new { prompt, client_id = form["clientId"].ToString() });
    var submitText = await submit.Content.ReadAsStringAsync();
    JsonNode? result;
    try { result = JsonNode.Parse(submitText); }
    catch (Exception ex) { throw new InvalidOperationException($"ComfyUI 提交接口返回非 JSON（HTTP {(int)submit.StatusCode}）：{submitText[..Math.Min(submitText.Length, 500)]}", ex); }
    if (!submit.IsSuccessStatusCode || result?["prompt_id"] == null) {
        var validationDetails = new List<string>();
        if (result?["node_errors"] is JsonObject nodeErrors)
            foreach (var nodeError in nodeErrors)
                if (nodeError.Value?["errors"] is JsonArray errors)
                    foreach (var error in errors.OfType<JsonObject>()) {
                        var nodeTitle = nodeError.Value?["_meta"]?["title"]?.ToString();
                        var nodeType = nodeError.Value?["class_type"]?.ToString();
                        var detail = error["details"]?.ToString();
                        var description = error["message"]?.ToString();
                        validationDetails.Add($"{nodeTitle ?? nodeType ?? nodeError.Key}：{description}{(string.IsNullOrWhiteSpace(detail) ? "" : $"（{detail}）")}");
                    }
        var baseMessage = result?["error"]?["message"]?.ToString() ?? "ComfyUI 提交失败";
        var message = validationDetails.Count == 0 ? baseMessage : $"{baseMessage}：{string.Join("；", validationDetails.Take(4))}";
        return Results.Json(new { success = false, message, nodeErrors = result?["node_errors"]?.DeepClone() }, statusCode: (int)submit.StatusCode);
    }
    var promptId = result["prompt_id"]!.GetValue<string>();
    await AddGenerationRecord(new GalleryGenerationRecord {
        PromptId = promptId,
        FilenamePrefix = NormalizeRelativePath(filenamePrefix),
        CreatedAt = DateTimeOffset.Now,
        PositivePrompt = form["positivePrompt"].ToString(),
        NegativePrompt = form["negativePrompt"].ToString(),
        Loras = ReadSelectedLoras(form["loras"].ToString()),
        WorkflowId = form["workflowId"].ToString(),
        Seed = actualSeed,
        Width = Has("width") ? int.Parse(Required("width")) : 0,
        Height = Has("height") ? int.Parse(Required("height")) : 0,
        Steps = Has("steps") ? int.Parse(Required("steps")) : 0,
        Cfg = Has("cfg") ? double.Parse(Required("cfg")) : 0,
        Sampler = form["sampler"].ToString(),
        Scheduler = form["scheduler"].ToString()
    });
    return Results.Ok(new { success = true, promptId, finalOutputNodeId = finalId, actualSeed,
        width = Has("width") ? int.Parse(Required("width")) : 0, height = Has("height") ? int.Parse(Required("height")) : 0 });
}).DisableAntiforgery();

app.MapGet("/api/gallery", async (int? maxItems) => {
    var items = await ScanGallery(Math.Clamp(maxItems ?? 300, 1, 1000));
    return Results.Ok(new { success = true, items });
});

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

app.MapGet("/api/assets", (int? maxItems) => Results.Ok(new { success = true, items = ScanMigratedAssets(Math.Clamp(maxItems ?? 1000, 1, 5000)) }));
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
    var assets = new List<object>();
    foreach (var path in paths) {
        var source = Path.GetFullPath(path);
        if (!File.Exists(source) || !IsGalleryImage(source)) throw new InvalidOperationException($"资产图片不存在：{path}");
        var target = Path.Combine(destination, Path.GetFileName(source));
        if (!string.Equals(source, target, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal)) {
            var suffix = 1;
            while (File.Exists(target)) target = Path.Combine(destination, $"{Path.GetFileNameWithoutExtension(source)}_{suffix++}{Path.GetExtension(source)}");
            File.Move(source, target);
        }
        var image = ToGalleryImage(new GalleryFile { FullPath = target, Path = target, Filename = Path.GetFileName(target), CreatedAt = new DateTimeOffset(File.GetLastWriteTime(target)) });
        assets.Add(new { oldPath = source, localPath = target,
            localUrl = $"http://127.0.0.1:32145/api/assets/file?path={Uri.EscapeDataString(target)}",
            fileName = image.Filename, fileSize = image.SizeBytes, width = image.Width, height = image.Height });
    }
    return Results.Ok(new { success = true, platform = settings.PlatformName, assets });
}).DisableAntiforgery();

app.MapGet("/api/migration/settings", () => Results.Ok(new { success = true, directory = GetMigrationDirectory() }));
app.MapPost("/api/migration/settings", async (MigrationSettingsRequest request) => {
    var directory = (request.Directory ?? "").Trim();
    if (!Path.IsPathFullyQualified(directory)) return Results.BadRequest(new { success = false, message = "迁移目录必须是完整路径" });
    directory = Path.GetFullPath(directory);
    Directory.CreateDirectory(directory);
    await File.WriteAllTextAsync(MigrationSettingsPath(), JsonSerializer.Serialize(new { directory }, GalleryJsonOptions(writeIndented: true)));
    return Results.Ok(new { success = true, directory });
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
    await File.WriteAllTextAsync(MigrationSettingsPath(), JsonSerializer.Serialize(new { directory }, GalleryJsonOptions(writeIndented: true)));
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
    await UpdateIndexedPaths(paths, null);
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
    var gallery = await ScanGallery(1000);
    var metadataByPath = gallery.SelectMany(item => item.Images.Select(image => new { image.Path, Item = item }))
        .ToDictionary(x => x.Path, x => x.Item, StringComparer.OrdinalIgnoreCase);
    var moved = new List<(string OldPath, string NewPath, string Source, string Target)>();
    var manifestItems = new JsonArray();
    try {
        foreach (var oldPath in paths) {
            var source = ResolveGalleryPath(oldPath, mustExist: true);
            if (!IsGalleryImage(source)) throw new InvalidOperationException($"不是可迁移的图片：{oldPath}");
            var target = Path.Combine(destination, Path.GetFileName(source));
            var suffix = 1;
            while (File.Exists(target)) target = Path.Combine(destination, $"{Path.GetFileNameWithoutExtension(source)}_{suffix++}{Path.GetExtension(source)}");
            var newPath = externalDestination ? target : NormalizeRelativePath(Path.GetRelativePath(root, target));
            if (string.Equals(oldPath, newPath, StringComparison.OrdinalIgnoreCase)) continue;
            File.Move(source, target);
            moved.Add((oldPath, newPath, source, target));
            metadataByPath.TryGetValue(oldPath, out var item);
            manifestItems.Add(new JsonObject {
                ["originalFileName"] = Path.GetFileName(source),
                ["originalPath"] = oldPath,
                ["newPath"] = newPath,
                ["metadata"] = item == null ? null : JsonSerializer.SerializeToNode(new {
                    item.PromptId, item.Prompt, item.NegativePrompt, item.Loras, item.CreatedAt, item.Width, item.Height,
                    item.Seed, item.Steps, item.Cfg, item.Sampler, item.Scheduler, item.WorkflowId
                })
            });
        }
        if (moved.Count == 0) return Results.BadRequest(new { success = false, message = "图片已经在目标文件夹" });
        var manifestName = $"aiprovider-migration-{DateTime.Now:yyyyMMdd-HHmmssfff}.json";
        var manifestPath = Path.Combine(destination, manifestName);
        var manifest = new JsonObject {
            ["format"] = "aiprovider-gallery-migration", ["version"] = 1,
            ["migratedAt"] = DateTimeOffset.Now.ToString("O"), ["destinationFolder"] = destination,
            ["items"] = manifestItems
        };
        await File.WriteAllTextAsync(manifestPath, manifest.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        foreach (var file in moved) await UpdateIndexedPaths(new[] { file.OldPath }, externalDestination ? null : file.NewPath);
        var assets = externalDestination ? moved.Select(file => {
            metadataByPath.TryGetValue(file.OldPath, out var item);
            var image = ToGalleryImage(new GalleryFile { FullPath = file.Target, Path = file.Target, Filename = Path.GetFileName(file.Target), CreatedAt = new DateTimeOffset(File.GetLastWriteTime(file.Target)) });
            return new {
                localPath = file.Target, localUrl = $"http://127.0.0.1:32145/api/assets/file?path={Uri.EscapeDataString(file.Target)}", fileName = image.Filename, fileSize = image.SizeBytes,
                width = image.Width ?? item?.Width, height = image.Height ?? item?.Height,
                prompt = item?.Prompt, negativePrompt = item?.NegativePrompt,
                lorasJson = item?.Loras == null ? null : JsonSerializer.Serialize(item.Loras, GalleryJsonOptions()), seed = item?.Seed,
                steps = item?.Steps, cfg = item?.Cfg, sampler = item?.Sampler,
                scheduler = item?.Scheduler, workflowId = item?.WorkflowId,
                generatedAt = item?.CreatedAt ?? new DateTimeOffset(File.GetLastWriteTime(file.Target))
            };
        }).ToArray() : Array.Empty<object>();
        return Results.Ok(new { success = true, moved = moved.Count, folder = destination, manifest = manifestName, platform = settings.PlatformName, assets });
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
    var planned = new List<(string Source, string Target)>();
    var taskRecords = new JsonArray();
    var reservedTargets = new HashSet<string>(OperatingSystem.IsWindows() ? StringComparer.OrdinalIgnoreCase : StringComparer.Ordinal);
    foreach (var promptId in promptIds) {
        var history = await client.GetFromJsonAsync<JsonObject>($"history/{promptId}");
        var item = history?[promptId];
        if (item == null) return Results.NotFound(new { success = false, message = $"任务 {promptId} 不存在" });
        var movedFiles = new JsonArray();
        if (item["outputs"] is JsonObject outputs) foreach (var output in outputs) {
            if (output.Value?["images"] is not JsonArray images) continue;
            foreach (var image in images) {
                var filename = image?["filename"]?.GetValue<string>();
                var subfolder = image?["subfolder"]?.GetValue<string>() ?? "";
                if (string.IsNullOrWhiteSpace(filename)) continue;
                var source = Path.GetFullPath(Path.Combine(root, subfolder, filename));
                if (!source.StartsWith(rootPrefix, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal) || !File.Exists(source)) continue;
                var target = Path.Combine(destination, Path.GetFileName(filename));
                var index = 1;
                while (File.Exists(target) || reservedTargets.Contains(target))
                    target = Path.Combine(destination, $"{Path.GetFileNameWithoutExtension(filename)}_{index++}{Path.GetExtension(filename)}");
                if (string.Equals(source, target, OperatingSystem.IsWindows() ? StringComparison.OrdinalIgnoreCase : StringComparison.Ordinal)) continue;
                reservedTargets.Add(target);
                planned.Add((source, target));
                movedFiles.Add(new JsonObject { ["from"] = Path.GetRelativePath(root, source), ["to"] = Path.GetRelativePath(root, target) });
            }
        }
        taskRecords.Add(new JsonObject { ["promptId"] = promptId, ["history"] = item.DeepClone(), ["movedFiles"] = movedFiles });
    }
    if (planned.Count == 0) return Results.BadRequest(new { success = false, message = "选中任务没有可迁移的图片，或图片已经在目标文件夹" });

    var moved = new List<(string Source, string Target)>();
    var manifestPath = Path.Combine(destination, $"aiprovider-migration-{DateTime.Now:yyyyMMdd-HHmmssfff}.json");
    try {
        foreach (var file in planned) { File.Move(file.Source, file.Target); moved.Add(file); }
        var manifest = new JsonObject {
            ["format"] = "aiprovider-comfy-migration",
            ["version"] = 1,
            ["migratedAt"] = DateTimeOffset.Now.ToString("O"),
            ["platform"] = settings.PlatformName,
            ["tasks"] = taskRecords
        };
        await File.WriteAllTextAsync(manifestPath, manifest.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        using var deleteResponse = await client.PostAsJsonAsync("history", new { delete = promptIds });
        deleteResponse.EnsureSuccessStatusCode();
    } catch {
        if (File.Exists(manifestPath)) File.Delete(manifestPath);
        foreach (var file in moved.AsEnumerable().Reverse()) if (File.Exists(file.Target) && !File.Exists(file.Source)) File.Move(file.Target, file.Source);
        throw;
    }
    return Results.Ok(new { success = true, moved = moved.Count, tasks = promptIds.Length, folder, manifest = Path.GetFileName(manifestPath) });
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
    foreach (var header in response.Headers) context.Response.Headers[header.Key] = header.Value.ToArray();
    foreach (var header in response.Content.Headers) context.Response.Headers[header.Key] = header.Value.ToArray();
    context.Response.Headers.Remove("transfer-encoding");
    await response.Content.CopyToAsync(context.Response.Body, context.RequestAborted);
});

app.Run();

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
    var files = Directory.EnumerateFiles(directory, "*.json", SearchOption.AllDirectories)
        .OrderBy(path => path, StringComparer.OrdinalIgnoreCase).Take(200).ToArray();
    foreach (var path in files) {
        var relative = NormalizeRelativePath(Path.GetRelativePath(directory, path));
        try {
            var info = new FileInfo(path);
            if (info.Length > 5 * 1024 * 1024) throw new InvalidOperationException("文件超过 5 MB");
            var raw = await File.ReadAllTextAsync(path);
            var source = JsonNode.Parse(raw) as JsonObject ?? throw new InvalidOperationException("JSON 根节点不是对象");
            var prompt = IsApiWorkflow(source) ? source : await ConvertLocalWorkflow(factory, raw);
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

async Task<JsonObject> ConvertLocalWorkflow(IHttpClientFactory factory, string raw) {
    using var content = new StringContent(raw, System.Text.Encoding.UTF8, "application/json");
    using var response = await factory.CreateClient("comfy").PostAsync("workflow/convert", content);
    var converted = await response.Content.ReadAsStringAsync();
    if (!response.IsSuccessStatusCode)
        throw new InvalidOperationException(response.StatusCode == HttpStatusCode.NotFound
            ? "ComfyUI 未安装工作流转换器"
            : $"转换失败（HTTP {(int)response.StatusCode}）");
    var prompt = JsonNode.Parse(converted) as JsonObject ?? throw new InvalidOperationException("转换器返回了无效 JSON");
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

    var latent = Find(node => node["class_type"]?.GetValue<string>() == "EmptyLatentImage" && node["inputs"]?["width"] != null && node["inputs"]?["height"] != null);
    Bind("width", latent, "width");
    Bind("height", latent, "height");
    if (latent?.Value["inputs"]?["batch_size"] != null) Bind("batchSize", latent, "batch_size");

    var sampler = Find(node => node["class_type"]?.GetValue<string>() is string type &&
        (type.Equals("KSampler", StringComparison.OrdinalIgnoreCase) || type.Equals("KSamplerAdvanced", StringComparison.OrdinalIgnoreCase)));
    Bind("seed", sampler, sampler?.Value["inputs"]?["seed"] != null ? "seed" : "noise_seed");
    Bind("steps", sampler, "steps");
    Bind("cfg", sampler, "cfg");
    Bind("sampler", sampler, "sampler_name");
    Bind("scheduler", sampler, "scheduler");
    if (sampler?.Value["inputs"]?["denoise"] != null) Bind("denoise", sampler, "denoise");

    var checkpoint = Find(node => node["class_type"]?.GetValue<string>() == "CheckpointLoaderSimple" && node["inputs"]?["ckpt_name"] != null);
    Bind("checkpoint", checkpoint, "ckpt_name");
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
    var required = isTextGeneration
        ? new[] { "positivePrompt", "negativePrompt", "width", "height", "seed", "steps", "cfg", "sampler", "scheduler", "filenamePrefix" }
        : sourceImage != null
            ? new[] { "sourceImage", "filenamePrefix" }
            : new[] { "filenamePrefix" };
    var missing = required.Where(key => !fields.ContainsKey(key)).ToArray();
    if (missing.Length > 0) throw new InvalidOperationException("无法自动识别字段：" + string.Join("、", missing));
    if (output == null) throw new InvalidOperationException("找不到 SaveImage 输出节点");
    var primaryOutput = output.Value;

    var id = "local-" + Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(relative.ToLowerInvariant())))[..16].ToLowerInvariant();
    defaults["workflowId"] = id;
    var hasControlNet = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("ControlNet", StringComparison.OrdinalIgnoreCase) == true);
    var needsSourceImage = sourceImage != null;
    var hasBackgroundModel = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("BackgroundRemovalModel", StringComparison.OrdinalIgnoreCase) == true);
    var hasBackgroundRemoval = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("RemoveBackground", StringComparison.OrdinalIgnoreCase) == true);
    var hasAlphaJoin = entries.Values.Any(node => node["class_type"]?.GetValue<string>()?.Contains("JoinImageWithAlpha", StringComparison.OrdinalIgnoreCase) == true);
    var autoCutout = hasBackgroundModel && hasBackgroundRemoval && hasAlphaJoin && transparentOutput != null;
    var transparentOutputId = transparentOutput?.Key;
    var generationAndCutout = isTextGeneration && autoCutout && transparentOutputId != primaryOutput.Key;
    defaults["generateTransparent"] = generationAndCutout;
    return new JsonObject {
        ["id"] = id,
        ["name"] = Path.ChangeExtension(relative, null)?.Replace("/", " › "),
        ["relativePath"] = relative,
        ["modifiedAt"] = new DateTimeOffset(modifiedAtUtc, TimeSpan.Zero),
        ["definition"] = prompt.DeepClone(),
        ["binding"] = new JsonObject {
            ["fields"] = fields,
            ["outputNode"] = new JsonObject { ["nodeId"] = primaryOutput.Key, ["title"] = Title(primaryOutput.Key, primaryOutput.Value) },
            ["optionalOutputs"] = generationAndCutout ? new JsonObject { ["transparent"] = transparentOutputId } : null,
            ["capabilities"] = new JsonObject { ["controlNet"] = hasControlNet, ["styleReference"] = false, ["poseReference"] = false, ["inputImage"] = needsSourceImage, ["autoCutout"] = autoCutout, ["generationAndCutout"] = generationAndCutout }
        },
        ["defaults"] = defaults,
        ["fields"] = exposed,
        ["models"] = checkpoint?.Value["inputs"]?["ckpt_name"] is JsonNode checkpointName ? new JsonArray(checkpointName.DeepClone()) : new JsonArray(),
        ["capabilities"] = new JsonObject { ["controlNet"] = hasControlNet, ["styleReference"] = false, ["poseReference"] = false, ["inputImage"] = needsSourceImage, ["autoCutout"] = autoCutout, ["generationAndCutout"] = generationAndCutout }
    };
}

string GalleryRoot() => Path.GetFullPath(settings.ActiveProfile.OutputDirectory);
string GalleryIndexPath() => Path.Combine(GalleryRoot(), ".aiprovider-gallery.json");
string MigrationSettingsPath() => Path.Combine(AppContext.BaseDirectory, "bridge-user-settings.json");
string GetMigrationDirectory() {
    try {
        if (File.Exists(MigrationSettingsPath())) {
            var saved = JsonNode.Parse(File.ReadAllText(MigrationSettingsPath()))?["directory"]?.GetValue<string>();
            if (!string.IsNullOrWhiteSpace(saved) && Path.IsPathFullyQualified(saved)) return Path.GetFullPath(saved);
        }
    } catch { }
    return Path.GetFullPath(settings.MigrationDirectory);
}
string NormalizeRelativePath(string? path) => (path ?? "").Replace('\\', '/').TrimStart('/');
bool IsGalleryImage(string path) => new[] { ".png", ".jpg", ".jpeg", ".webp" }.Contains(Path.GetExtension(path), StringComparer.OrdinalIgnoreCase);

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

List<GalleryItem> ScanMigratedAssets(int maxItems) {
    var root = Path.GetFullPath(GetMigrationDirectory());
    if (!Directory.Exists(root)) return new List<GalleryItem>();
    return Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories).Where(IsGalleryImage)
        .Select(path => {
            var relative = NormalizeRelativePath(Path.GetRelativePath(root, path));
            var file = new GalleryFile { FullPath = path, Path = relative, Filename = Path.GetFileName(path), CreatedAt = new DateTimeOffset(File.GetLastWriteTime(path)) };
            var embedded = ReadEmbeddedGeneration(path);
            return new GalleryItem {
                Id = "asset-" + Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(relative.ToLowerInvariant()))).ToLowerInvariant(),
                Source = "asset", Prompt = embedded?.PositivePrompt ?? "已迁移图片资产", NegativePrompt = embedded?.NegativePrompt,
                CreatedAt = file.CreatedAt, Seed = embedded?.Seed, Steps = embedded?.Steps, Cfg = embedded?.Cfg,
                Sampler = embedded?.Sampler, Scheduler = embedded?.Scheduler, Images = new List<GalleryImage> { ToGalleryImage(file) }
            };
        }).OrderByDescending(item => item.CreatedAt).Take(maxItems).ToList();
}

async Task<GalleryIndex> ReadGalleryIndex() {
    var path = GalleryIndexPath();
    if (!File.Exists(path)) return new GalleryIndex();
    try {
        return JsonSerializer.Deserialize<GalleryIndex>(await File.ReadAllTextAsync(path), GalleryJsonOptions()) ?? new GalleryIndex();
    } catch (JsonException) {
        return new GalleryIndex();
    }
}

async Task WriteGalleryIndex(GalleryIndex index) {
    Directory.CreateDirectory(GalleryRoot());
    var path = GalleryIndexPath();
    var temporary = path + ".tmp";
    await File.WriteAllTextAsync(temporary, JsonSerializer.Serialize(index, GalleryJsonOptions(writeIndented: true)));
    File.Move(temporary, path, true);
}

JsonSerializerOptions GalleryJsonOptions(bool writeIndented = false) => new() {
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    PropertyNameCaseInsensitive = true,
    WriteIndented = writeIndented
};

async Task AddGenerationRecord(GalleryGenerationRecord record) {
    await galleryIndexLock.WaitAsync();
    try {
        var index = await ReadGalleryIndex();
        index.Generations.RemoveAll(item => string.Equals(item.PromptId, record.PromptId, StringComparison.OrdinalIgnoreCase));
        index.Generations.Add(record);
        if (index.Generations.Count > 2000)
            index.Generations = index.Generations.OrderByDescending(item => item.CreatedAt).Take(2000).ToList();
        await WriteGalleryIndex(index);
    } finally { galleryIndexLock.Release(); }
}

async Task UpdateIndexedPaths(IEnumerable<string> oldPaths, string? newPath) {
    var old = oldPaths.Select(NormalizeRelativePath).ToHashSet(StringComparer.OrdinalIgnoreCase);
    await galleryIndexLock.WaitAsync();
    try {
        var index = await ReadGalleryIndex();
        var changed = false;
        foreach (var generation in index.Generations) {
            var retained = generation.Files.Where(path => !old.Contains(NormalizeRelativePath(path))).ToList();
            if (retained.Count == generation.Files.Count) continue;
            if (!string.IsNullOrWhiteSpace(newPath)) retained.Add(NormalizeRelativePath(newPath));
            generation.Files = retained.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            changed = true;
        }
        if (changed || !File.Exists(GalleryIndexPath())) await WriteGalleryIndex(index);
    } finally { galleryIndexLock.Release(); }
}

async Task<List<GalleryItem>> ScanGallery(int maxItems) {
    await galleryIndexLock.WaitAsync();
    try {
        var root = GalleryRoot();
        if (!Directory.Exists(root)) return new List<GalleryItem>();
        var index = await ReadGalleryIndex();
        var files = Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories)
            .Where(IsGalleryImage)
            .Select(path => new GalleryFile {
                FullPath = path,
                Path = NormalizeRelativePath(Path.GetRelativePath(root, path)),
                Filename = Path.GetFileName(path),
                CreatedAt = new DateTimeOffset(File.GetLastWriteTime(path))
            })
            .OrderByDescending(file => file.CreatedAt)
            .Take(5000)
            .ToList();
        var assigned = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var changed = false;
        var items = new List<GalleryItem>();
        foreach (var generation in index.Generations.OrderByDescending(item => item.CreatedAt)) {
            var explicitPaths = generation.Files.Select(NormalizeRelativePath).ToHashSet(StringComparer.OrdinalIgnoreCase);
            var prefix = NormalizeRelativePath(generation.FilenamePrefix);
            var matches = files.Where(file => !assigned.Contains(file.Path) &&
                (explicitPaths.Contains(file.Path) || file.Path.StartsWith(prefix + "_", StringComparison.OrdinalIgnoreCase) ||
                 string.Equals(Path.ChangeExtension(file.Path, null), prefix, StringComparison.OrdinalIgnoreCase))).ToList();
            if (matches.Count == 0) continue;
            foreach (var file in matches) assigned.Add(file.Path);
            var discovered = generation.Files.Concat(matches.Select(file => file.Path)).Select(NormalizeRelativePath).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            if (!discovered.SequenceEqual(generation.Files, StringComparer.OrdinalIgnoreCase)) { generation.Files = discovered; changed = true; }
            items.Add(new GalleryItem {
                Id = generation.PromptId, PromptId = generation.PromptId,
                Prompt = string.IsNullOrWhiteSpace(generation.PositivePrompt) ? "本机生成图片" : generation.PositivePrompt,
                NegativePrompt = generation.NegativePrompt, CreatedAt = generation.CreatedAt,
                Loras = generation.Loras,
                Width = generation.Width, Height = generation.Height, Seed = generation.Seed, Steps = generation.Steps,
                Cfg = generation.Cfg, Sampler = generation.Sampler, Scheduler = generation.Scheduler, WorkflowId = generation.WorkflowId,
                Images = matches.Select(ToGalleryImage).ToList()
            });
        }
        foreach (var file in files.Where(file => !assigned.Contains(file.Path))) {
            var id = Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(file.Path.ToLowerInvariant()))).ToLowerInvariant();
            var embedded = ReadEmbeddedGeneration(file.FullPath);
            items.Add(new GalleryItem {
                Id = id, Prompt = embedded?.PositivePrompt ?? "本机历史图片", NegativePrompt = embedded?.NegativePrompt, Loras = embedded?.Loras ?? new List<GenerationLora>(),
                CreatedAt = file.CreatedAt, Width = embedded?.Width, Height = embedded?.Height, Seed = embedded?.Seed,
                Steps = embedded?.Steps, Cfg = embedded?.Cfg, Sampler = embedded?.Sampler, Scheduler = embedded?.Scheduler,
                Images = new List<GalleryImage> { ToGalleryImage(file) }
            });
        }
        if (changed || !File.Exists(GalleryIndexPath())) await WriteGalleryIndex(index);
        return items.OrderByDescending(item => item.CreatedAt).Take(maxItems).ToList();
    } finally { galleryIndexLock.Release(); }
}

GalleryImage ToGalleryImage(GalleryFile file) {
    var (width, height) = ReadImageDimensions(file.FullPath);
    return new GalleryImage { Path = file.Path, FullPath = file.FullPath, Filename = file.Filename, SizeBytes = new FileInfo(file.FullPath).Length, Width = width, Height = height };
}

void OpenFileInFolder(string file) {
    if (OperatingSystem.IsMacOS())
        Process.Start(new ProcessStartInfo("open", $"-R \"{file}\"") { UseShellExecute = true });
    else
        Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{file}\"") { UseShellExecute = true });
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

GalleryGenerationRecord? ReadEmbeddedGeneration(string path) {
    try {
        var text = ReadPngText(path, "prompt");
        if (string.IsNullOrWhiteSpace(text) || JsonNode.Parse(text) is not JsonObject prompt) return null;
        var samplerEntry = prompt.FirstOrDefault(entry => entry.Value?["class_type"]?.ToString().Contains("KSampler", StringComparison.OrdinalIgnoreCase) == true);
        if (samplerEntry.Value is not JsonObject sampler || sampler["inputs"] is not JsonObject inputs) return null;
        string? LinkedText(string name) {
            if (inputs[name] is not JsonArray link || link[0] == null) return null;
            return prompt[link[0]!.ToString()]?["inputs"]?["text"]?.ToString();
        }
        long? Long(string name) => long.TryParse(inputs[name]?.ToString(), out var value) ? value : null;
        int? Int(string name) => int.TryParse(inputs[name]?.ToString(), out var value) ? value : null;
        double? Double(string name) => double.TryParse(inputs[name]?.ToString(), out var value) ? value : null;
        return new GalleryGenerationRecord {
            PositivePrompt = LinkedText("positive"), NegativePrompt = LinkedText("negative"),
            Seed = Long("seed") ?? Long("noise_seed"), Steps = Int("steps"), Cfg = Double("cfg"),
            Sampler = inputs["sampler_name"]?.ToString(), Scheduler = inputs["scheduler"]?.ToString()
        };
    } catch { return null; }
}

string? ReadPngText(string path, string keyword) {
    using var stream = File.OpenRead(path);
    Span<byte> signature = stackalloc byte[8];
    if (stream.Read(signature) != 8 || !signature.SequenceEqual(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 })) return null;
    Span<byte> header = stackalloc byte[8];
    while (stream.Read(header) == 8) {
        var length = BinaryPrimitives.ReadInt32BigEndian(header[..4]);
        if (length < 0 || length > 16 * 1024 * 1024) return null;
        var type = System.Text.Encoding.ASCII.GetString(header[4..8]);
        if (type is "tEXt" or "iTXt") {
            var data = new byte[length];
            if (stream.Read(data) != length) return null;
            stream.Seek(4, SeekOrigin.Current);
            var zero = Array.IndexOf(data, (byte)0);
            if (zero < 0 || System.Text.Encoding.Latin1.GetString(data, 0, zero) != keyword) continue;
            if (type == "tEXt") return System.Text.Encoding.Latin1.GetString(data, zero + 1, data.Length - zero - 1);
            var cursor = zero + 1;
            if (cursor + 2 > data.Length || data[cursor++] != 0) return null;
            cursor++;
            for (var fields = 0; fields < 2; fields++) { var end = Array.IndexOf(data, (byte)0, cursor); if (end < 0) return null; cursor = end + 1; }
            return System.Text.Encoding.UTF8.GetString(data, cursor, data.Length - cursor);
        }
        stream.Seek(length + 4L, SeekOrigin.Current);
        if (type == "IEND") break;
    }
    return null;
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
sealed class AssetPathRequest { public string? Path { get; set; } }
sealed class HistoryMoveRequest { public string[] PromptIds { get; set; } = Array.Empty<string>(); public string? Folder { get; set; } }
class GalleryPathsRequest { public string[] Paths { get; set; } = Array.Empty<string>(); }
sealed class ClientLogRequest { public string Scope { get; set; } = "unknown"; public string Message { get; set; } = ""; public string? PromptId { get; set; } public string? Path { get; set; } public string? Details { get; set; } }
sealed class GalleryMoveRequest : GalleryPathsRequest { public string? Folder { get; set; } }
sealed class MigrationSettingsRequest { public string? Directory { get; set; } }
sealed class LocalWorkflowSettingsRequest { public string? Directory { get; set; } }
sealed class LocalWorkflowScanResult {
    public List<JsonObject> Workflows { get; set; } = new();
    public List<LocalWorkflowRejection> Rejected { get; set; } = new();
}
sealed class LocalWorkflowRejection {
    public string Path { get; set; } = "";
    public string Message { get; set; } = "";
}
sealed class GalleryIndex {
    public int Version { get; set; } = 1;
    public List<GalleryGenerationRecord> Generations { get; set; } = new();
}
sealed class GalleryGenerationRecord {
    public string PromptId { get; set; } = "";
    public string FilenamePrefix { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public string? PositivePrompt { get; set; }
    public string? NegativePrompt { get; set; }
    public List<GenerationLora> Loras { get; set; } = new();
    public string? WorkflowId { get; set; }
    public long? Seed { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public int? Steps { get; set; }
    public double? Cfg { get; set; }
    public string? Sampler { get; set; }
    public string? Scheduler { get; set; }
    public List<string> Files { get; set; } = new();
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
sealed class GalleryItem {
    public string Id { get; set; } = "";
    public string? PromptId { get; set; }
    public string Source { get; set; } = "output";
    public string Prompt { get; set; } = "";
    public string? NegativePrompt { get; set; }
    public List<GenerationLora> Loras { get; set; } = new();
    public DateTimeOffset CreatedAt { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public long? Seed { get; set; }
    public int? Steps { get; set; }
    public double? Cfg { get; set; }
    public string? Sampler { get; set; }
    public string? Scheduler { get; set; }
    public string? WorkflowId { get; set; }
    public List<GalleryImage> Images { get; set; } = new();
}
sealed class GenerationLora {
    public string Name { get; set; } = "";
    public double ModelStrength { get; set; } = 1;
    public double ClipStrength { get; set; } = 1;
}
