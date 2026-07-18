package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.AssetBatchDTO;
import com.aiprovider.model.dto.AssetDeleteDTO;
import com.aiprovider.model.dto.AssetStatusDTO;
import com.aiprovider.model.vo.AssetPageVO;
import com.aiprovider.model.vo.AssetBatchResultVO;
import com.aiprovider.model.vo.AssetPromptVO;
import com.aiprovider.service.AssetService;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;
    public AssetController(AssetService service) { this.service = service; }
    @GetMapping public Result<AssetPageVO> page(@RequestParam String platform, @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "100") int pageSize,
                                               @RequestParam(required = false) String status) {
        return Result.success(service.page(platform, page, pageSize, status));
    }
    @PostMapping("/batch") public Result<AssetBatchResultVO> save(@RequestBody AssetBatchDTO dto) {
        return Result.success(service.saveBatch(dto));
    }
    @GetMapping("/prompt-pool") public Result<List<AssetPromptVO>> promptPool(@RequestParam String platform) {
        return Result.success(service.imagePromptPool(platform));
    }
    @PostMapping("/delete") public Result<Map<String,Integer>> delete(@RequestBody AssetDeleteDTO dto) {
        return Result.success(Collections.singletonMap("deleted", service.delete(dto)));
    }
    @PutMapping("/status") public Result<Map<String,Integer>> updateStatus(@RequestBody AssetStatusDTO dto) {
        return Result.success(Collections.singletonMap("updated", service.updateStatus(dto)));
    }
}