import { buildPromptTranslations, restorePromptFromChinese, translatePromptToChinese } from "./promptComposer";

const serviceCache = new WeakMap();

export function getPromptTranslationService(options = []) {
  if (serviceCache.has(options)) return serviceCache.get(options);
  const translations = {
    positivePrompt: buildPromptTranslations(options, "positivePrompt"),
    negativePrompt: buildPromptTranslations(options, "negativePrompt"),
  };
  const service = Object.freeze({
    translations: (field) => translations[field] || translations.positivePrompt,
    toChinese: (field, prompt) => translatePromptToChinese(prompt, translations[field] || translations.positivePrompt),
    toOriginal: (field, prompt) => restorePromptFromChinese(prompt, translations[field] || translations.positivePrompt),
  });
  serviceCache.set(options, service);
  return service;
}
