package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.PromptTranslationDTO;
import com.aiprovider.model.vo.PromptTranslationVO;
import com.aiprovider.service.PromptTranslationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompt-translations")
public class PromptTranslationController {
    private final PromptTranslationService service;
    public PromptTranslationController(PromptTranslationService service) { this.service = service; }

    @PostMapping
    public Result<PromptTranslationVO> translate(@RequestBody PromptTranslationDTO dto) {
        return Result.success(service.translate(dto));
    }
}
