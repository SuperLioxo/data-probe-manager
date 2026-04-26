package com.lixin.probe.dto;

import java.util.Map;

/**
 * 实时指标数据DTO
 *
 * @author Claude Code
 * @date 2026-04-13
 */
public class MetricsData {

    private Double cpuUsage;
    private Integer cpuCores;
    private Double cpuLoad1min;
    private Double cpuLoad5min;
    private Double cpuLoad15min;

    private Double memoryUsage;
    private Long memoryTotal;
    private Long memoryAvailable;
    private Long memoryUsed;

    private Double diskUsage;
    private Long diskUsed;
    private Long diskTotal;

    private Double networkIn;
    private Double networkOut;
    private Long networkRxBytes;
    private Long networkTxBytes;
    private Integer networkRxErrors;
    private Integer networkTxErrors;

    // JVM指标（可选）
    private Double jvmHeapUsage;
    private Long jvmHeapMax;
    private Double jvmThreadCount;
    private Long jvmClassLoaded;

    // OS指标（可选）
    private Integer osProcessCount;
    private Integer osThreadCount;
    private Long osUptimeSeconds;

    // 进程指标（可选）
    private Double processCpuUsage;
    private Long processMemoryResident;
    private Long processJvmUptime;
    private Integer processId;

    // 构造函数
    public MetricsData() {}

    // Getter和Setter方法
    public Double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(Double cpuUsage) { this.cpuUsage = cpuUsage; }

    public Integer getCpuCores() { return cpuCores; }
    public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }

    public Double getCpuLoad1min() { return cpuLoad1min; }
    public void setCpuLoad1min(Double cpuLoad1min) { this.cpuLoad1min = cpuLoad1min; }

    public Double getCpuLoad5min() { return cpuLoad5min; }
    public void setCpuLoad5min(Double cpuLoad5min) { this.cpuLoad5min = cpuLoad5min; }

    public Double getCpuLoad15min() { return cpuLoad15min; }
    public void setCpuLoad15min(Double cpuLoad15min) { this.cpuLoad15min = cpuLoad15min; }

    public Double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(Double memoryUsage) { this.memoryUsage = memoryUsage; }

    public Long getMemoryTotal() { return memoryTotal; }
    public void setMemoryTotal(Long memoryTotal) { this.memoryTotal = memoryTotal; }

    public Long getMemoryAvailable() { return memoryAvailable; }
    public void setMemoryAvailable(Long memoryAvailable) { this.memoryAvailable = memoryAvailable; }

    public Long getMemoryUsed() { return memoryUsed; }
    public void setMemoryUsed(Long memoryUsed) { this.memoryUsed = memoryUsed; }

    public Double getDiskUsage() { return diskUsage; }
    public void setDiskUsage(Double diskUsage) { this.diskUsage = diskUsage; }

    public Long getDiskUsed() { return diskUsed; }
    public void setDiskUsed(Long diskUsed) { this.diskUsed = diskUsed; }

    public Long getDiskTotal() { return diskTotal; }
    public void setDiskTotal(Long diskTotal) { this.diskTotal = diskTotal; }

    public Double getNetworkIn() { return networkIn; }
    public void setNetworkIn(Double networkIn) { this.networkIn = networkIn; }

    public Double getNetworkOut() { return networkOut; }
    public void setNetworkOut(Double networkOut) { this.networkOut = networkOut; }

    public Long getNetworkRxBytes() { return networkRxBytes; }
    public void setNetworkRxBytes(Long networkRxBytes) { this.networkRxBytes = networkRxBytes; }

    public Long getNetworkTxBytes() { return networkTxBytes; }
    public void setNetworkTxBytes(Long networkTxBytes) { this.networkTxBytes = networkTxBytes; }

    public Integer getNetworkRxErrors() { return networkRxErrors; }
    public void setNetworkRxErrors(Integer networkRxErrors) { this.networkRxErrors = networkRxErrors; }

    public Integer getNetworkTxErrors() { return networkTxErrors; }
    public void setNetworkTxErrors(Integer networkTxErrors) { this.networkTxErrors = networkTxErrors; }

    public Double getJvmHeapUsage() { return jvmHeapUsage; }
    public void setJvmHeapUsage(Double jvmHeapUsage) { this.jvmHeapUsage = jvmHeapUsage; }

    public Long getJvmHeapMax() { return jvmHeapMax; }
    public void setJvmHeapMax(Long jvmHeapMax) { this.jvmHeapMax = jvmHeapMax; }

    public Double getJvmThreadCount() { return jvmThreadCount; }
    public void setJvmThreadCount(Double jvmThreadCount) { this.jvmThreadCount = jvmThreadCount; }

    public Long getJvmClassLoaded() { return jvmClassLoaded; }
    public void setJvmClassLoaded(Long jvmClassLoaded) { this.jvmClassLoaded = jvmClassLoaded; }

    public Integer getOsProcessCount() { return osProcessCount; }
    public void setOsProcessCount(Integer osProcessCount) { this.osProcessCount = osProcessCount; }

    public Integer getOsThreadCount() { return osThreadCount; }
    public void setOsThreadCount(Integer osThreadCount) { this.osThreadCount = osThreadCount; }

    public Long getOsUptimeSeconds() { return osUptimeSeconds; }
    public void setOsUptimeSeconds(Long osUptimeSeconds) { this.osUptimeSeconds = osUptimeSeconds; }

    public Double getProcessCpuUsage() { return processCpuUsage; }
    public void setProcessCpuUsage(Double processCpuUsage) { this.processCpuUsage = processCpuUsage; }

    public Long getProcessMemoryResident() { return processMemoryResident; }
    public void setProcessMemoryResident(Long processMemoryResident) { this.processMemoryResident = processMemoryResident; }

    public Long getProcessJvmUptime() { return processJvmUptime; }
    public void setProcessJvmUptime(Long processJvmUptime) { this.processJvmUptime = processJvmUptime; }

    public Integer getProcessId() { return processId; }
    public void setProcessId(Integer processId) { this.processId = processId; }
}
