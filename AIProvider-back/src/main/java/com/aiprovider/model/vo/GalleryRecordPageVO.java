package com.aiprovider.model.vo;

import java.util.List;
import java.util.Map;

public class GalleryRecordPageVO {
    private final List<Map<String,Object>> items;
    private final long total;
    private final int page;
    private final int pageSize;
    private final long pages;
    public GalleryRecordPageVO(List<Map<String,Object>> items, long total, int page, int pageSize) {
        this.items = items; this.total = total; this.page = page; this.pageSize = pageSize;
        this.pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }
    public List<Map<String,Object>> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getPages() { return pages; }
}
