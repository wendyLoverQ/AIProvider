import { describe, expect, it } from "vitest";
import { addText, addTrack, createProject, duplicateElement, insertAsset, projectDuration, removeTrack, resizeElement, snapTime, splitElement, updateElement } from "./videoEditorModel";

const asset = { id: "asset-1", name: "demo.mp4", kind: "video", duration: 10 };

describe("video editor timeline model", () => {
  it("inserts media on the OpenCut-style main track", () => {
    const project = insertAsset(createProject(), asset, 2);
    expect(project.tracks.find((track) => track.type === "main").elements[0]).toMatchObject({ start: 2, duration: 10, trimStart: 0 });
    expect(projectDuration(project)).toBe(12);
  });

  it("splits visible and source spans consistently", () => {
    let project = insertAsset(createProject(), asset, 0);
    const id = project.tracks.find((track) => track.type === "main").elements[0].id;
    project = updateElement(project, id, { speed: 2, trimStart: 1 });
    const result = splitElement(project, id, 4);
    const [left, right] = result.project.tracks.find((track) => track.type === "main").elements;
    expect(left.duration).toBe(4);
    expect(right.duration).toBe(6);
    expect(right.trimStart).toBe(9);
  });

  it("adds a text overlay and snaps to clip boundaries", () => {
    let project = insertAsset(createProject(), asset, 0);
    project = addText(project, "标题", 3).project;
    expect(project.tracks.find((track) => track.type === "overlay").elements[0].text).toBe("标题");
    expect(snapTime(project, 10.08, "missing")).toBe(10);
  });

  it("trims from both sides while preserving source bounds", () => {
    let project = insertAsset(createProject(), asset, 0);
    const id = project.tracks.find((track) => track.type === "main").elements[0].id;
    project = resizeElement(project, id, "left", 2);
    expect(project.tracks.find((track) => track.type === "main").elements[0]).toMatchObject({ start: 2, duration: 8, trimStart: 2 });
    project = resizeElement(project, id, "right", -3);
    expect(project.tracks.find((track) => track.type === "main").elements[0]).toMatchObject({ duration: 5, trimEnd: 3 });
  });

  it("duplicates clips and manages secondary tracks", () => {
    let project = insertAsset(createProject(), asset, 0);
    const id = project.tracks.find((track) => track.type === "main").elements[0].id;
    const duplicated = duplicateElement(project, id);
    expect(projectDuration(duplicated.project)).toBe(20);
    project = addTrack(duplicated.project, "overlay");
    const extra = project.tracks.find((track) => track.name === "叠加轨 2");
    expect(extra).toBeTruthy();
    expect(removeTrack(project, extra.id).tracks.filter((track) => track.type === "overlay")).toHaveLength(1);
  });
});
