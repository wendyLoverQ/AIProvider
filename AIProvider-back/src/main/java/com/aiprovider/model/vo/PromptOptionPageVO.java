package com.aiprovider.model.vo;

import java.util.List;

public class PromptOptionPageVO {
    private final List<PromptOptionVO> items;
    private final long total;
    private final int page;
    private final int pageSize;
    private final long pages;

    public PromptOptionPageVO(List<PromptOptionVO> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    public List<PromptOptionVO> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getPages() { return pages; }
}
