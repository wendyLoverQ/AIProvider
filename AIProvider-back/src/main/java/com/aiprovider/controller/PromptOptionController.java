package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.PromptOptionDTO;
import com.aiprovider.model.vo.PromptOptionPageVO;
import com.aiprovider.service.PromptOptionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prompt-options")
public class PromptOptionController {
    private final PromptOptionService service;
    public PromptOptionController(PromptOptionService service) { this.service = service; }
    @GetMapping
    public Result<PromptOptionPageVO> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "100") int pageSize,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(defaultValue = "all") String status) {
        return Result.success(service.page(query, category, status, page, pageSize));
    }
    @PostMapping public Result<Void> create(@RequestBody PromptOptionDTO dto) { service.create(dto); return Result.success(); }
    @PutMapping("/{id}") public Result<Void> update(@PathVariable String id, @RequestBody PromptOptionDTO dto) { service.update(id, dto); return Result.success(); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable String id) { service.delete(id); return Result.success(); }
}
