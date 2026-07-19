package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.*;
import com.aiprovider.model.vo.*;
import com.aiprovider.service.ContentOperationsService;
import com.aiprovider.service.ContentAiConfigService;
import com.aiprovider.service.ContentGenerationService;
import com.aiprovider.service.ContentSourceService;
import com.aiprovider.service.ContentRelevanceService;
import com.aiprovider.service.ContentPipelineService;
import com.aiprovider.service.XiaohongshuAccountService;
import com.aiprovider.service.XiaohongshuPublicationService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content-operations")
public class ContentOperationsController {
    private final ContentOperationsService service;
    private final ContentAiConfigService aiConfigService;
    private final ContentGenerationService generationService;
    private final ContentSourceService sourceService;
    private final ContentRelevanceService relevanceService;
    private final ContentPipelineService pipelineService;
    private final XiaohongshuAccountService xhsAccountService;
    private final XiaohongshuPublicationService publicationService;
    public ContentOperationsController(ContentOperationsService service,ContentAiConfigService aiConfigService,ContentGenerationService generationService,ContentSourceService sourceService,ContentRelevanceService relevanceService,ContentPipelineService pipelineService,XiaohongshuAccountService xhsAccountService,XiaohongshuPublicationService publicationService){this.service=service;this.aiConfigService=aiConfigService;this.generationService=generationService;this.sourceService=sourceService;this.relevanceService=relevanceService;this.pipelineService=pipelineService;this.xhsAccountService=xhsAccountService;this.publicationService=publicationService;}
    @GetMapping("/overview") public Result<ContentOperationsOverviewVO> overview(){return Result.success(service.overview());}
    @PostMapping("/accounts") public Result<ContentAccountVO> createAccount(@RequestBody ContentAccountCreateDTO dto){return Result.success(service.createAccount(dto));}
    @PatchMapping("/accounts/{id}") public Result<ContentAccountVO> updateAccount(@PathVariable long id,@RequestBody ContentAccountModeDTO dto){return Result.success(service.updateAccount(id,dto));}
    @PostMapping("/sources") public Result<ContentSourceVO> createSource(@RequestBody ContentSourceCreateDTO dto){return Result.success(sourceService.create(dto));}
    @PostMapping("/collection-accounts") public Result<ContentCollectionAccountVO> createCollectionAccount(@RequestBody ContentCollectionAccountCreateDTO dto){return Result.success(sourceService.createCollectionAccount(dto));}
    @PostMapping("/sources/{id}/test-fetch") public Result<ContentSourceTestVO> testSource(@PathVariable long id){return Result.success(sourceService.testFetch(id));}
    @GetMapping("/sources/{id}/items") public Result<List<ContentItemVO>> sourceItems(@PathVariable long id,@RequestParam(defaultValue="50") int limit){return Result.success(sourceService.items(id,limit));}
    @PostMapping("/items/{id}/classify") public Result<ContentRelevanceVO> classify(@PathVariable long id){return Result.success(relevanceService.classify(id));}
    @GetMapping("/accounts/{id}/sources") public Result<List<Long>> accountSources(@PathVariable long id){return Result.success(sourceService.accountSourceIds(id));}
    @PutMapping("/accounts/{id}/sources") public Result<List<Long>> bindAccountSources(@PathVariable long id,@RequestBody ContentAccountSourcesDTO dto){return Result.success(sourceService.bindAccountSources(id,dto));}
    @PostMapping("/accounts/{id}/test-pipeline") public Result<List<ContentPipelineTestVO>> testPipeline(@PathVariable long id){return Result.success(pipelineService.testAccount(id));}
    @PostMapping("/publications/{id}/retry") public Result<XhsPublicationResultVO> retryPublication(@PathVariable long id){return Result.success(publicationService.publish(id));}
    @GetMapping("/publications/{id}") public Result<Map<String,Object>> publicationDetails(@PathVariable long id){return Result.success(service.publicationDetails(id));}
    @GetMapping("/items") public Result<List<Map<String,Object>>> collectionHistory(@RequestParam(required=false) String query,@RequestParam(required=false) Long sourceId,@RequestParam(defaultValue="100") int limit){return Result.success(service.collectionHistory(query,sourceId,limit));}
    @GetMapping("/automation-runs") public Result<List<Map<String,Object>>> automationRuns(@RequestParam(defaultValue="30") int limit){return Result.success(service.automationRuns(limit));}
    @PostMapping("/accounts/{id}/xhs-login") public Result<XhsLoginSessionVO> startXhsLogin(@PathVariable long id){return Result.success(xhsAccountService.startLogin(id));}
    @GetMapping("/accounts/{id}/xhs-login/{sessionId}") public Result<XhsLoginSessionVO> pollXhsLogin(@PathVariable long id,@PathVariable String sessionId){return Result.success(xhsAccountService.poll(id,sessionId));}
    @PutMapping("/settings") public Result<ContentOperationSettingsVO> updateSettings(@RequestBody ContentOperationSettingsDTO dto){return Result.success(service.updateSettings(dto));}
    @GetMapping("/ai-config") public Result<ContentAiConfigVO> aiConfig(){return Result.success(aiConfigService.get());}
    @PutMapping("/ai-config") public Result<ContentAiConfigVO> updateAiConfig(@RequestBody ContentAiConfigDTO dto){return Result.success(aiConfigService.save(dto));}
    @PostMapping("/ai-config/test") public Result<ContentGenerationVO> testAiConfig(){return Result.success(generationService.testConnection());}
    @PostMapping("/generate/content-rewrite") public Result<ContentGenerationVO> rewrite(@RequestBody ContentRewriteRequestDTO dto){return Result.success(generationService.rewrite(dto));}
    @PostMapping("/generate/comment-reply") public Result<ContentGenerationVO> reply(@RequestBody CommentReplyRequestDTO dto){return Result.success(generationService.reply(dto));}
}
