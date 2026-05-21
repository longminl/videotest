package com.videocollect.dto;

import java.util.List;

/**
 * 通用分页结果
 */
public class PageResult<T> {

    private List<T> list;
    private int page;
    private int pageSize;
    private long total;
    private int totalPages;

    public PageResult() {}

    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
