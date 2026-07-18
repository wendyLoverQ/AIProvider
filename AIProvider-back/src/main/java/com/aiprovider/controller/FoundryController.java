package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.FoundryCallDTO;
import com.aiprovider.model.vo.FoundryQueryVO;
import com.aiprovider.model.vo.FoundryStatusVO;
import com.aiprovider.service.FoundryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foundry")
public class FoundryController {
    private final FoundryService service;

    public FoundryController(FoundryService service) { this.service = service; }

    @GetMapping("/status")
    public Result<FoundryStatusVO> status() { return Result.success(service.status()); }

    @GetMapping("/block-number")
    public Result<FoundryQueryVO> blockNumber() { return Result.success(service.blockNumber()); }

    @GetMapping("/balance")
    public Result<FoundryQueryVO> balance(@RequestParam String address) { return Result.success(service.balance(address)); }

    @GetMapping("/code")
    public Result<FoundryQueryVO> code(@RequestParam String address) { return Result.success(service.code(address)); }

    @PostMapping("/call")
    public Result<FoundryQueryVO> call(@RequestBody FoundryCallDTO dto) { return Result.success(service.call(dto)); }
}
