package com.lixin.probe.agent.pojo.response;


import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应类
 *
 * @param <T> 数据类型
 * @author probe-agent
 * @since 1.0.0
 */
public class PageResponse<T> {

    /**
     * 数据列表
     */
    private List<T> records = new ArrayList<>();

    /**
     * 总记录数
     */
    private Long total = 0L;

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 20;

    /**
     * 总页数
     */
    private Integer totalPages = 0;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious = false;

    /**
     * 是否有下一页
     */
    private Boolean hasNext = false;

    /**
     * 创建空分页响应
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>();
    }

    /**
     * 创建分页响应
     */
    public static <T> PageResponse<T> of(List<T> records, long total, int page, int size) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);

        int totalPages = (int) Math.ceil((double) total / size);
        response.setTotalPages(totalPages);
        response.setHasPrevious(page > 1);
        response.setHasNext(page < totalPages);

        return response;
    }

    /**
     * 创建分页响应（自动计算总页数）
     */
    public static <T> PageResponse<T> of(List<T> records, int page, int size) {
        return of(records, (long) records.size(), page, size);
    }
    public List<T> getRecords() {
        return records;
    }
    public void setRecords(List<T> records) {
        this.records = records;
    }
    public Long getTotal() {
        return total;
    }
    public void setTotal(Long total) {
        this.total = total;
    }
    public Integer getPage() {
        return page;
    }
    public void setPage(Integer page) {
        this.page = page;
    }
    public Integer getSize() {
        return size;
    }
    public void setSize(Integer size) {
        this.size = size;
    }
    public Integer getTotalPages() {
        return totalPages;
    }
    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
    public Boolean getHasPrevious() {
        return hasPrevious;
    }
    public void setHasPrevious(Boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
    public Boolean getHasNext() {
        return hasNext;
    }
    public void setHasNext(Boolean hasNext) {
        this.hasNext = hasNext;
    }
}
