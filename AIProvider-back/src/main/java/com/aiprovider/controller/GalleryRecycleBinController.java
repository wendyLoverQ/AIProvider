package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.service.GalleryRecycleBinService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gallery-recycle-bin")
public class GalleryRecycleBinController {
    private final GalleryRecycleBinService service;
    public GalleryRecycleBinController(GalleryRecycleBinService service) { this.service = service; }
    @GetMapping public Result<GalleryRecordPageVO> page(@RequestParam String platform,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "100") int pageSize) {
        return Result.success(service.page(platform, page, pageSize));
    }
}
