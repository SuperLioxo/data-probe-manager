package com.lixin.probe.agent.pojo.request;


/**
 * 分页请求基类
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class PageRequest {

    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 20;

    /**
     * 排序字段
     */
    private String sort;

    /**
     * 排序方向（asc/desc）
     */
    private String order = "asc";

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }

    /**
     * 验证分页参数
     */
    public void validate() {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 1000) {
            size = 1000; // 限制最大每页1000条
        }
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
    public String getSort() {
        return sort;
    }
    public void setSort(String sort) {
        this.sort = sort;
    }
    public String getOrder() {
        return order;
    }
    public void setOrder(String order) {
        this.order = order;
    }
    public String getKeyword() {
        return keyword;
    }
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
