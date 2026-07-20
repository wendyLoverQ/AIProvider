package com.aiprovider.controller;
import com.aiprovider.common.Result;
import com.aiprovider.model.dto.ComfyTaskDuplicateBatchDTO;
import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import com.aiprovider.service.ComfyTaskService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/comfy-tasks")
public class ComfyTaskController {
    private final ComfyTaskService service;
    public ComfyTaskController(ComfyTaskService service) { this.service = service; }
    @PostMapping public Result<Void> save(@RequestBody ComfyTaskRecordDTO dto) { service.save(dto); return Result.success(null); }
    @PostMapping("/batch") public Result<Void> saveBatch(@RequestBody List<ComfyTaskRecordDTO> items) { service.saveBatch(items); return Result.success(null); }
    @GetMapping("/duplicate") public Result<Map<String,Object>> duplicate(@RequestParam String workflowId, @RequestParam String inputSha256) { return Result.success(service.duplicate(workflowId, inputSha256)); }
    @PostMapping("/duplicates") public Result<List<String>> duplicates(@RequestBody ComfyTaskDuplicateBatchDTO dto) { return Result.success(service.duplicateHashes(dto.getWorkflowId(), dto.getInputSha256List())); }
    @PostMapping("/{promptId}/complete") public Result<Void> complete(@PathVariable String promptId, @RequestBody Map<String,List<String>> body) { service.complete(promptId, body.get("paths")); return Result.success(null); }
}
