import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const sourcePath = fileURLToPath(new URL("../../ComfyUIAgent/Program.cs", import.meta.url));
const builtInInpaintPath = fileURLToPath(new URL("../../ComfyUIAgent/Resources/ComfyUI/BuiltInWorkflows/sdxl_inpaint_outpaint_api.json", import.meta.url));
const startupInstallerPath = fileURLToPath(new URL("../../ComfyUIAgent/install-startup.ps1", import.meta.url));
const startupUninstallerPath = fileURLToPath(new URL("../../ComfyUIAgent/uninstall-startup.ps1", import.meta.url));

describe("ComfyUIAgent browser launch protocol", () => {
  it("registers and removes the Windows protocol used by the workbench", () => {
    const source = readFileSync(sourcePath, "utf8");
    const installer = readFileSync(startupInstallerPath, "utf8");
    const uninstaller = readFileSync(startupUninstallerPath, "utf8");
    expect(source).toContain('argument.TrimEnd(\'/\'), "aiprovider-bridge://start"');
    expect(source).toContain("Args = builderArgs");
    expect(installer).toContain("HKCU:\\Software\\Classes\\aiprovider-bridge");
    expect(installer).toContain("'URL Protocol'");
    expect(installer).toContain("ComfyUIAgent.exe");
    expect(uninstaller).toContain("HKCU:\\Software\\Classes\\aiprovider-bridge");
    expect(uninstaller).toContain("Remove-Item -LiteralPath $protocolRoot -Recurse -Force");
  });
});

describe("ComfyUIAgent generated image metadata", () => {
  it("does not synthesize Prompt display text for workflows without Prompt fields", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).not.toContain('Prompt = string.IsNullOrWhiteSpace(generation.PositivePrompt) ? "本机生成图片"');
    expect(source).not.toContain('Prompt = embedded?.PositivePrompt ?? "本机历史图片"');
    expect(source).not.toContain('Prompt = embedded?.PositivePrompt ?? "已迁移图片资产"');
  });
});

describe("ComfyUIAgent generation validation boundary", () => {
  it("leaves generation value validation to ComfyUI and exposes rejection through task state", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).not.toContain("缺少生成参数");
    expect(source).not.toContain("最终输出宽高必须是大于 0 且能被 4 整除的整数");
    expect(source).not.toContain("validationDetails");
    expect(source).toContain("lets ComfyUI perform the authoritative node/input validation");
    expect(source).toContain("await FailBridgeQueuedGeneration(item.PromptId, $\"ComfyUI 拒绝任务");
    expect(source).toContain('state = bridgeQueued.State');
    expect(source).toContain('message = bridgeQueued.Error');
  });
});

describe("ComfyUIAgent local inpaint workflow discovery", () => {
  it("only exposes inpaint and outpaint for workflows with a real mask pipeline", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('classType.Contains("VAEEncodeForInpaint"');
    expect(source).toContain('classType.Contains("InpaintModelConditioning"');
    expect(source).toContain('classType.Contains("SetLatentNoiseMask"');
    expect(source).toContain('["inpaint"] = hasInpaintPipeline');
    expect(source).toContain('["outpaint"] = hasInpaintPipeline');
  });

  it("ships a native-node inpaint workflow and selects an installed checkpoint", () => {
    const source = readFileSync(sourcePath, "utf8");
    const workflow = readFileSync(builtInInpaintPath, "utf8");
    expect(source).toContain('"Resources", "ComfyUI", "BuiltInWorkflows"');
    expect(source).toContain('"__AIPROVIDER_FIRST_CHECKPOINT__"');
    expect(workflow).toContain('"class_type": "VAEEncodeForInpaint"');
    expect(workflow).toContain('"class_type": "LoadImage"');
    expect(workflow).toContain('"class_type": "SaveImage"');
  });
});

describe("ComfyUIAgent main model discovery", () => {
  it("binds both checkpoint and diffusion-model workflow loaders and reads their exact ComfyUI choices", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('app.MapGet("/api/main-models"');
    expect(source).toContain('root[nodeType]?["input"]');
    expect(source).toContain('type.Contains("UNET"');
    expect(source).toContain('node["inputs"]?["unet_name"]');
    expect(source).toContain('Bind("checkpoint", primaryModel, checkpoint != null ? "ckpt_name" : "unet_name", "主模型")');
    expect(source).toContain('MainModel = Has("checkpoint") ? form["checkpoint"].ToString() : null');
    expect(source).toContain('public string? MainModel { get; set; }');
  });
});

describe("ComfyUIAgent advanced sampler workflow discovery", () => {
  it("maps Flux latent size and RandomNoise to the standard size and seed controls", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('type.Equals("EmptySD3LatentImage", StringComparison.OrdinalIgnoreCase)');
    expect(source).toContain('?.Equals("RandomNoise", StringComparison.OrdinalIgnoreCase) == true');
    expect(source).toContain('var seedNode = sampler ?? randomNoise;');
    expect(source).toContain('Bind("seed", seedNode');
  });
});

describe("ComfyUIAgent durable generation ownership", () => {
  it("retains accepted tasks through terminal state and cancels asynchronously", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('await SetBridgeGenerationState(item.PromptId, "SUBMITTED")');
    expect(source).toContain('entry.State is "PENDING" or "SUBMITTED" or "QUEUED" or "RUNNING" or "CANCEL_REQUESTED"');
    expect(source).toContain('await SetBridgeGenerationState(tracked.PromptId, "SUCCEEDED")');
    expect(source).toContain('state = "CANCEL_REQUESTED"');
    expect(source).toContain('statusCode: StatusCodes.Status202Accepted');
    expect(source).not.toContain('await RemoveBridgeQueuedGeneration(item.PromptId)');
  });
});

describe("ComfyUIAgent local recycle bin", () => {
  it("keeps local files until a permanent delete is requested from the persisted trash index", () => {
    const source = readFileSync(sourcePath, "utf8");
    expect(source).toContain('app.MapPost("/api/gallery/trash"');
    expect(source).toContain('app.MapPost("/api/gallery/restore"');
    expect(source).toContain('string GalleryTrashIndexPath()');
    expect(source).toContain('只有回收站中的本机图片可以永久删除');
    expect(source).toContain('trashedPaths.Contains(file.Path) == trashedOnly');
  });
});
