package com.aiprovider.service;

import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.repository.GalleryRecycleBinRepository;
import org.springframework.stereotype.Service;

@Service
public class GalleryRecycleBinService {
    private final GalleryRecycleBinRepository repository;
    public GalleryRecycleBinService(GalleryRecycleBinRepository repository) { this.repository = repository; }
    public GalleryRecordPageVO page(String platformValue, int page, int pageSize) {
        String platform;
        if ("windows".equalsIgnoreCase(platformValue)) platform = "Windows";
        else if ("mac".equalsIgnoreCase(platformValue) || "macos".equalsIgnoreCase(platformValue)) platform = "macOS";
        else throw new IllegalArgumentException("platform 仅支持 Windows 或 macOS");
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        long total = repository.count(platform);
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int currentPage = pages == 0 ? 1 : (int)Math.min(page, pages);
        return new GalleryRecordPageVO(repository.findPage(platform, pageSize, (currentPage - 1) * pageSize), total, currentPage, pageSize);
    }
}
