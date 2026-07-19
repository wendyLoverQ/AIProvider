package com.aiprovider.model.vo;

import java.util.List;

public class FavoriteMediaPageVO {
    private final List<FavoriteMediaVO> items;
    private final long total;
    private final int page;
    private final int pageSize;
    public FavoriteMediaPageVO(List<FavoriteMediaVO> items, long total, int page, int pageSize) {
        this.items = items; this.total = total; this.page = page; this.pageSize = pageSize;
    }
    public List<FavoriteMediaVO> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
