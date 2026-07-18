const splitPrompt = (value) => String(value || "").split(/[,，\n]+/).map((part) => part.trim()).filter(Boolean);

function weightedFragments(pool, key) {
  const frequencies = new Map();
  for (const row of pool || []) {
    const weight = Math.max(1, Number(row.weight) || 1);
    for (const fragment of splitPrompt(row[key])) {
      const normalized = fragment.toLocaleLowerCase("en-US");
      const current = frequencies.get(normalized);
      frequencies.set(normalized, { text: current?.text || fragment, weight: (current?.weight || 0) + weight });
    }
  }
  return [...frequencies.values()];
}

function pickWeighted(candidates, minimum, maximum, random) {
  const available = [...candidates];
  const target = Math.min(available.length, minimum + Math.floor(random() * (maximum - minimum + 1)));
  const picked = [];
  while (picked.length < target && available.length) {
    const total = available.reduce((sum, item) => sum + item.weight, 0);
    let cursor = random() * total;
    let index = available.length - 1;
    for (let candidateIndex = 0; candidateIndex < available.length; candidateIndex += 1) {
      cursor -= available[candidateIndex].weight;
      if (cursor < 0) { index = candidateIndex; break; }
    }
    picked.push(available.splice(index, 1)[0].text);
  }
  return picked;
}

function mergePrompt(current, additions) {
  const seen = new Set();
  return [...splitPrompt(current), ...additions].filter((fragment) => {
    const normalized = fragment.toLocaleLowerCase("en-US");
    if (seen.has(normalized)) return false;
    seen.add(normalized);
    return true;
  }).join(", ");
}

export function buildLuckyPrompts(currentPositive, currentNegative, pool, random = Math.random) {
  const positive = pickWeighted(weightedFragments(pool, "prompt"), 3, 6, random);
  const negative = pickWeighted(weightedFragments(pool, "negativePrompt"), 2, 4, random);
  if (!positive.length && !negative.length) throw new Error("我的资产中还没有可用于手气不错的 Prompt");
  return {
    positivePrompt: mergePrompt(currentPositive, positive),
    negativePrompt: mergePrompt(currentNegative, negative),
    positiveAdditions: positive,
    negativeAdditions: negative,
  };
}
