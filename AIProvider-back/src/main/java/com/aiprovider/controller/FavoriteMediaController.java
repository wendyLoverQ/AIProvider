package com.aiprovider.controller;

import com.aiprovider.common.Result;
import com.aiprovider.model.dto.FavoriteMediaDeleteDTO;
import com.aiprovider.model.dto.FavoriteMediaBatchItemDTO;
import com.aiprovider.model.vo.FavoriteMediaContent;
import com.aiprovider.model.vo.FavoriteMediaPageVO;
import com.aiprovider.model.vo.FavoriteMediaVO;
import com.aiprovider.service.FavoriteMediaService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteMediaController {
    private final FavoriteMediaService service;
    public FavoriteMediaController(FavoriteMediaService service) { this.service = service; }

    @GetMapping
    public Result<FavoriteMediaPageVO> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "60") int pageSize) {
        return Result.success(service.page(page, pageSize));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FavoriteMediaVO> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) Long assetId,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) Integer width,
                                         @RequestParam(required = false) Integer height,
                                         @RequestParam(required = false) String prompt,
                                         @RequestParam(required = false) String sourcePlatform) throws IOException {
        return Result.success(service.upload(file, assetId, title, width, height, prompt, sourcePlatform));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String,Integer>> uploadBatch(@RequestParam("files") List<MultipartFile> files,
                                                    @RequestParam("metadata") String metadata) throws IOException {
        return Result.success(Collections.singletonMap("saved", service.uploadBatch(files, metadata)));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(@PathVariable long id) throws IOException {
        FavoriteMediaContent content = service.content(id);
        return response(content, true);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<org.springframework.core.io.Resource> thumbnail(@PathVariable long id) throws IOException {
        FavoriteMediaContent content = service.thumbnail(id);
        // 缩略图长期缓存：1 天
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(content.getFileSize())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(content.getResource());
    }

    @DeleteMapping
    public Result<Map<String,Integer>> delete(@RequestBody FavoriteMediaDeleteDTO dto) throws IOException {
        return Result.success(Collections.singletonMap("deleted", service.delete(dto == null ? null : dto.getIds())));
    }

    private ResponseEntity<org.springframework.core.io.Resource> response(FavoriteMediaContent content, boolean inline) {
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(content.getContentType()); }
        catch (IllegalArgumentException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaType).contentLength(content.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(content.getResource());
    }
}
