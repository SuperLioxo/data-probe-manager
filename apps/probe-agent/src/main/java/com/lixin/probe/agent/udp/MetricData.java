package com.lixin.probe.agent.udp;

/**
 * UDP 指标数据模型
 * 用于封装系统监控指标
 */
public class MetricData {

    /**
     * 指标名称
     */
    private String name;

    /**
     * 指标值
     */
    private double value;

    /**
     * 时间戳（毫秒）
     */
    private long timestamp;

    /**
     * 指标标签（可选）
     */
    private String tags;

    // Constructor
    public MetricData() {}

    public MetricData(String name, double value, long timestamp, String tags) {
        this.name = name;
        this.value = value;
        this.timestamp = timestamp;
        this.tags = tags;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    // Builder pattern support
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 预定义的指标名称常量
     */
    public static class Metrics {
        // CPU 指标
        public static final String CPU_USAGE = "cpu.usage";
        public static final String CPU_LOAD_1MIN = "cpu.load.1min";
        public static final String CPU_LOAD_5MIN = "cpu.load.5min";
        public static final String CPU_LOAD_15MIN = "cpu.load.15min";
        public static final String CPU_CORES = "cpu.cores";

        // 内存指标
        public static final String MEMORY_USAGE = "memory.usage";
        public static final String MEMORY_USED = "memory.used";
        public static final String MEMORY_TOTAL = "memory.total";
        public static final String MEMORY_FREE = "memory.free";
        public static final String MEMORY_AVAILABLE = "memory.available";
        public static final String MEMORY_SWAP_USED = "memory.swap.used";
        public static final String MEMORY_SWAP_TOTAL = "memory.swap.total";

        // 磁盘指标
        public static final String DISK_USAGE = "disk.usage";
        public static final String DISK_USED = "disk.used";
        public static final String DISK_TOTAL = "disk.total";
        public static final String DISK_FREE = "disk.free";
        public static final String DISK_READS = "disk.reads";
        public static final String DISK_WRITES = "disk.writes";

        // 网络指标
        public static final String NETWORK_RX_BYTES = "network.rx.bytes";
        public static final String NETWORK_TX_BYTES = "network.tx.bytes";
        public static final String NETWORK_RX_PACKETS = "network.rx.packets";
        public static final String NETWORK_TX_PACKETS = "network.tx.packets";
        public static final String NETWORK_RX_ERRORS = "network.rx.errors";
        public static final String NETWORK_TX_ERRORS = "network.tx.errors";
    }

    /**
     * 创建 CPU 使用率指标
     */
    public static MetricData cpuUsage(double value) {
        return MetricData.builder()
                .name(Metrics.CPU_USAGE)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建内存使用率指标
     */
    public static MetricData memoryUsage(double value) {
        return MetricData.builder()
                .name(Metrics.MEMORY_USAGE)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建磁盘使用率指标
     */
    public static MetricData diskUsage(String mountPoint, double value) {
        return MetricData.builder()
                .name(Metrics.DISK_USAGE)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .tags("mount=" + mountPoint)
                .build();
    }

    /**
     * 创建网络接收字节数指标
     */
    public static MetricData networkRxBytes(String interfaceName, long value) {
        return MetricData.builder()
                .name(Metrics.NETWORK_RX_BYTES)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .tags("interface=" + interfaceName)
                .build();
    }

    /**
     * 创建网络发送字节数指标
     */
    public static MetricData networkTxBytes(String interfaceName, long value) {
        return MetricData.builder()
                .name(Metrics.NETWORK_TX_BYTES)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .tags("interface=" + interfaceName)
                .build();
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String name;
        private double value;
        private long timestamp;
        private String tags;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tags(String tags) {
            this.tags = tags;
            return this;
        }

        public MetricData build() {
            return new MetricData(name, value, timestamp, tags);
        }
    }
}
