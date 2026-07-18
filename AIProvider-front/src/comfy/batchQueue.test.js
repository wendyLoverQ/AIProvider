import { describe, expect, it } from "vitest";
import { canSubmitToComfyQueue, COMFY_QUEUE_LIMIT, countComfyQueue } from "./batchQueue";

describe("Comfy queue limit", () => {
  it("never opens another submission slot at 20 in-flight tasks", () => {
    const queue = { queue_running: [[0]], queue_pending: Array.from({ length: 19 }, (_, index) => [index + 1]) };
    expect(countComfyQueue(queue)).toBe(COMFY_QUEUE_LIMIT);
    expect(canSubmitToComfyQueue(queue)).toBe(false);
  });

  it("opens a slot when the in-flight count drops below 20", () => {
    expect(canSubmitToComfyQueue({ queue_running: [[0]], queue_pending: Array.from({ length: 18 }, () => []) })).toBe(true);
  });
});
