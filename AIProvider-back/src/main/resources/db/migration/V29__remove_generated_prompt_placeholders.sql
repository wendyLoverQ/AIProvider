UPDATE c_LocalGeneratedImages
SET Prompt = NULL
WHERE Prompt IN ('本机生成图片', '本机历史图片', '已迁移图片资产');

UPDATE c_LocalGeneratedImages
SET NegativePrompt = NULL
WHERE NegativePrompt = '未记录';

UPDATE c_GeneratedAssets
SET Prompt = NULL
WHERE Prompt IN ('本机生成图片', '本机历史图片', '已迁移图片资产');

UPDATE c_GeneratedAssets
SET NegativePrompt = NULL
WHERE NegativePrompt = '未记录';
