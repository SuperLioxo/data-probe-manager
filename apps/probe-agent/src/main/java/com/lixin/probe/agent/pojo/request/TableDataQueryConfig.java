package com.lixin.probe.agent.pojo.request;

import java.util.Map;

/**
 * 表数据查询配置
 */
public class TableDataQueryConfig {
    private String databaseName;
    private String tableName;
    private Integer pageNum = 1;
    private Integer pageSize = 50;
    private Map<String, Object> filters; // 列名 -> 过滤值

    // 游标分页支持
    private String cursor; // 上一次查询的最后一条记录的游标值
    private String orderByColumn; // 排序列（通常是时间戳列）
    private boolean useCursorPagination = false; // 是否使用游标分页

    public TableDataQueryConfig() {}

    public TableDataQueryConfig(String databaseName, String tableName, Integer pageNum, Integer pageSize) {
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.pageNum = pageNum != null ? pageNum : 1;
        this.pageSize = pageSize != null ? pageSize : 50;
    }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public Map<String, Object> getFilters() { return filters; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }

    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public String getOrderByColumn() { return orderByColumn; }
    public void setOrderByColumn(String orderByColumn) { this.orderByColumn = orderByColumn; }

    public boolean isUseCursorPagination() { return useCursorPagination; }
    public void setUseCursorPagination(boolean useCursorPagination) { this.useCursorPagination = useCursorPagination; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private TableDataQueryConfig config = new TableDataQueryConfig();

        public Builder databaseName(String databaseName) {
            config.databaseName = databaseName;
            return this;
        }

        public Builder tableName(String tableName) {
            config.tableName = tableName;
            return this;
        }

        public Builder pageNum(Integer pageNum) {
            config.pageNum = pageNum;
            return this;
        }

        public Builder pageSize(Integer pageSize) {
            config.pageSize = pageSize;
            return this;
        }

        public Builder filters(Map<String, Object> filters) {
            config.filters = filters;
            return this;
        }

        public Builder cursor(String cursor) {
            config.cursor = cursor;
            return this;
        }

        public Builder orderByColumn(String orderByColumn) {
            config.orderByColumn = orderByColumn;
            return this;
        }

        public Builder useCursorPagination(boolean useCursorPagination) {
            config.useCursorPagination = useCursorPagination;
            return this;
        }

        public TableDataQueryConfig build() {
            return config;
        }
    }
}
