package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.ComfyPresetDTO;
import com.aiprovider.model.vo.ComfyPresetVO;
import com.aiprovider.service.ComfyPresetService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/comfy-presets")
public class ComfyPresetController {
    private final ComfyPresetService service;
    public ComfyPresetController(ComfyPresetService service) { this.service = service; }
    @GetMapping public Result<List<ComfyPresetVO>> list() { return Result.success(service.list()); }
    @PostMapping public Result<Map<String, Long>> create(@RequestBody ComfyPresetDTO dto) {
        return Result.success(Collections.singletonMap("id", service.create(dto)));
    }
    @PutMapping("/{id}") public Result<Void> update(@PathVariable long id, @RequestBody ComfyPresetDTO dto) { service.update(id, dto); return Result.success(); }
    @PostMapping("/{id}/default") public Result<Void> setDefault(@PathVariable long id) { service.setDefault(id); return Result.success(); }
    @DeleteMapping("/{id}/default") public Result<Void> clearDefault(@PathVariable long id) { service.clearDefault(); return Result.success(); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.success(); }
}
