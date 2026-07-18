// @vitest-environment jsdom
import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useProjectHistory } from "./useProjectHistory";

describe("project history", () => {
  it("undoes and redoes committed timeline changes", () => {
    const { result } = renderHook(() => useProjectHistory({ version: 1, name: "A", tracks: [], assets: [] }));
    act(() => result.current.commitProject({ version: 1, name: "B", tracks: [], assets: [] }));
    expect(result.current.project.name).toBe("B");
    expect(result.current.canUndo).toBe(true);
    act(() => result.current.undo());
    expect(result.current.project.name).toBe("A");
    expect(result.current.canRedo).toBe(true);
    act(() => result.current.redo());
    expect(result.current.project.name).toBe("B");
  });

  it("records a drag transaction as one history entry", () => {
    const start = { version: 1, name: "A", tracks: [], assets: [] };
    const { result } = renderHook(() => useProjectHistory(start));
    act(() => result.current.replaceProject({ ...start, name: "Dragged" }));
    act(() => result.current.checkpoint(start));
    act(() => result.current.undo());
    expect(result.current.project.name).toBe("A");
  });
});
