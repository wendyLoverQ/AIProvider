package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.AsrCorrectionDTO;
import com.aiprovider.model.vo.*;
import com.aiprovider.service.AsrTranscriptionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/asr/records")
public class AsrAdminController {
    private final AsrTranscriptionService service;public AsrAdminController(AsrTranscriptionService service){this.service=service;}
    @GetMapping public Result<AsrRecordPageVO> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String provider,@RequestParam(required=false) String model,@RequestParam(required=false) String keyword,@RequestParam(required=false) String startTime,@RequestParam(required=false) String endTime){return Result.success(service.page(page,pageSize,status,provider,model,keyword,startTime,endTime));}
    @GetMapping("/filters") public Result<Map<String,Object>> filters(){return Result.success(service.filters());}
    @GetMapping("/quota") public Result<AsrQuotaVO> quota(){return Result.success(service.quota());}
    @GetMapping("/{recordId}") public Result<AsrRecordVO> get(@PathVariable String recordId){return Result.success(service.get(recordId));}
    @PutMapping("/{recordId}/correction") public Result<Void> correction(@PathVariable String recordId,@RequestBody AsrCorrectionDTO dto){service.correct(recordId,dto==null?null:dto.getCorrectedText());return Result.success();}
    @GetMapping("/{recordId}/audio") public ResponseEntity<org.springframework.core.io.Resource> audio(@PathVariable String recordId){AsrAudioContent content=service.audio(recordId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.getContentType())).contentLength(content.getFileSize()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(content.getFileName(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"private, max-age=86400").header(HttpHeaders.ACCEPT_RANGES,"bytes").body(content.getResource());}
}
