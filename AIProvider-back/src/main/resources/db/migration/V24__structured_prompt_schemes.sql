DELETE FROM c_ComfyParameterSchemes;

ALTER TABLE c_ComfyParameterSchemes
  DROP COLUMN Title,
  DROP COLUMN ParametersJson,
  DROP COLUMN OutputFolder,
  DROP COLUMN Notes,
  ADD COLUMN Name VARCHAR(100) NOT NULL AFTER Id,
  ADD COLUMN SelectedOptionsJson JSON NOT NULL AFTER Name,
  ADD COLUMN PositiveExtra TEXT NOT NULL AFTER SelectedOptionsJson,
  ADD COLUMN NegativeExtra TEXT NOT NULL AFTER PositiveExtra,
  ADD COLUMN PositivePrompt TEXT NOT NULL AFTER NegativeExtra,
  ADD COLUMN NegativePrompt TEXT NOT NULL AFTER PositivePrompt,
  ADD COLUMN Remark VARCHAR(1000) NULL AFTER NegativePrompt;

CREATE TABLE c_PromptOptions (
  Id VARCHAR(64) NOT NULL PRIMARY KEY,
  Category VARCHAR(40) NOT NULL,
  Name VARCHAR(100) NOT NULL,
  PositivePrompt VARCHAR(500) NOT NULL,
  NegativePrompt VARCHAR(500) NULL,
  SortOrder INT NOT NULL DEFAULT 0,
  Enabled BOOLEAN NOT NULL DEFAULT TRUE,
  AllowMultiple BOOLEAN NOT NULL DEFAULT FALSE,
  CreatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY IX_PromptOptions_Category (Category, Enabled, SortOrder)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE c_PromptTemplates (
  Id VARCHAR(64) NOT NULL PRIMARY KEY,
  Name VARCHAR(100) NOT NULL,
  Prompt TEXT NOT NULL,
  Enabled BOOLEAN NOT NULL DEFAULT TRUE,
  UpdatedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO c_PromptTemplates(Id, Name, Prompt, Enabled) VALUES
('general_negative', '通用反向模板', 'low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, missing fingers, extra limbs, deformed limbs, text, watermark, logo', TRUE);

INSERT INTO c_PromptOptions(Id, Category, Name, PositivePrompt, NegativePrompt, SortOrder, Enabled, AllowMultiple) VALUES
('solo', 'character_count', '单人', 'solo', 'multiple people, crowd', 10, TRUE, FALSE),
('two_people', 'character_count', '双人', '2people', 'solo, three or more people', 20, TRUE, FALSE),
('three_people', 'character_count', '三人', '3people', 'solo, two people, crowd', 30, TRUE, FALSE),
('girl', 'character_type', '女孩', '1girl', 'male', 10, TRUE, TRUE),
('woman', 'character_type', '成年女性', 'adult woman', 'child', 20, TRUE, TRUE),
('boy', 'character_type', '男孩', '1boy', 'female', 30, TRUE, TRUE),
('man', 'character_type', '成年男性', 'adult man', 'child', 40, TRUE, TRUE),
('futanari_girl', 'character_type', '扶她女孩', 'futanari girl', 'male only', 50, TRUE, TRUE),
('couple', 'relationship', '情侣', 'couple', 'strangers', 10, TRUE, TRUE),
('friends', 'relationship', '朋友', 'close friends', 'hostile', 20, TRUE, TRUE),
('lovers', 'relationship', '恋人', 'lovers', 'distant relationship', 30, TRUE, TRUE),
('looking_at_viewer', 'action', '看向镜头', 'looking at viewer', 'looking away', 10, TRUE, TRUE),
('hugging', 'action', '拥抱', 'hugging', 'separated', 20, TRUE, TRUE),
('kissing', 'action', '亲吻', 'kissing', 'separated faces', 30, TRUE, TRUE),
('walking', 'action', '行走', 'walking', 'static pose', 40, TRUE, TRUE),
('white_silk_thighhighs', 'clothing', '白色丝质长筒袜', 'white silk thighhighs', 'bare legs', 10, TRUE, TRUE),
('school_uniform', 'clothing', '校服', 'school uniform', 'nude', 20, TRUE, TRUE),
('elegant_dress', 'clothing', '优雅连衣裙', 'elegant dress', 'casual wear', 30, TRUE, TRUE),
('business_suit', 'clothing', '商务西装', 'business suit', 'sportswear', 40, TRUE, TRUE),
('swimsuit', 'clothing', '泳装', 'swimsuit', 'winter clothes', 50, TRUE, TRUE),
('smile', 'expression', '微笑', 'smile', 'sad, angry', 10, TRUE, FALSE),
('serious', 'expression', '严肃', 'serious expression', 'smile', 20, TRUE, FALSE),
('blush', 'expression', '脸红', 'blush', 'pale face', 30, TRUE, FALSE),
('crying', 'expression', '哭泣', 'crying', 'smile', 40, TRUE, FALSE),
('standing', 'pose', '站立', 'standing', 'sitting, lying', 10, TRUE, FALSE),
('sitting', 'pose', '坐姿', 'sitting', 'standing', 20, TRUE, FALSE),
('lying', 'pose', '躺姿', 'lying down', 'standing', 30, TRUE, FALSE),
('kneeling', 'pose', '跪姿', 'kneeling', 'standing', 40, TRUE, FALSE),
('eye_level', 'camera_angle', '平视', 'eye-level shot', 'high angle, low angle', 10, TRUE, FALSE),
('low_angle', 'camera_angle', '低角度', 'low angle', 'high angle', 20, TRUE, FALSE),
('high_angle', 'camera_angle', '高角度', 'high angle', 'low angle', 30, TRUE, FALSE),
('from_behind', 'camera_angle', '背后视角', 'from behind', 'front view', 40, TRUE, FALSE),
('full_body', 'shot_type', '全身', 'full body', 'cropped, out of frame, missing limbs', 10, TRUE, FALSE),
('cowboy_shot', 'shot_type', '牛仔镜头', 'cowboy shot', 'close-up, full body', 20, TRUE, FALSE),
('upper_body', 'shot_type', '上半身', 'upper body', 'full body, feet focus', 30, TRUE, FALSE),
('close_up', 'shot_type', '特写', 'close-up', 'full body, distant view', 40, TRUE, FALSE),
('bedroom', 'scene', '卧室', 'bedroom', 'outdoor', 10, TRUE, FALSE),
('city_street', 'scene', '城市街道', 'city street', 'indoor', 20, TRUE, FALSE),
('beach', 'scene', '海滩', 'beach', 'indoor', 30, TRUE, FALSE),
('forest', 'scene', '森林', 'forest', 'urban', 40, TRUE, FALSE),
('cafe', 'scene', '咖啡馆', 'cafe interior', 'outdoor', 50, TRUE, FALSE),
('centered_composition', 'composition', '居中构图', 'centered composition', 'off-center subject', 10, TRUE, TRUE),
('rule_of_thirds', 'composition', '三分法构图', 'rule of thirds', 'poor composition', 20, TRUE, TRUE),
('symmetrical', 'composition', '对称构图', 'symmetrical composition', 'asymmetrical framing', 30, TRUE, TRUE),
('depth_of_field', 'composition', '景深', 'depth of field', 'flat image', 40, TRUE, TRUE),
('dynamic_composition', 'composition', '动态构图', 'dynamic composition', 'static composition', 50, TRUE, TRUE),
('masterpiece', 'quality', '杰作', 'masterpiece', 'low quality', 10, TRUE, TRUE),
('best_quality', 'quality', '最佳画质', 'best quality', 'worst quality', 20, TRUE, TRUE),
('absurdres', 'quality', '超高分辨率', 'absurdres', 'lowres', 30, TRUE, TRUE),
('highly_detailed', 'quality', '高细节', 'highly detailed', 'simple background, low detail', 40, TRUE, TRUE);

INSERT INTO c_ComfyParameterSchemes
(Name, SelectedOptionsJson, PositiveExtra, NegativeExtra, PositivePrompt, NegativePrompt, Remark, IsDefault)
VALUES
('默认结构化方案',
 '{"characterCount":["solo"],"characterTypes":["girl"],"relationships":[],"actions":[],"clothing":[],"expression":[],"pose":[],"cameraAngle":[],"shotType":["full_body"],"scene":[],"composition":[],"quality":["masterpiece","best_quality"]}',
 '', '',
 'masterpiece, best quality, solo, 1girl, full body',
 'low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, missing fingers, extra limbs, deformed limbs, text, watermark, logo, multiple people, crowd, cropped, out of frame, missing limbs',
 '系统初始化的结构化 Prompt 方案', TRUE);
