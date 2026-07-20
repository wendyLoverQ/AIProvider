package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.SyncBatchDTO;
import com.aiprovider.model.vo.SyncResultVO;
import com.aiprovider.service.SyncService;
import com.aiprovider.service.MaidAvatarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final MaidAvatarService avatarService;

    public SyncController(SyncService syncService, MaidAvatarService avatarService) {
        this.syncService = syncService;
        this.avatarService = avatarService;
    }

    @PostMapping("/business-batch")
    public Result<SyncResultVO> businessBatch(@RequestBody SyncBatchDTO batch) {
        return Result.success(syncService.processBusinessBatch(
            batch.getDeviceId(), batch.getRecords()));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(syncService.getStatus());
    }

    @PostMapping(value = "/role-avatar/{roleId}", consumes = "multipart/form-data")
    public Result<Map<String, Object>> roleAvatar(@PathVariable String roleId,
                                                   @RequestPart("file") MultipartFile file) throws java.io.IOException {
        avatarService.save(roleId, file);
        log.info("businessOperation=syncRoleAvatar roleId={} requestCount=1 affectedRows=1 fileSize={}", roleId, file.getSize());
        return Result.success(Map.of("roleId", roleId, "saved", 1));
    }
}
