package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.*;
import com.aiprovider.model.vo.*;
import com.aiprovider.service.ContentOperationsService;
import com.aiprovider.service.ContentAiConfigService;
import com.aiprovider.service.ContentGenerationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content-operations")
public class ContentOperationsController {
    private final ContentOperationsService service;
    private final ContentAiConfigService aiConfigService;
    private final ContentGenerationService generationService;
    public ContentOperationsController(ContentOperationsService service,ContentAiConfigService aiConfigService,ContentGenerationService generationService){this.service=service;this.aiConfigService=aiConfigService;this.generationService=generationService;}
    @GetMapping("/overview") public Result<ContentOperationsOverviewVO> overview(){return Result.success(service.overview());}
    @PostMapping("/accounts") public Result<ContentAccountVO> createAccount(@RequestBody ContentAccountCreateDTO dto){return Result.success(service.createAccount(dto));}
    @PatchMapping("/accounts/{id}") public Result<ContentAccountVO> updateAccount(@PathVariable long id,@RequestBody ContentAccountModeDTO dto){return Result.success(service.updateAccount(id,dto));}
    @PostMapping("/sources") public Result<ContentSourceVO> createSource(@RequestBody ContentSourceCreateDTO dto){return Result.success(service.createSource(dto));}
    @PutMapping("/settings") public Result<ContentOperationSettingsVO> updateSettings(@RequestBody ContentOperationSettingsDTO dto){return Result.success(service.updateSettings(dto));}
    @GetMapping("/ai-config") public Result<ContentAiConfigVO> aiConfig(){return Result.success(aiConfigService.get());}
    @PutMapping("/ai-config") public Result<ContentAiConfigVO> updateAiConfig(@RequestBody ContentAiConfigDTO dto){return Result.success(aiConfigService.save(dto));}
    @PostMapping("/ai-config/test") public Result<ContentGenerationVO> testAiConfig(){return Result.success(generationService.testConnection());}
    @PostMapping("/generate/content-rewrite") public Result<ContentGenerationVO> rewrite(@RequestBody ContentRewriteRequestDTO dto){return Result.success(generationService.rewrite(dto));}
    @PostMapping("/generate/comment-reply") public Result<ContentGenerationVO> reply(@RequestBody CommentReplyRequestDTO dto){return Result.success(generationService.reply(dto));}
}
