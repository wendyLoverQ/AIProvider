# 图片主模型元数据设计

## 目标

从本次发布之后生成的图片开始，稳定记录生成时选择的主模型，并在图片详情中显示主模型文件名。历史图片不回填；没有记录的图片继续显示“未记录”。

## 已确认根因

图片详情界面已经读取 `item.mainModel`，生成资产表和任务表也已有 `MainModel` 字段，但本机图片队列 `c_LocalGeneratedImages`、对应 DTO/Mapper，以及前端图片完成入库请求均未携带该字段。因此主模型在“生成任务完成 → 本机图片队列”边界丢失，之后转成资产时只能保存空值。

## 数据设计

- 新增 Flyway 迁移，为 `c_LocalGeneratedImages` 添加可空的 `MainModel VARCHAR(1000)`。
- 迁移只新增字段，不执行 `UPDATE`，不回填历史记录。
- `LocalGeneratedImageItemDTO` 增加 `mainModel`。
- 批量入库时清理首尾空白并限制到 1000 字符，与生成任务和资产的现有约束一致。
- `LocalGeneratedImageMapper` 的批量插入、重复路径更新、分页查询和按路径查询全部携带 `MainModel`。

## 数据流

1. 创建生成任务时，工作台继续以 `form.checkpoint` 作为实际选择的主模型。
2. 任务完成并登记本机图片时，记录使用 `task.mainModel`；若任务对象没有独立字段，则使用 `form.checkpoint`。
3. 本机图片 API 保存并返回 `mainModel`。
4. 图片从本机队列迁移到“我的资产”时，沿用现有 `source.mainModel` 映射写入 `c_GeneratedAssets.MainModel`。
5. 图片详情读取记录上的 `mainModel`，仅显示路径末尾的文件名；空值显示“未记录”。

这里不增加从任务表查询、PNG 解析或其他兜底路径。每张图片记录必须自包含生成元数据。

## 展示规则

- Windows 路径 `flux\\dev.safetensors` 显示为 `dev.safetensors`。
- Unix 路径 `flux/dev.safetensors` 显示为 `dev.safetensors`。
- 单独文件名保持不变。
- `NULL`、空字符串或仅空白显示“未记录”。
- 只改变视觉显示，数据库保存原始规范化值，确保后续追溯不丢目录信息。

## 日志与错误处理

沿用现有本机图片批量入库结构化日志，记录操作、平台、请求数量、保存数量和数据库 ID。主模型属于普通生成元数据，但日志不额外输出其完整值，避免扩大日志内容。

## 测试范围

- 后端服务测试：新图片批量入库会清理并保存 `mainModel`。
- 后端 Mapper/契约检查：插入、更新和查询 SQL 都包含 `MainModel`。
- 前端工作台测试：完成记录请求携带主模型。
- 前端详情测试：Windows/Unix 模型路径仅显示文件名，空值显示“未记录”。
- UI 门禁：图片详情交互与语义检查继续通过。

只运行覆盖本次改动的后端测试、工作台相关前端测试和 `uiGate.test.js`，不运行无关全量测试。

## 发布边界

本设计只描述本地实现与验证，不自动授权生产发布。若后续要求发布，按独立前后端版本号、明确 `master` 提交哈希和生产配置执行发布流程。
