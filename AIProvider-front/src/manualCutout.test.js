import { describe, expect, it } from "vitest";
import { isSimplePolygon, nearestAnchor, nearestSegment, projectToSegment } from "./manualCutout";

describe("manual cutout geometry", () => {
  it("projects pointer positions to the closest line segment", () => {
    expect(projectToSegment({ x: 4, y: 3 }, { x: 0, y: 0 }, { x: 10, y: 0 })).toEqual({
      point: { x: 4, y: 0 }, distance: 3, t: 0.4,
    });
  });

  it("finds anchors and insertion segments within tolerance", () => {
    const points = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }];
    expect(nearestAnchor(points, { x: 1, y: 1 }, 2)?.index).toBe(0);
    expect(nearestSegment(points, { x: 5, y: 1 }, 2)?.index).toBe(0);
  });

  it("rejects self-intersecting polygons", () => {
    expect(isSimplePolygon([{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }])).toBe(true);
    expect(isSimplePolygon([{ x: 0, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }, { x: 10, y: 0 }])).toBe(false);
  });
});
