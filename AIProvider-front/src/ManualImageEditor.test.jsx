// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ManualImageEditor from "./ManualImageEditor";

const context = {
  beginPath: vi.fn(),
  moveTo: vi.fn(),
  lineTo: vi.fn(),
  stroke: vi.fn(),
  clearRect: vi.fn(),
  drawImage: vi.fn(),
  getImageData: vi.fn(() => ({ data: new Uint8ClampedArray(4) })),
  putImageData: vi.fn(),
  translate: vi.fn(),
  rotate: vi.fn(),
  scale: vi.fn(),
  save: vi.fn(),
  restore: vi.fn(),
  closePath: vi.fn(),
  clip: vi.fn(),
  fill: vi.fn(),
};

describe("ManualImageEditor", () => {
  beforeEach(() => {
    Object.values(context).forEach((mock) => typeof mock?.mockClear === "function" && mock.mockClear());
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(context);
    vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation((callback) => callback(new Blob()));
    vi.stubGlobal("requestAnimationFrame", (callback) => callback());
    vi.stubGlobal("ResizeObserver", class { observe() {} disconnect() {} });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders the dedicated tool list and switches tools with shortcuts", () => {
    render(<ManualImageEditor />);
    expect(screen.getByRole("complementary", { name: "图片编辑工具" })).toBeTruthy();
    expect(screen.getByRole("button", { name: /画笔/ }).className).toContain("active");

    fireEvent.keyDown(window, { key: "e" });
    expect(screen.getByRole("button", { name: /橡皮擦/ }).className).toContain("active");

    fireEvent.keyDown(window, { key: "h" });
    expect(screen.getByRole("button", { name: /移动画布/ }).className).toContain("active");
    expect(screen.getByRole("button", { name: /AI 遮罩/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /擦除遮罩/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /导入一张图片开始编辑/ })).toBeTruthy();
  });

  it("updates brush size and records reversible rotation", () => {
    render(<ManualImageEditor />);
    fireEvent.change(screen.getByLabelText("笔刷大小"), { target: { value: "64" } });
    expect(screen.getByText("64px")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "顺时针旋转" }));
    expect(screen.getByText("800 × 1200 px")).toBeTruthy();
    const undo = screen.getByTitle("撤销 (Ctrl+Z)");
    expect(undo.disabled).toBe(false);

    fireEvent.click(undo);
    expect(screen.getByText("1200 × 800 px")).toBeTruthy();
    expect(context.putImageData).toHaveBeenCalled();
  });

  it("exposes OpenCut-style image properties and export settings", () => {
    render(<ManualImageEditor />);
    expect(screen.getByRole("complementary", { name: "图片属性" })).toBeTruthy();
    expect(screen.getByRole("slider", { name: "亮度" }).value).toBe("100");
    expect(screen.getByText("非破坏式调整")).toBeTruthy();
    expect(screen.getByText("PNG")).toBeTruthy();
  });

  it("imports an image and adopts its file name and dimensions", () => {
    class FakeImage {
      naturalWidth = 640;
      naturalHeight = 360;
      set src(_value) { this.onload(); }
    }
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const { container } = render(<ManualImageEditor />);
    const input = container.querySelector('input[type="file"]');
    const file = new File(["image"], "示例图片.png", { type: "image/png" });

    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByText("示例图片")).toBeTruthy();
    expect(screen.getByText("640 × 360 px")).toBeTruthy();
    expect(context.drawImage).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "导出图片" }).disabled).toBe(false);
  });

  it("adjusts, crops, flips, and exports an imported image", () => {
    class FakeImage {
      naturalWidth = 1600;
      naturalHeight = 900;
      set src(_value) { this.onload(); }
    }
    const file = new File(["image"], "portrait.png", { type: "image/png" });
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const { container } = render(<ManualImageEditor />);

    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [file] } });
    fireEvent.change(screen.getByRole("slider", { name: "亮度" }), { target: { value: "140" } });
    expect(screen.getByLabelText("图片编辑画布").style.filter).toContain("brightness(140%)");

    fireEvent.click(screen.getByRole("button", { name: "1 : 1" }));
    expect(screen.getByText("900 × 900 px")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "水平翻转" }));
    expect(context.scale).toHaveBeenCalledWith(-1, 1);

    fireEvent.change(screen.getByRole("combobox", { name: "格式" }), { target: { value: "image/webp" } });
    fireEvent.change(screen.getByRole("slider", { name: "导出质量" }), { target: { value: "80" } });
    fireEvent.click(screen.getByRole("button", { name: "导出图片" }));

    expect(HTMLCanvasElement.prototype.toBlob).toHaveBeenLastCalledWith(expect.any(Function), "image/webp", 0.8);
    expect(context.filter).toContain("brightness(140%)");
    expect(anchorClick).toHaveBeenCalled();
  });

  it("creates a polygon selection and applies a transparent cutout mask", () => {
    class FakeImage {
      naturalWidth = 400;
      naturalHeight = 300;
      set src(_value) { this.onload(); }
    }
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const { container } = render(<ManualImageEditor />);
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [new File(["image"], "cutout.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: /连线抠图/ }));
    const canvas = screen.getByLabelText("图片编辑画布");
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 400, height: 300, right: 400, bottom: 300, x: 0, y: 0, toJSON() {} });
    const overlay = container.querySelector(".manual-cutout-overlay");
    fireEvent.pointerDown(overlay, { clientX: 40, clientY: 40, button: 0, pointerId: 1 });
    fireEvent.pointerDown(overlay, { clientX: 300, clientY: 40, button: 0, pointerId: 1 });
    fireEvent.pointerDown(overlay, { clientX: 180, clientY: 240, button: 0, pointerId: 1 });
    fireEvent.keyDown(window, { key: "Enter" });

    expect(screen.getByText("已闭合 · 3 个锚点")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "保留选区" }));
    expect(context.clip).toHaveBeenCalled();
    expect(context.clearRect).toHaveBeenCalledWith(0, 0, 400, 300);
    expect(screen.getByRole("combobox", { name: "格式" }).value).toBe("image/png");
  });

  it("zooms around the pointer and temporarily pans while polygon cutout stays active", () => {
    class FakeImage {
      naturalWidth = 400;
      naturalHeight = 300;
      set src(_value) { this.onload(); }
    }
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const { container } = render(<ManualImageEditor />);
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [new File(["image"], "corner.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: /连线抠图/ }));
    const stage = container.querySelector(".manual-stage");
    const position = container.querySelector(".manual-canvas-position");
    vi.spyOn(stage, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 1000, height: 800, right: 1000, bottom: 800, x: 0, y: 0, toJSON() {} });

    fireEvent.wheel(stage, { clientX: 900, clientY: 100, deltaY: -100 });
    expect(screen.getByText("11%")).toBeTruthy();
    expect(position.style.transform).toContain("+ -40");
    expect(position.style.transform).toContain("+ 30px");

    const overlay = container.querySelector(".manual-cutout-overlay");
    overlay.setPointerCapture = vi.fn();
    overlay.hasPointerCapture = vi.fn(() => true);
    overlay.releasePointerCapture = vi.fn();
    fireEvent.keyDown(window, { key: " ", code: "Space" });
    expect(stage.className).toContain("is-temporary-pan");
    fireEvent.pointerDown(overlay, { clientX: 100, clientY: 100, button: 0, pointerId: 7 });
    fireEvent.pointerMove(overlay, { clientX: 150, clientY: 130, pointerId: 7 });
    fireEvent.pointerUp(overlay, { clientX: 150, clientY: 130, pointerId: 7 });
    const offsetMatch = position.style.transform.match(/\+ (-?[\d.]+)px\).*\+ (-?[\d.]+)px/);
    expect(Number(offsetMatch[1])).toBeCloseTo(10);
    expect(Number(offsetMatch[2])).toBeCloseTo(60);
    fireEvent.keyUp(window, { key: " ", code: "Space" });
    expect(stage.className).not.toContain("is-temporary-pan");
    expect(screen.getByText(/空格 \/ Alt \/ 中键拖动画布/)).toBeTruthy();
  });

  it("refuses a self-intersecting cutout after closed anchors are adjusted", () => {
    class FakeImage {
      naturalWidth = 400;
      naturalHeight = 300;
      set src(_value) { this.onload(); }
    }
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const { container } = render(<ManualImageEditor />);
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [new File(["image"], "crossing.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: /连线抠图/ }));
    const canvas = screen.getByLabelText("图片编辑画布");
    vi.spyOn(canvas, "getBoundingClientRect").mockReturnValue({ left: 0, top: 0, width: 400, height: 300, right: 400, bottom: 300, x: 0, y: 0, toJSON() {} });
    const overlay = container.querySelector(".manual-cutout-overlay");
    overlay.setPointerCapture = vi.fn();
    overlay.hasPointerCapture = vi.fn(() => true);
    overlay.releasePointerCapture = vi.fn();
    [[40, 40], [300, 40], [300, 240], [40, 240]].forEach(([clientX, clientY]) => fireEvent.pointerDown(overlay, { clientX, clientY, button: 0, pointerId: 3 }));
    fireEvent.keyDown(window, { key: "Enter" });
    fireEvent.pointerDown(overlay, { clientX: 300, clientY: 40, button: 0, pointerId: 3 });
    fireEvent.pointerMove(overlay, { clientX: 180, clientY: 280, pointerId: 3 });
    fireEvent.pointerUp(overlay, { clientX: 180, clientY: 280, pointerId: 3 });
    fireEvent.click(screen.getByRole("button", { name: "保留选区" }));
    expect(screen.getByRole("alert").textContent).toContain("选区边线发生交叉");
    expect(context.clip).not.toHaveBeenCalled();
  });

  it("expands the canvas and submits a real local ComfyUI inpaint workflow", async () => {
    class FakeImage {
      naturalWidth = 640;
      naturalHeight = 360;
      set src(_value) { this.onload(); }
    }
    vi.stubGlobal("Image", FakeImage);
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const workflow = {
      id: "local-inpaint",
      name: "真实修补工作流",
      capabilities: { inpaint: true },
      defaults: { steps: 20, cfg: 5, randomSeed: true },
      definition: { 1: { class_type: "LoadImage", inputs: { image: "input.png" } } },
      binding: { fields: { sourceImage: { nodeId: "1", input: "image" } }, outputNode: { nodeId: "9" } },
    };
    const fetchMock = vi.fn(async (url, options = {}) => {
      const path = String(url);
      if (path.endsWith("/api/config")) return { ok: true, json: async () => ({ token: "token" }) };
      if (path.endsWith("/api/comfy/status")) return { ok: true, json: async () => ({ running: true }) };
      if (path.endsWith("/api/local-workflows")) return { ok: true, json: async () => ({ workflows: [workflow] }) };
      if (path.endsWith("/api/generate")) {
        expect(options.method).toBe("POST");
        expect(options.body.get("workflowId")).toBe("local-inpaint");
        expect(options.body.get("sourceImage")).toBeInstanceOf(File);
        return { ok: true, json: async () => ({ promptId: "prompt-1", finalOutputNodeId: "9" }) };
      }
      if (path.includes("/comfy/history/prompt-1")) return { ok: true, json: async () => ({ "prompt-1": { status: { status_str: "success", completed: true }, outputs: { 9: { images: [{ filename: "result.png", type: "output" }] } } } }) };
      if (path.includes("/comfy/view?")) return { ok: true, blob: async () => new Blob(["result"], { type: "image/png" }) };
      throw new Error(`unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const { container } = render(<ManualImageEditor />);
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [new File(["image"], "scene.png", { type: "image/png" })] } });
    await screen.findByText("已发现 1 个真实修补工作流");

    fireEvent.click(screen.getByRole("button", { name: "向右扩图" }));
    expect(screen.getByText("800 × 360 px")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("AI 正向提示词"), { target: { value: "延伸自然的草地" } });
    fireEvent.click(screen.getByRole("button", { name: "开始 AI 编辑" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/api/generate"), expect.objectContaining({ method: "POST" })), { timeout: 2500 });
    await screen.findByText("AI 编辑完成，结果已回填画布，可继续手动编辑或撤销", {}, { timeout: 4000 });
  });
});
