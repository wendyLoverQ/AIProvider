// @vitest-environment jsdom
import React from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DynamicShowcase, { showImages } from "./DynamicShowcase";

describe("DynamicShowcase", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("keeps the main image selected when clicked and hides direct selectors", () => {
    vi.useFakeTimers();
    render(<DynamicShowcase />);
    expect(screen.getByRole("img", { name: showImages[0].name })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "按住持续放大轮播图片" }));
    expect(screen.getByRole("img", { name: showImages[0].name })).toBeTruthy();
    expect(screen.queryByRole("group", { name: "轮播图片选择" })).toBeNull();
  });

  it("plays the side bounce before selecting that image", () => {
    vi.useFakeTimers();
    render(<DynamicShowcase />);
    const next = screen.getByRole("button", { name: "查看下一张轮播图片" });
    fireEvent.click(next);
    expect(next.classList.contains("is-bouncing")).toBe(true);
    expect(screen.getByRole("img", { name: showImages[0].name })).toBeTruthy();
    act(() => vi.advanceTimersByTime(360));
    expect(screen.getByRole("img", { name: showImages[1].name })).toBeTruthy();
  });

  it("slowly enlarges while held and does not advance after a long press", () => {
    vi.useFakeTimers();
    render(<DynamicShowcase />);
    const main = screen.getByRole("button", { name: "按住持续放大轮播图片" });
    fireEvent.pointerDown(main, { button: 0 });
    expect(main.classList.contains("is-holding")).toBe(true);
    act(() => vi.advanceTimersByTime(600));
    fireEvent.pointerUp(main, { button: 0 });
    fireEvent.click(main);
    expect(main.classList.contains("is-releasing")).toBe(true);
    expect(screen.getByRole("img", { name: showImages[0].name })).toBeTruthy();
  });

  it("pauses automatic rotation for the entire time the main image is being enlarged", () => {
    vi.useFakeTimers();
    render(<DynamicShowcase />);
    const main = screen.getByRole("button", { name: "按住持续放大轮播图片" });
    fireEvent.pointerDown(main, { button: 0 });
    act(() => vi.advanceTimersByTime(13500));
    expect(screen.getByRole("img", { name: showImages[0].name })).toBeTruthy();
    fireEvent.pointerUp(main, { button: 0 });
    act(() => vi.advanceTimersByTime(4500));
    expect(screen.getByRole("img", { name: showImages[1].name })).toBeTruthy();
  });
});
