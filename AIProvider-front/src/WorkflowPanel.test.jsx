// @vitest-environment jsdom
import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import WorkflowPanel from "./WorkflowPanel";

describe("WorkflowPanel generation action", () => {
  afterEach(cleanup);

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
});
