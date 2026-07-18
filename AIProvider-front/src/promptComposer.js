const CATEGORY_LABELS = {
  Appearance: "外貌",
  Character: "人物",
  Special: "特殊",
  Clothing: "服装",
  Artist: "画师",
  Pose: "姿势",
  Expression: "表情",
  Camera: "镜头",
  Hair: "头发",
  Relationship: "人物关系",
  Action: "行为",
  Eyes: "眼睛",
  Background: "背景",
  Lighting: "光照",
  Composition: "构图",
  Style: "风格",
  Quality: "画质词",
};

const CATEGORY_ORDER = Object.keys(CATEGORY_LABELS);

export const PROMPT_CATEGORIES = CATEGORY_ORDER.map((category) => ({
  key: category,
  category,
  label: CATEGORY_LABELS[category],
  multiple: true,
}));

export function buildPromptCategories(options = []) {
  const grouped = new Map();
  for (const option of options) {
    if (!option?.category) continue;
    const current = grouped.get(option.category);
    grouped.set(option.category, current === undefined ? Boolean(option.allowMultiple) : current || Boolean(option.allowMultiple));
  }
  const categories = [...grouped.keys()].sort((left, right) => {
    const leftIndex = CATEGORY_ORDER.indexOf(left);
    const rightIndex = CATEGORY_ORDER.indexOf(right);
    return (leftIndex < 0 ? Number.MAX_SAFE_INTEGER : leftIndex) - (rightIndex < 0 ? Number.MAX_SAFE_INTEGER : rightIndex) || left.localeCompare(right);
  });
  if (!categories.length) return PROMPT_CATEGORIES;
  return categories.map((category) => ({ key: category, category, label: CATEGORY_LABELS[category] || category, multiple: grouped.get(category) }));
}

export function emptySelectedOptions(definitions = PROMPT_CATEGORIES) {
  return Object.fromEntries(definitions.map(({ key }) => [key, []]));
}

export function normalizePrompt(...parts) {
  const seen = new Set();
  const terms = [];
  parts.flatMap((part) => String(part || "").split(",")).forEach((part) => {
    const term = part.trim().replace(/\s+/g, " ");
    const identity = term.toLowerCase();
    if (!term || seen.has(identity)) return;
    seen.add(identity); terms.push(term);
  });
  return terms.join(", ");
}

function promptContainsTerm(prompt, term) {
  const source = String(prompt || "").toLocaleLowerCase("en-US");
  const target = String(term || "").trim().toLocaleLowerCase("en-US");
  if (!target) return false;
  const escaped = target.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return /[a-z0-9]/i.test(target) ? new RegExp(`(^|[^a-z0-9])${escaped}(?=$|[^a-z0-9])`, "i").test(source) : source.includes(target);
}

export function relatedNegativePromptsForPositive(prompt, options = []) {
  return options.filter((option) => String(option.negativePrompt || "").trim() &&
    String(option.positivePrompt || "").split(",").some((term) => promptContainsTerm(prompt, term)))
    .map((option) => option.negativePrompt);
}

export function extractPositiveExtra(prompt, selectedOptions, options = []) {
  const byId = new Map(options.map((option) => [option.id, option]));
  const structuredTerms = new Set(Object.values(selectedOptions || {}).flat()
    .flatMap((id) => String(byId.get(id)?.positivePrompt || "").split(","))
    .map((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US"))
    .filter(Boolean));
  const manualTerms = String(prompt || "").split(",")
    .map((term) => term.trim().replace(/\s+/g, " "))
    .filter((term) => term && !structuredTerms.has(term.toLocaleLowerCase("en-US")));
  return normalizePrompt(manualTerms);
}

export function extractNegativeExtra(prompt, selectedOptions, options = [], generalNegativePrompt = "") {
  const byId = new Map(options.map((option) => [option.id, option]));
  const generatedTerms = new Set([
    ...String(generalNegativePrompt || "").split(","),
    ...Object.values(selectedOptions || {}).flat().flatMap((id) => String(byId.get(id)?.negativePrompt || "").split(",")),
  ].map((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US")).filter(Boolean));
  return normalizePrompt(String(prompt || "").split(",")
    .map((term) => term.trim().replace(/\s+/g, " "))
    .filter((term) => term && !generatedTerms.has(term.toLocaleLowerCase("en-US"))));
}

export function composePrompts(selectedOptions, options, generalNegativePrompt, positiveExtra = "", negativeExtra = "", definitions = buildPromptCategories(options)) {
  const byId = new Map((options || []).map((option) => [option.id, option]));
  const prompts = (key, field) => (selectedOptions?.[key] || []).map((id) => byId.get(id)?.[field]).filter(Boolean);
  return {
    positivePrompt: normalizePrompt(...definitions.flatMap(({ key }) => prompts(key, "positivePrompt")), positiveExtra),
    negativePrompt: normalizePrompt(generalNegativePrompt, ...definitions.flatMap(({ key }) => prompts(key, "negativePrompt")), negativeExtra),
  };
}

export function normalizeSelectedOptions(value, definitions = PROMPT_CATEGORIES) {
  const empty = emptySelectedOptions(definitions);
  for (const { key, multiple } of definitions) {
    const ids = Array.isArray(value?.[key]) ? [...new Set(value[key].filter((id) => typeof id === "string" && id))] : [];
    empty[key] = multiple ? ids : ids.slice(0, 1);
  }
  return empty;
}

export function matchSelectedOptionsFromPrompt(prompt, options = [], definitions = buildPromptCategories(options)) {
  const promptTerms = new Set(String(prompt || "").split(",")
    .map((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US"))
    .filter(Boolean));
  const selected = emptySelectedOptions(definitions);
  for (const option of options) {
    const matched = String(option.positivePrompt || "").split(",")
      .map((term) => term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US"))
      .some((term) => term && promptTerms.has(term));
    if (!matched || !selected[option.category]) continue;
    selected[option.category] = option.allowMultiple === false ? [option.id] : [...selected[option.category], option.id];
  }
  return selected;
}

export function buildPromptTranslations(options = [], field = "positivePrompt") {
  const englishToChinese = new Map();
  const chineseToEnglish = new Map();
  for (const option of options) {
    const english = String(option[field] || "").trim();
    const chinese = String(option.name || "").trim();
    if (!english || !chinese) continue;
    const terms = english.split(",").map((term) => term.trim().replace(/\s+/g, " ")).filter(Boolean);
    for (const term of terms) {
      const key = term.trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US");
      const label = terms.length === 1 ? chinese : `${chinese}（${term}）`;
      const chineseKey = label.toLocaleLowerCase("zh-CN");
      if (key && !englishToChinese.has(key)) englishToChinese.set(key, label);
      if (!chineseToEnglish.has(chineseKey)) chineseToEnglish.set(chineseKey, term);
    }
  }
  return { englishToChinese, chineseToEnglish };
}

export function translatePromptToChinese(prompt, translations) {
  return normalizePrompt(String(prompt || "").split(",").map((term) => {
    const clean = term.trim().replace(/\s+/g, " ");
    return translations.englishToChinese.get(clean.toLocaleLowerCase("en-US")) || clean;
  }));
}

export function restorePromptFromChinese(prompt, translations) {
  return normalizePrompt(String(prompt || "").split(",").map((term) => {
    const clean = term.trim().replace(/\s+/g, " ");
    return translations.chineseToEnglish.get(clean.toLocaleLowerCase("zh-CN")) || clean;
  }));
}
