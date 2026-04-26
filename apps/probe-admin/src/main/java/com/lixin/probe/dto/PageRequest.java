package com.lixin.probe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页请求参数封装类
 * 统一管理分页参数，避免在每个Controller中重复定义
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
public class PageRequest {

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页数量
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最小页码
     */
    public static final int MIN_PAGE_NUM = 1;

    /**
     * 最大每页数量
     */
    public static final int MAX_PAGE_SIZE = 1000;

    /**
     * 页码（从1开始）
     */
    @Min(value = MIN_PAGE_NUM, message = "页码必须大于0")
    private int pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页数量
     */
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = MAX_PAGE_SIZE, message = "每页数量不能超过" + MAX_PAGE_SIZE)
    private int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方向（asc/desc）
     */
    private String sortOrder = "desc";

    public PageRequest() {
    }

    public PageRequest(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * 获取偏移量（用于SQL的LIMIT OFFSET）
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 创建默认分页请求
     */
    public static PageRequest ofDefault() {
        return new PageRequest(DEFAULT_PAGE_NUM, DEFAULT_PAGE_SIZE);
    }

    /**
     * 创建分页请求
     */
    public static PageRequest of(int pageNum, int pageSize) {
        return new PageRequest(pageNum, pageSize);
    }

    /**
     * 从Query参数创建分页请求
     *
     * @param pageNum 页码（可为null，使用默认值1）
     * @param pageSize 每页数量（可为null，使用默认值10）
     * @return 分页请求对象
     */
    public static PageRequest fromQuery(Integer pageNum, Integer pageSize) {
        PageRequest request = new PageRequest();
        if (pageNum != null && pageNum >= MIN_PAGE_NUM) {
            request.setPageNum(pageNum);
        }
        if (pageSize != null && pageSize >= 1 && pageSize <= MAX_PAGE_SIZE) {
            request.setPageSize(pageSize);
        }
        return request;
    }

    // Getters and Setters
    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 检查是否需要排序
     */
    public boolean needsSort() {
        return sortField != null && !sortField.isEmpty();
    }

    /**
     * 获取排序字符串（用于SQL ORDER BY）
     * 例如：id DESC
     */
    public String getSortString() {
        if (!needsSort()) {
            return null;
        }
        return sortField + " " + sortOrder.toUpperCase();
    }

    @Override
    public String toString() {
        return "PageRequest{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", sortField='" + sortField + '\'' +
                ", sortOrder='" + sortOrder + '\'' +
                '}';
    }
}
