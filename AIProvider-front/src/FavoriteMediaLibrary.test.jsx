// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import FavoriteMediaLibrary from "./FavoriteMediaLibrary";

const item = { id: 7, title: "星夜海岸", originalFileName: "coast.png", mediaType: "image", contentType: "image/png", fileSize: 4096, width: 1920, height: 1080, contentUrl: "/api/favorites/7/content", thumbnailUrl: "/api/favorites/7/thumbnail", createdAt: "2026-07-20T01:00:00" };
const jsonResponse = (data) => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(data) });

beforeEach(() => {
  vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue({ width: 1920, height: 1080, close: vi.fn() }));
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({ fillStyle: "", filter: "", fillRect: vi.fn(), drawImage: vi.fn(), save: vi.fn(), restore: vi.fn() });
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation((callback) => callback(new Blob(["wallpaper"], { type: "image/png" })));
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe("FavoriteMediaLibrary", () => {
  it("deletes one or multiple favorites by their IDs", async () => {
    const items = [item, { ...item, id: 8, title: "晨雾" }, { ...item, id: 9, title: "晚霞" }];
    const deleteBodies = [];
    vi.spyOn(globalThis, "fetch").mockImplementation((url, options = {}) => {
      if (url === "/api/favorites?page=1&pageSize=100") return jsonResponse({ code: 200, data: { items, total: items.length } });
      if (url === "/api/favorites" && options.method === "DELETE") {
        deleteBodies.push(JSON.parse(options.body));
        return jsonResponse({ code: 200, data: { deleted: deleteBodies.at(-1).ids.length } });
      }
      throw new Error(`unexpected request: ${url}`);
    });
    render(<FavoriteMediaLibrary />);
    await screen.findByText("星夜海岸");

    expect(screen.queryByRole("button", { name: "删除 星夜海岸" })).toBeNull();
    expect(screen.queryByRole("button", { name: "星夜海岸 更多操作" })).toBeNull();
    fireEvent.contextMenu(screen.getByRole("button", { name: "预览 星夜海岸" }), { clientX: 200, clientY: 200 });
    fireEvent.click(screen.getByRole("button", { name: "从我的最爱移除" }));
    expect(screen.getByRole("dialog", { name: "确认删除" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(deleteBodies).toEqual([{ ids: [7] }]));

    fireEvent.click(screen.getByRole("button", { name: "批量删除" }));
    expect(document.querySelector(".favorite-select")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "选择 晨雾" }));
    fireEvent.click(screen.getByRole("button", { name: "选择 晚霞" }));
    fireEvent.click(screen.getByRole("button", { name: "删除已选 2" }));
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(deleteBodies).toEqual([{ ids: [7] }, { ids: [8, 9] }]));
  });

  it("uploads image files dropped anywhere on the page", async () => {
    const uploaded = { ...item, id: 8, title: "拖入图片" };
    vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
      if (url === "/api/favorites?page=1&pageSize=100") return jsonResponse({ code: 200, data: { items: [], total: 0 } });
      throw new Error(`unexpected request: ${url}`);
    });
    const sendUpload = vi.fn(); let finishUpload;
    vi.stubGlobal("XMLHttpRequest", class {
      constructor() {
        this.listeners = {}; this.uploadListeners = {};
        this.upload = { addEventListener: (type, listener) => { this.uploadListeners[type] = listener; } };
      }
      open(method, url) { this.method = method; this.url = url; }
      addEventListener(type, listener) { this.listeners[type] = listener; }
      send(form) {
        sendUpload(form);
        const file = form.get("file");
        this.uploadListeners.progress?.({ lengthComputable: true, loaded: Math.ceil(file.size / 2), total: file.size });
        finishUpload = () => { this.status = 200; this.response = { code: 200, data: uploaded }; this.listeners.load?.(); };
      }
    });
    render(<FavoriteMediaLibrary />);
    await screen.findByText("这里还没有喜欢的画面");
    const file = new File(["image"], "拖入图片.png", { type: "image/png" });
    const dataTransfer = { types: ["Files"], files: [file], dropEffect: "none" };
    fireEvent.dragEnter(window, { dataTransfer });
    expect(screen.getByRole("status", { name: "拖放上传区域" })).toBeTruthy();
    fireEvent.dragOver(window, { dataTransfer });
    expect(dataTransfer.dropEffect).toBe("copy");
    fireEvent.drop(window, { dataTransfer });
    expect(screen.getByRole("dialog", { name: "确认拖放上传" })).toBeTruthy();
    expect(sendUpload).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认上传" }));
    await waitFor(() => expect(sendUpload).toHaveBeenCalledOnce());
    expect(screen.getByRole("progressbar").getAttribute("value")).toBe("3");
    await act(async () => { finishUpload(); });
    expect(await screen.findByText("已保存 1 个媒体到服务器")).toBeTruthy();
    expect(screen.queryByRole("status", { name: "拖放上传区域" })).toBeNull();
  });

  it("loads the server gallery and filters it through the shared search field", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
      if (url === "/api/favorites?page=1&pageSize=100") return jsonResponse({ code: 200, data: { items: [item], total: 1 } });
      throw new Error(`unexpected request: ${url}`);
    });
    render(<FavoriteMediaLibrary />);
    expect(await screen.findByText("星夜海岸")).toBeTruthy();
    const search = screen.getByRole("textbox", { name: "搜索我的最爱" });
    fireEvent.change(search, { target: { value: "不存在" } });
    expect(screen.getByText("没有找到匹配的媒体")).toBeTruthy();
    fireEvent.change(search, { target: { value: "海岸" } });
    expect(screen.getByText("星夜海岸")).toBeTruthy();
  });

  it("offers every detected display and uploads a monitor-sized smart wallpaper to the Bridge", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((url, options = {}) => {
      if (url === "/api/favorites?page=1&pageSize=100") return jsonResponse({ code: 200, data: { items: [item], total: 1 } });
      if (url === "http://127.0.0.1:32145/api/config") return jsonResponse({ success: true, token: "local-token" });
      if (url === "http://127.0.0.1:32145/api/wallpaper/monitors") return jsonResponse({ success: true, monitors: [
        { id: "one", number: 1, label: "显示器 1", width: 1920, height: 1080, primary: true },
        { id: "two", number: 2, label: "显示器 2", width: 1080, height: 1920, primary: false },
        { id: "three", number: 3, label: "显示器 3", width: 2560, height: 1440, primary: false },
      ] });
      if (url === "/api/favorites/7/content") return Promise.resolve({ ok: true, blob: () => Promise.resolve(new Blob(["source"], { type: "image/png" })) });
      if (url === "http://127.0.0.1:32145/api/wallpaper/apply") {
        expect(options.headers["X-Local-Token"]).toBe("local-token");
        expect(options.body.get("monitorId")).toBe("two");
        expect(options.body.get("file").type).toBe("image/png");
        return jsonResponse({ success: true, monitorId: "two" });
      }
      throw new Error(`unexpected request: ${url}`);
    });
    render(<FavoriteMediaLibrary />);
    const image = await screen.findByRole("button", { name: "预览 星夜海岸" });
    fireEvent.contextMenu(image, { clientX: 200, clientY: 200 });
    fireEvent.click(screen.getByRole("button", { name: "应用为壁纸" }));
    expect(await screen.findByText("显示器 3")).toBeTruthy();
    fireEvent.click(screen.getByText("显示器 2"));
    expect(screen.getByText("方向不一致时使用模糊扩展背景，保留完整主体")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "应用壁纸" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:32145/api/wallpaper/apply", expect.any(Object)));
    expect(await screen.findByText("已应用到显示器 2")).toBeTruthy();
  });
});
