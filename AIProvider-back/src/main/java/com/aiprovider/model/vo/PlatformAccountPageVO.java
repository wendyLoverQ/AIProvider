package com.aiprovider.model.vo;

import java.util.List;

public class PlatformAccountPageVO {
    private final List<PlatformAccountVO> items;private final long total;private final int page;private final int pageSize;private final int pages;
    public PlatformAccountPageVO(List<PlatformAccountVO> items,long total,int page,int pageSize){this.items=items;this.total=total;this.page=page;this.pageSize=pageSize;this.pages=(int)Math.ceil(total/(double)pageSize);}public List<PlatformAccountVO> getItems(){return items;}public long getTotal(){return total;}public int getPage(){return page;}public int getPageSize(){return pageSize;}public int getPages(){return pages;}
}
