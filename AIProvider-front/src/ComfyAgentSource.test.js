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
    expect(source).toContain('state = bridgeState');
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
