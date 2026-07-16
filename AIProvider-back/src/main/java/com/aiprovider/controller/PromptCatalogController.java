package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.vo.PromptCatalogVO;
import com.aiprovider.service.PromptCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompt-catalog")
public class PromptCatalogController {
    private final PromptCatalogService service;
    public PromptCatalogController(PromptCatalogService service) { this.service = service; }
    @GetMapping public Result<PromptCatalogVO> get() { return Result.success(service.get()); }
}
