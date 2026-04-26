package com.lixin.probe.dto;

import java.time.LocalDateTime;

/**
 * 通用查询请求封装类
 * 封装常用的查询条件
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
public class QueryRequest {

    /**
     * 分页参数
     */
    private PageRequest pageRequest;

    /**
     * 关键字搜索
     */
    private String keyword;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态
     */
    private String status;

    /**
     * 类型
     */
    private String type;

    /**
     * 是否启用
     */
    private Boolean enabled;

    public QueryRequest() {
        this.pageRequest = PageRequest.ofDefault();
    }

    public QueryRequest(PageRequest pageRequest) {
        this.pageRequest = pageRequest != null ? pageRequest : PageRequest.ofDefault();
    }

    /**
     * 创建基础查询请求
     */
    public static QueryRequest of(PageRequest pageRequest) {
        return new QueryRequest(pageRequest);
    }

    /**
     * 创建带搜索关键字的查询请求
     */
    public static QueryRequest of(String keyword, PageRequest pageRequest) {
        QueryRequest request = new QueryRequest(pageRequest);
        request.setKeyword(keyword);
        return request;
    }

    /**
     * 创建时间范围查询请求
     */
    public static QueryRequest ofTimeRange(LocalDateTime startTime, LocalDateTime endTime, PageRequest pageRequest) {
        QueryRequest request = new QueryRequest(pageRequest);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        return request;
    }

    /**
     * 检查是否有关键字搜索
     */
    public boolean hasKeyword() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    /**
     * 检查是否有时间范围
     */
    public boolean hasTimeRange() {
        return startTime != null && endTime != null;
    }

    /**
     * 检查是否有状态过滤
     */
    public boolean hasStatus() {
        return status != null && !status.trim().isEmpty();
    }

    /**
     * 检查是否有类型过滤
     */
    public boolean hasType() {
        return type != null && !type.trim().isEmpty();
    }

    /**
     * 检查是否启用过滤
     */
    public boolean hasEnabled() {
        return enabled != null;
    }

    // Getters and Setters
    public PageRequest getPageRequest() {
        return pageRequest;
    }

    public void setPageRequest(PageRequest pageRequest) {
        this.pageRequest = pageRequest;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                "pageRequest=" + pageRequest +
                ", keyword='" + keyword + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
