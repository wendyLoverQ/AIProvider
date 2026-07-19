package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImagePathsDTO;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.service.LocalGeneratedImageService;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/local-generated-images")
public class LocalGeneratedImageController {
    private final LocalGeneratedImageService service;
    public LocalGeneratedImageController(LocalGeneratedImageService service) { this.service = service; }
    @GetMapping public Result<GalleryRecordPageVO> page(@RequestParam String platform,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "100") int pageSize,
                                                       @RequestParam(defaultValue = "ACTIVE") String status) {
        return Result.success(service.page(platform, page, pageSize, status));
    }
    @PostMapping("/batch") public Result<Map<String,Integer>> save(@RequestBody LocalGeneratedImageBatchDTO dto) {
        return Result.success(Collections.singletonMap("saved", service.saveBatch(dto)));
    }
    @PostMapping("/trash") public Result<Map<String,Integer>> trash(@RequestBody LocalGeneratedImagePathsDTO dto) {
        return Result.success(Collections.singletonMap("trashed", service.trash(dto)));
    }
    @PostMapping("/restore") public Result<Map<String,Integer>> restore(@RequestBody LocalGeneratedImagePathsDTO dto) {
        return Result.success(Collections.singletonMap("restored", service.restore(dto)));
    }
    @PostMapping("/delete") public Result<Map<String,Integer>> delete(@RequestBody LocalGeneratedImagePathsDTO dto) {
        return Result.success(Collections.singletonMap("deleted", service.delete(dto)));
    }
}
