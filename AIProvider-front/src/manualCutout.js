export function distance(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

export function projectToSegment(point, start, end) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSquared = dx * dx + dy * dy;
  if (!lengthSquared) return { point: { ...start }, distance: distance(point, start), t: 0 };
  const t = Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared));
  const projected = { x: start.x + dx * t, y: start.y + dy * t };
  return { point: projected, distance: distance(point, projected), t };
}

export function nearestAnchor(points, point, tolerance) {
  let match = null;
  points.forEach((anchor, index) => {
    const candidateDistance = distance(anchor, point);
    if (candidateDistance <= tolerance && (!match || candidateDistance < match.distance)) {
      match = { index, distance: candidateDistance };
    }
  });
  return match;
}

export function nearestSegment(points, point, tolerance) {
  if (points.length < 2) return null;
  let match = null;
  points.forEach((start, index) => {
    const end = points[(index + 1) % points.length];
    const projected = projectToSegment(point, start, end);
    if (projected.distance <= tolerance && projected.t > 0.02 && projected.t < 0.98 && (!match || projected.distance < match.distance)) {
      match = { index, ...projected };
    }
  });
  return match;
}

function orientation(a, b, c) {
  return Math.sign((b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y));
}

function segmentsIntersect(a, b, c, d) {
  return orientation(a, b, c) !== orientation(a, b, d) && orientation(c, d, a) !== orientation(c, d, b);
}

export function isSimplePolygon(points) {
  if (points.length < 3) return false;
  for (let first = 0; first < points.length; first += 1) {
    const firstEnd = (first + 1) % points.length;
    for (let second = first + 1; second < points.length; second += 1) {
      const secondEnd = (second + 1) % points.length;
      if (first === second || firstEnd === second || secondEnd === first) continue;
      if (segmentsIntersect(points[first], points[firstEnd], points[second], points[secondEnd])) return false;
    }
  }
  return true;
}
