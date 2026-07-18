// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MonitorCenter from "./MonitorCenter";

const json = (data) => new Response(JSON.stringify({ code: 200, data }), { headers: { "Content-Type": "application/json" } });

describe("MonitorCenter request chart", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async (input) => {
      const url = String(input);
      if (url.endsWith("/cloud-servers")) return json({
        aws: { displayName: "AWS 东京", status: "UP", collectedAt: "2026-07-15T02:00:00+08:00", memory: { available: true, usedBytes: 1, totalBytes: 2 }, disk: { available: true, usedBytes: 1, totalBytes: 2 }, network: { available: true, inboundBytesPerSecond: 1048576, outboundBytesPerSecond: 524288, monthInboundBytes: 100, monthOutboundBytes: 200 }, traffic: { available: true, status: "CLOUDWATCH_API_AWS_100GB_FREE_DTO", usedBytes: 200, totalBytes: 100000000000 }, instance: { instanceId: "i-aws", instanceType: "c7i-flex.large", availabilityZone: "ap-northeast-1c", publicIpv4: "35.78.120.126", awsApiStatus: "CLOUDWATCH_API_AVAILABLE" } },
        tencent: { displayName: "腾讯云", status: "OFFLINE", collectedAt: "2026-07-15T02:00:00+08:00", memory: { available: false }, disk: { available: false }, network: { available: false }, traffic: { available: false }, instance: { instanceId: "lhins-test", publicIpv4: "124.222.185.195", awsApiStatus: "NOT_APPLICABLE" } }
      });
      if (url.endsWith("/ai-overview")) return json({ totalRequests: 5, successRate: 80, failureCount: 1, p95DurationMs: 420 });
      if (url.endsWith("/aws-billing")) return json({ collectedAt: "2026-07-15T02:00:00+08:00", plan: { available: true, type: "FREE", status: "ACTIVE", remainingCredits: 90, currency: "USD" }, cost: { available: true, netUnblendedCost: 1.25, currency: "USD", estimated: true }, credits: { available: true, remainingAmount: 90, currency: "USD", items: [] }, freeTier: { available: true, items: [{ service: "AmazonEBS", description: "EBS storage", actual: 20, limit: 30, unit: "GB-Mo", usagePercent: 66.7 }] } });
      return json([{ bucket: "2026-07-15T02:00:00+08:00", totalRequests: 5, errorRate: 20, avgDurationMs: 180, p95DurationMs: 420 }]);
    }));
  });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("shows a compact styled tooltip on hover instead of a native title", async () => {
    render(<MonitorCenter />);
    const point = await screen.findByLabelText(/请求 5/);
    expect(point.getAttribute("title")).toBeNull();
    fireEvent.mouseEnter(point);
    await waitFor(() => expect(screen.getByText("小时统计")).toBeTruthy());
    expect(screen.getByText("平均响应")).toBeTruthy();
    expect(screen.getByText("P95 响应", { selector: "dt" })).toBeTruthy();
  });

  it("switches between AWS and Tencent without removing the stopped Tencent monitor", async () => {
    render(<MonitorCenter />);
    expect(await screen.findByText("AWS 东京健康状态")).toBeTruthy();
    expect(screen.getByText(/c7i-flex.large/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "腾讯云" }));
    expect(await screen.findByText("腾讯云健康状态")).toBeTruthy();
    expect(screen.getByText("OFFLINE")).toBeTruthy();
  });

  it("shows real AWS plan, cost, credits and free-tier usage", async () => {
    render(<MonitorCenter />);
    expect(await screen.findByText("FREE · ACTIVE")).toBeTruthy();
    expect(screen.getByText("本月净费用（预估）")).toBeTruthy();
    expect(screen.getByText(/1\.25/)).toBeTruthy();
    expect(screen.getByText(/90\.00/)).toBeTruthy();
    fireEvent.click(screen.getByText("免费额度用量明细"));
    expect(screen.getByText("EBS storage")).toBeTruthy();
  });
});
