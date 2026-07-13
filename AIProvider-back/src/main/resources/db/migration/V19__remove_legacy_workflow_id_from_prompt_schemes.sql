UPDATE ComfyParameterSchemes
SET ParametersJson = JSON_REMOVE(ParametersJson, '$.workflowId')
WHERE JSON_CONTAINS_PATH(ParametersJson, 'one', '$.workflowId');
