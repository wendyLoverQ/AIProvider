// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import WorkflowPanel from "./WorkflowPanel";

describe("WorkflowPanel generation action", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("keeps Krea2 diffusion models out of Flux text-encoder workflows", () => {
    const change = vi.fn();
    const workflow = {
      id: "flux", name: "Flux", capabilities: {}, models: ["fluxedUp.safetensors"],
      definition: { "11": { class_type: "DualCLIPLoader", inputs: { type: "flux" } } },
    };
    render(<WorkflowPanel workflows={[workflow]} workflow={workflow}
      fieldKeys={["checkpoint"]} fieldSpecs={{ checkpoint: { input: "unet_name" } }}
      values={{ checkpoint: "redcraft_Krea2Edition.safetensors" }} mainModels={["fluxedUp.safetensors", "redcraft_Krea2Edition.safetensors"]}
      referenceFiles={{}} presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={change} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    expect(screen.getByRole("option", { name: "fluxedUp.safetensors" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "redcraft_Krea2Edition.safetensors" })).toBeNull();
    expect(change).toHaveBeenCalledWith("checkpoint", "fluxedUp.safetensors");
  });

  it("keeps task progress away from the Generate button", () => {
    render(<WorkflowPanel
      workflows={[{ id: "wf", name: "测试工作流" }]}
      workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={[]}
      fieldSpecs={{}}
      values={{}}
      referenceFiles={{}}
      presets={[]}
      presetQuery=""
      disabled={{ blocked: false, busy: false }}
      loading={false}
      onWorkflowChange={vi.fn()}
      onFieldChange={vi.fn()}
      onReference={vi.fn()}
      onPresetChange={vi.fn()}
      onGenerate={vi.fn()}
    />);

    expect(screen.queryByRole("status")).toBeNull();
    expect(screen.queryByRole("progressbar")).toBeNull();
  });

  it("offers person and background cutout targets when the workflow exposes a strict cutout binding", () => {
    const change = vi.fn();
    render(<WorkflowPanel
      workflows={[{ id: "cutout", name: "工具_抠图" }]}
      workflow={{ id: "cutout", name: "工具_抠图", capabilities: { cutoutTarget: true } }}
      fieldKeys={["cutoutTarget"]}
      fieldSpecs={{ cutoutTarget: { nodeType: "CutoutTarget", label: "抠图目标" } }}
      values={{ cutoutTarget: "person" }}
      referenceFiles={{}}
      presets={[]}
      presetQuery=""
      disabled={{ blocked: false, busy: false }}
      loading={false}
      onWorkflowChange={vi.fn()}
      onFieldChange={change}
      onReference={vi.fn()}
      onPresetChange={vi.fn()}
      onGenerate={vi.fn()}
    />);

    expect(screen.getByRole("button", { name: "保留人物" }).getAttribute("aria-pressed")).toBe("true");
    fireEvent.click(screen.getByRole("button", { name: "保留背景" }));
    expect(change).toHaveBeenCalledWith("cutoutTarget", "background");
  });

  it("exposes editing for the currently selected structured Prompt scheme", () => {
    const edit = vi.fn();
    const reload = vi.fn();
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt", "negativePrompt"]} fieldSpecs={{ positivePrompt: {}, negativePrompt: {} }} values={{ positivePrompt: "p", negativePrompt: "n" }}
      referenceFiles={{}} presets={[{ id: 7, name: "结构化方案" }]} presetQuery="7" presetSaveName="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onPresetSaveNameChange={vi.fn()} onSavePreset={vi.fn()} onReloadPreset={reload} onEditPreset={edit} onGenerate={vi.fn()} />);
    expect(screen.getByRole("option", { name: "结构化方案" }).value).toBe("7");
    expect(screen.queryByRole("textbox", { name: "新 Prompt 方案名称" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "另存为方案" }));
    expect(screen.getByRole("dialog", { name: "另存为 Prompt 方案" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "新 Prompt 方案名称" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "关闭另存为方案" }));
    expect(screen.queryByLabelText("正向提示词")).toBeNull();
    const promptEditButtons = screen.getAllByRole("button", { name: "手动编辑" });
    expect(promptEditButtons[0].getAttribute("aria-expanded")).toBe("false");
    fireEvent.click(promptEditButtons[0]);
    fireEvent.click(promptEditButtons[1]);
    expect(screen.getByLabelText("正向提示词").value).toBe("p");
    expect(screen.getByLabelText("反向提示词").value).toBe("n");
    fireEvent.click(screen.getByRole("button", { name: "编辑当前方案" }));
    expect(edit).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "重新加载当前方案" }));
    expect(reload).toHaveBeenCalledOnce();
  });

  it("keeps the fixed seed input below the seed mode controls", () => {
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["batchSize", "seed"]} fieldSpecs={{ batchSize: {}, seed: {} }} values={{ batchSize: 1, seed: 123, randomSeed: false }}
      referenceFiles={{}} presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    const seed = screen.getByRole("spinbutton", { name: "Seed" });
    expect(screen.getByText("种子", { exact: true })).toBeTruthy();
    expect(screen.queryByText("种子模式", { exact: true })).toBeNull();
    expect(seed.closest(".workflow-panel__seed").classList.contains("is-fixed")).toBe(true);
    expect(seed.closest("label").parentElement.classList.contains("workflow-panel__seed")).toBe(true);
  });

  it("keeps the size selector accessible without a visible redundant label", () => {
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["width", "height"]} fieldSpecs={{ width: {}, height: {} }} values={{ width: 1920, height: 1080 }}
      referenceFiles={{}} presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    expect(screen.getByRole("combobox", { name: "最终输出尺寸" })).toBeTruthy();
    expect(screen.queryByText("最终输出尺寸", { exact: true })).toBeNull();
  });

  it("places the main model on its own row before the seed and generation-count row", () => {
    const change = vi.fn();
    render(<WorkflowPanel workflows={[{ id: "wf", name: "扩散模型工作流" }]} workflow={{ id: "wf", name: "扩散模型工作流", models: ["flux/default.safetensors"], capabilities: {} }}
      fieldKeys={["batchSize", "checkpoint", "height", "seed", "width"]} fieldSpecs={{ width: {}, height: {}, batchSize: {}, seed: {}, checkpoint: { nodeType: "UNETLoader", input: "unet_name" } }}
      values={{ width: 1920, height: 1080, batchSize: 1, seed: 123, randomSeed: true, checkpoint: "flux/default.safetensors" }} mainModels={[{ name: "flux/default.safetensors", displayName: "default · flux" }, { name: "flux/dev.safetensors", displayName: "dev · flux" }]}
      referenceFiles={{}} presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={change} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    const size = screen.getByRole("combobox", { name: "最终输出尺寸" }).closest(".workflow-panel__size");
    const model = screen.getByRole("combobox", { name: "主模型" });
    const seed = screen.getByText("种子", { exact: true }).closest(".workflow-panel__seed");
    const count = screen.getByRole("spinbutton", { name: "生成数量" });
    expect(size.compareDocumentPosition(model) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(model.closest("label").classList.contains("workflow-panel__main-model")).toBe(true);
    expect(model.compareDocumentPosition(seed) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(seed.compareDocumentPosition(count) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(model.title).toContain("扩散模型");
    expect(model.closest("details")).toBeNull();
    fireEvent.change(model, { target: { value: "flux/dev.safetensors" } });
    expect(change).toHaveBeenCalledWith("checkpoint", "flux/dev.safetensors");
  });

  it("switches every prompt term between English and Chinese without changing positions", () => {
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt", "negativePrompt"]} fieldSpecs={{ positivePrompt: {}, negativePrompt: {} }}
      values={{ positivePrompt: "standing, black stockings", negativePrompt: "bad hands" }} referenceFiles={{}}
      promptOptions={[
        { id: "standing", name: "站立", positivePrompt: "standing", type: "positive" },
        { id: "black_stockings", name: "黑丝袜", positivePrompt: "black stockings", type: "positive" },
        { id: "bad_hands", name: "手部错误", negativePrompt: "bad hands", type: "negative" },
      ]}
      presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    const editBtns = screen.getAllByRole("button", { name: "手动编辑" });
    fireEvent.click(editBtns[0]);
    fireEvent.click(editBtns[1]);
    expect(screen.getByLabelText("正向提示词").value).toBe("");
    expect(screen.getByText("standing", { exact: true })).toBeTruthy();
    expect(screen.getByRole("button", { name: "停用词条 站立" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "切换为中文 Prompt" }));
    expect(screen.getByLabelText("正向提示词").value).toBe("");
    expect(screen.getByText("站立", { exact: true })).toBeTruthy();
    expect(screen.getByRole("button", { name: "停用词条 站立" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "停用词条 手部错误" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "切换为英文 Prompt" }));
    expect(screen.getByLabelText("正向提示词").value).toBe("");
    expect(screen.getByText("black stockings", { exact: true })).toBeTruthy();
  });

  it("quickly creates and appends positive and negative catalog terms in their own sections", async () => {
    const change = vi.fn();
    const writes = [];
    vi.stubGlobal("fetch", vi.fn(async (_url, options) => { writes.push(JSON.parse(options.body)); return new Response(JSON.stringify({ code: 200, data: null }), { status: 200, headers: { "Content-Type": "application/json" } }); }));
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt", "negativePrompt"]} fieldSpecs={{ positivePrompt: {}, negativePrompt: {} }}
      values={{ positivePrompt: "standing", negativePrompt: "bad hands" }} referenceFiles={{}} promptOptions={[{ category: "Clothing", type: "positive", allowMultiple: true }]}
      presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={change} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    fireEvent.change(screen.getByLabelText("正向词条中文名称"), { target: { value: "银发" } });
    fireEvent.change(screen.getByLabelText("正向词条英文 Prompt"), { target: { value: "silver hair" } });
    fireEvent.click(screen.getByRole("button", { name: "添加正向词条" }));
    await vi.waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0]).toMatchObject({ category: "Clothing", name: "银发", prompt: "silver hair", type: "positive", allowMultiple: true });
    await vi.waitFor(() => expect(change).toHaveBeenCalledWith("positivePrompt", "standing, silver hair"));
    const buttons = screen.getAllByRole("button", { name: "手动编辑" });
    fireEvent.click(buttons[buttons.length - 1]);
    fireEvent.change(screen.getByLabelText("反向词条中文名称"), { target: { value: "水印" } });
    fireEvent.change(screen.getByLabelText("反向词条英文 Prompt"), { target: { value: "watermark" } });
    fireEvent.click(screen.getByRole("button", { name: "添加反向词条" }));
    await vi.waitFor(() => expect(writes).toHaveLength(2));
    expect(writes[1]).toMatchObject({ category: "Quality", name: "水印", prompt: "watermark", type: "negative", allowMultiple: false });
    await vi.waitFor(() => expect(change).toHaveBeenCalledWith("negativePrompt", "bad hands, watermark"));
  });

  it("keeps structured term order unchanged when a term is disabled", () => {
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt"]} fieldSpecs={{ positivePrompt: {} }} values={{ positivePrompt: "standing, black stockings" }} referenceFiles={{}}
      promptOptions={[{ id: "standing", name: "站立", positivePrompt: "standing", type: "positive" }, { id: "black", name: "黑丝袜", positivePrompt: "black stockings", type: "positive" }]}
      presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    const list = screen.getByLabelText("正向已选结构化词条");
    expect(list.textContent).toBe("standingblack stockings");
    fireEvent.click(screen.getByRole("button", { name: "停用词条 站立" }));
    expect(list.textContent).toBe("standingblack stockings");
    expect(screen.getByRole("button", { name: "启用词条 站立" }).getAttribute("aria-pressed")).toBe("false");
  });

  it("reorders structured terms by drag and updates the prompt order", () => {
    const change = vi.fn();
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt"]} fieldSpecs={{ positivePrompt: {} }} values={{ positivePrompt: "standing, black stockings" }} referenceFiles={{}}
      promptOptions={[{ id: "standing", name: "站立", positivePrompt: "standing", type: "positive" }, { id: "black", name: "黑丝袜", positivePrompt: "black stockings", type: "positive" }]}
      presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={change} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    const source = screen.getByRole("button", { name: "停用词条 站立" }).parentElement;
    const target = screen.getByRole("button", { name: "停用词条 黑丝袜" }).parentElement;
    fireEvent.dragStart(source); fireEvent.drop(target);
    expect(change).toHaveBeenCalledWith("positivePrompt", "black stockings, standing");
  });

  it("opens a quick editor from a structured term context menu", () => {
    render(<WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
      fieldKeys={["positivePrompt"]} fieldSpecs={{ positivePrompt: {} }} values={{ positivePrompt: "standing" }} referenceFiles={{}}
      promptOptions={[{ id: "standing", name: "站立", positivePrompt: "standing", type: "positive", category: "Pose", allowMultiple: false }]}
      presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
      onWorkflowChange={vi.fn()} onFieldChange={vi.fn()} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()} />);
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    fireEvent.contextMenu(screen.getByText("standing", { exact: true }).parentElement);
    fireEvent.click(screen.getByRole("button", { name: "编辑" }));
    expect(screen.getByRole("dialog", { name: "编辑结构化词条" })).toBeTruthy();
    expect(screen.getByLabelText("编辑词条中文名称").value).toBe("站立");
  });

  it("keeps a structured term in the same position after editing its Prompt", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ code: 200, data: null }), { status: 200, headers: { "Content-Type": "application/json" } })));
    function Harness() {
      const [values, setValues] = React.useState({ positivePrompt: "standing, black stockings" });
      const [options, setOptions] = React.useState([
        { id: "standing", name: "站立", prompt: "standing", type: "positive", category: "Pose", allowMultiple: false },
        { id: "black", name: "黑丝袜", prompt: "black stockings", type: "positive", category: "Clothing", allowMultiple: true },
      ]);
      return <WorkflowPanel workflows={[{ id: "wf", name: "测试工作流" }]} workflow={{ id: "wf", name: "测试工作流", capabilities: {} }}
        fieldKeys={["positivePrompt"]} fieldSpecs={{ positivePrompt: {} }} values={values} referenceFiles={{}} promptOptions={options}
        presets={[]} presetQuery="" disabled={{ blocked: false, busy: false }} loading={false}
        onWorkflowChange={vi.fn()} onFieldChange={(field, value) => setValues((current) => ({ ...current, [field]: value }))} onReference={vi.fn()} onPresetChange={vi.fn()} onGenerate={vi.fn()}
        onPromptOptionsReload={() => setOptions((current) => current.map((item) => item.id === "standing" ? { ...item, name: "挺立", prompt: "upright pose" } : item))} />;
    }
    render(<Harness />);
    fireEvent.click(screen.getAllByRole("button", { name: "手动编辑" })[0]);
    fireEvent.contextMenu(screen.getByText("standing", { exact: true }).parentElement);
    fireEvent.click(screen.getByRole("button", { name: "编辑" }));
    fireEvent.change(screen.getByLabelText("编辑词条中文名称"), { target: { value: "挺立" } });
    fireEvent.change(screen.getByLabelText("编辑词条英文 Prompt"), { target: { value: "upright pose" } });
    fireEvent.click(screen.getByRole("button", { name: "保存", exact: true }));
    await vi.waitFor(() => expect(screen.getByLabelText("正向已选结构化词条").textContent).toBe("upright poseblack stockings"));
  });
});
