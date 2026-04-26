package com.lixin.probe.agent.collector;

import com.lixin.probe.agent.udp.MetricData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统指标采集器
 * 使用 OSHI 库采集系统监控指标
 */
@Component
public class SystemMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsCollector.class);
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;
    private long[] prevTicks;
    private final boolean isWsl;

    // 网络速率计算（保存上一次采样值）
    // 使用LRU策略防止内存泄漏（最多缓存100个网络接口）
    // 改为静态变量，确保Agent重启后历史数据保留，网络速率能正常计算
    private static final int MAX_NETWORK_STATS = 100;
    private static final Map<String, NetworkStatsSnapshot> prevNetworkStats =
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, NetworkStatsSnapshot> eldest) {
                if (size() > MAX_NETWORK_STATS) {
                    log.warn("网络统计缓存已满，移除最老的条目: {}", eldest.getKey());
                    return true;
                }
                return false;
            }
        };

    // CPU初始化Future（异步初始化，避免阻塞）
    private final AtomicReference<CompletableFuture<Void>> cpuInitFuture = new AtomicReference<>();

    public SystemMetricsCollector() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.isWsl = detectWslEnvironment();

        // 异步初始化CPU采样（避免阻塞）
        initCpuSamplingAsync();
    }

    /**
     * 异步初始化CPU采样
     * 在后台线程完成首次采样，避免阻塞主线程
     */
    private void initCpuSamplingAsync() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                CentralProcessor processor = hardware.getProcessor();
                // 预采样：触发CPU tick采集，首次需要500ms等待
                processor.getSystemCpuLoadTicks();
                Thread.sleep(500);
                log.debug("CPU采样初始化完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("CPU采样初始化被中断", e);
            } catch (Exception e) {
                log.error("CPU采样初始化失败", e);
            }
        });

        cpuInitFuture.set(future);
    }

    /**
     * 网络统计快照
     */
    private static class NetworkStatsSnapshot {
        long rxBytes;
        long txBytes;
        long timestamp;

        NetworkStatsSnapshot(long rxBytes, long txBytes, long timestamp) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
            this.timestamp = timestamp;
        }
    }

    /**
     * 检测是否为WSL环境
     */
    private boolean detectWslEnvironment() {
        try {
            String version = Files.readString(Paths.get("/proc/version"));
            if (version.contains("Microsoft") || version.contains("WSL")) {
                log.info("检测到WSL环境，将使用df命令采集磁盘数据");
                return true;
            }
        } catch (Exception e) {
            // 忽略异常，返回false
        }
        return false;
    }

    /**
     * 采集所有系统指标
     *
     * @return 指标数据列表
     */
    public List<MetricData> collectMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            // 1. CPU 指标
            metrics.addAll(collectCpuMetrics());

            // 2. 内存指标
            metrics.addAll(collectMemoryMetrics());

            // 3. 磁盘指标
            metrics.addAll(collectDiskMetrics());

            // 4. 网络指标
            metrics.addAll(collectNetworkMetrics());

            // 5. JVM 指标
            metrics.addAll(collectJvmMetrics());

            // 6. 操作系统指标（新增）
            metrics.addAll(collectOsMetrics());

            // 7. 进程信息指标（新增）
            metrics.addAll(collectProcessMetrics());

        } catch (Exception e) {
            log.error("采集系统指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集 CPU 指标
     */
    private List<MetricData> collectCpuMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            CentralProcessor processor = hardware.getProcessor();

            // 获取 CPU 使用率（getProcessorCpuLoadBetweenTicks内部已经乘以100）
            double cpuUsage = getProcessorCpuLoadBetweenTicks(processor);
            metrics.add(MetricData.cpuUsage(cpuUsage));

            // 获取 CPU 负载
            double[] loadAverage = processor.getSystemLoadAverage(3);
            if (loadAverage.length >= 1 && loadAverage[0] >= 0) {
                metrics.add(createMetric("cpu.load.1min", loadAverage[0]));
            }
            if (loadAverage.length >= 2 && loadAverage[1] >= 0) {
                metrics.add(createMetric("cpu.load.5min", loadAverage[1]));
            }
            if (loadAverage.length >= 3 && loadAverage[2] >= 0) {
                metrics.add(createMetric("cpu.load.15min", loadAverage[2]));
            }

            // CPU 核心数
            int cores = processor.getLogicalProcessorCount();
            metrics.add(createMetric("cpu.cores", cores));

        } catch (Exception e) {
            log.error("采集 CPU 指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集内存指标
     */
    private List<MetricData> collectMemoryMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            GlobalMemory memory = hardware.getMemory();

            // 总内存和已用内存（字节）
            long total = memory.getTotal();
            long available = memory.getAvailable();
            long used = total - available;

            metrics.add(createMetric("memory.total", total));
            metrics.add(createMetric("memory.used", used));
            metrics.add(createMetric("memory.available", available));
            metrics.add(createMetric("memory.free", memory.getAvailable()));

            // 内存使用率（百分比）
            double usage = (double) used / total * 100;
            metrics.add(createMetric("memory.usage", usage));

            // 交换空间
            long swapTotal = memory.getVirtualMemory().getSwapTotal();
            long swapUsed = memory.getVirtualMemory().getSwapUsed();
            metrics.add(createMetric("memory.swap.total", swapTotal));
            metrics.add(createMetric("memory.swap.used", swapUsed));

        } catch (Exception e) {
            log.error("采集内存指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集磁盘指标
     */
    private List<MetricData> collectDiskMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            // WSL环境下使用df命令获取准确的磁盘数据
            if (isWsl) {
                return collectDiskMetricsFromDf(metrics);
            }

            // 非WSL环境使用OSHI库
            FileSystem fileSystem = operatingSystem.getFileSystem();
            Iterable<OSFileStore> fileStores = fileSystem.getFileStores();

            boolean rootCollected = false;

            for (OSFileStore store : fileStores) {
                String mountPoint = store.getMount();

                // 跳过特殊和虚拟文件系统
                if (mountPoint == null || mountPoint.isEmpty() ||
                        mountPoint.equals("/proc") ||
                        mountPoint.equals("/sys") ||
                        mountPoint.equals("/dev") ||
                        mountPoint.equals("/run") ||
                        mountPoint.startsWith("/sys/fs/") ||
                        mountPoint.startsWith("/run/user/")) {
                    continue;
                }

                // 过滤掉虚拟文件系统和特殊挂载点
                String name = store.getName();
                if (name != null && (name.contains("overlay") ||
                                   name.contains("docker") ||
                                   name.contains("snap") ||
                                   name.contains("tmpfs"))) {
                    continue;
                }

                long total = store.getTotalSpace();

                // 跳过容量太小的分区（小于1GB）
                if (total < 1024 * 1024 * 1024) {
                    continue;
                }

                long usable = store.getUsableSpace();
                long used = total - usable;

                // 磁盘使用率（百分比）
                double usage = total > 0 ? (double) used / total * 100 : 0;

                String mount = mountPoint.replace("/", "_").replaceAll("^_", "");
                if (mount.isEmpty()) {
                    mount = "root";
                }

                // 只记录根分区或实际的主要分区
                if ("/".equals(mountPoint) || !rootCollected) {
                    metrics.add(MetricData.diskUsage(mount, usage));
                    metrics.add(createMetric("disk.used." + mount, used));
                    metrics.add(createMetric("disk.total." + mount, total));
                    metrics.add(createMetric("disk.free." + mount, usable));

                    if ("/".equals(mountPoint)) {
                        rootCollected = true;
                    }
                }
            }

        } catch (Exception e) {
            log.error("采集磁盘指标失败", e);
        }

        return metrics;
    }

    /**
     * 使用df命令采集磁盘指标（WSL专用）
     * 在WSL环境下，OSHI的getUsableSpace()返回的值不准确
     * 使用df命令可以直接获取准确的磁盘使用率
     */
    private List<MetricData> collectDiskMetricsFromDf(List<MetricData> metrics) {
        try {
            // 使用df命令获取磁盘信息（-B1显示字节数）
            Process process = Runtime.getRuntime().exec(new String[]{"df", "-B1", "-x", "squashfs", "-x", "tmpfs", "-x", "devtmpfs"});

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean firstSkipped = false;

                while ((line = reader.readLine()) != null) {
                    // 跳过标题行
                    if (!firstSkipped) {
                        firstSkipped = true;
                        continue;
                    }

                    // 解析df输出
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 6) {
                        try {
                            long total = Long.parseLong(parts[1]);
                            long used = Long.parseLong(parts[2]);
                            long available = Long.parseLong(parts[3]);
                            String usagePercent = parts[4]; // "3%" 格式
                            String mountPoint = parts[5];

                            // 跳过特殊挂载点
                            if (mountPoint.equals("/proc") || mountPoint.equals("/sys") ||
                                mountPoint.equals("/dev") || mountPoint.startsWith("/sys/fs/") ||
                                mountPoint.startsWith("/run/user/") || mountPoint.startsWith("/snap") ||
                                mountPoint.contains("/docker") || mountPoint.contains("/wsl")) {
                                continue;
                            }

                            // 跳过容量太小的分区（小于1GB）
                            if (total < 1024 * 1024 * 1024) {
                                continue;
                            }

                            // 解析使用率百分比
                            double usage = 0.0;
                            if (usagePercent.endsWith("%")) {
                                usage = Double.parseDouble(usagePercent.substring(0, usagePercent.length() - 1));
                            } else {
                                usage = total > 0 ? (double) used / total * 100 : 0;
                            }

                            // 标准化挂载点名称
                            String mount = mountPoint.replace("/", "_").replaceAll("^_", "");
                            if (mount.isEmpty()) {
                                mount = "root";
                            }

                            // 创建指标数据
                            metrics.add(MetricData.diskUsage(mount, usage));
                            metrics.add(createMetric("disk.used." + mount, used));
                            metrics.add(createMetric("disk.total." + mount, total));
                            metrics.add(createMetric("disk.free." + mount, available));

                            log.debug("WSL磁盘指标: mount={}, usage={}%, used={}GB, total={}GB, available={}GB",
                                mount, usage,
                                used / (1024.0 * 1024 * 1024),
                                total / (1024.0 * 1024 * 1024),
                                available / (1024.0 * 1024 * 1024));

                        } catch (NumberFormatException e) {
                            log.warn("解析磁盘数据失败: {}", line);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("df命令执行失败，退出码: {}", exitCode);
            }

        } catch (Exception e) {
            log.error("使用df命令采集磁盘指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集网络指标
     */
    private List<MetricData> collectNetworkMetrics() {
        List<MetricData> metrics = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        try {
            // 记录map状态
            log.info("开始采集网络指标，prevNetworkStats 大小: {}, keys: {}",
                    prevNetworkStats.size(), prevNetworkStats.keySet());

            // 使用OSHI获取网络接口统计信息
            List<oshi.hardware.NetworkIF> networkIFs = hardware.getNetworkIFs();

            for (oshi.hardware.NetworkIF netIF : networkIFs) {
                String name = netIF.getName();

                // 跳过回环接口（名称通常以 lo 开头）
                if (name != null && name.startsWith("lo")) {
                    continue;
                }

                // 获取网络统计字节数
                long rxBytes = netIF.getBytesRecv();
                long txBytes = netIF.getBytesSent();
                long rxPackets = netIF.getPacketsRecv();
                long txPackets = netIF.getPacketsSent();
                long rxErrors = netIF.getInErrors();
                long txErrors = netIF.getOutErrors();

                // 计算网络速率（bytes/s）
                NetworkStatsSnapshot prev = prevNetworkStats.get(name);
                log.info("网络接口 [{}]: RX bytes={}, TX bytes={}, 历史数据={}, map大小={}",
                    name, rxBytes, txBytes, prev != null ? "存在" : "不存在", prevNetworkStats.size());

                if (prev != null && currentTime > prev.timestamp) {
                    long timeDelta = currentTime - prev.timestamp; // 毫秒
                    log.info("网络接口 [{}]: 时间差={}ms", name, timeDelta);
                    if (timeDelta > 0) {
                        // 计算速率：bytes/second
                        long rxDelta = rxBytes - prev.rxBytes;
                        long txDelta = txBytes - prev.txBytes;
                        double rxRate = rxDelta * 1000.0 / timeDelta; // bytes/s
                        double txRate = txDelta * 1000.0 / timeDelta; // bytes/s

                        log.info("网络接口 [{}]: RX delta={}, TX delta={}, 计算速率: RX={} KB/s, TX={} KB/s",
                            name, rxDelta, txDelta, String.format("%.2f", rxRate / 1024), String.format("%.2f", txRate / 1024));

                        // 只上报正速率（避免网络重置导致的负值）
                        if (rxRate >= 0) {
                            MetricData rxRateMetric = createMetric("network.rx.rate", rxRate);
                            rxRateMetric.setTags("interface=" + name);
                            metrics.add(rxRateMetric);
                            log.info("添加网络RX速率指标: interface={}, rate={} KB/s", name, String.format("%.2f", rxRate / 1024));
                        }
                        if (txRate >= 0) {
                            MetricData txRateMetric = createMetric("network.tx.rate", txRate);
                            txRateMetric.setTags("interface=" + name);
                            metrics.add(txRateMetric);
                            log.info("添加网络TX速率指标: interface={}, rate={} KB/s", name, String.format("%.2f", txRate / 1024));
                        }
                    } else {
                        log.warn("网络接口 [{}]: 时间差<=0，跳过速率计算", name);
                    }
                } else {
                    log.info("网络接口 [{}]: 无历史数据，跳过速率计算（首次采集）", name);
                }

                // 保存当前快照
                NetworkStatsSnapshot snapshot = new NetworkStatsSnapshot(rxBytes, txBytes, currentTime);
                prevNetworkStats.put(name, snapshot);
                log.info("网络接口 [{}]: 已保存快照到map，map当前大小: {}, keys: {}",
                        name, prevNetworkStats.size(), prevNetworkStats.keySet());

                // 保留累计字节数（用于历史趋势）
                metrics.add(MetricData.networkRxBytes(name, rxBytes));
                metrics.add(MetricData.networkTxBytes(name, txBytes));

                // 其他统计信息
                MetricData rxPacketsMetric = createMetric("network.rx.packets", rxPackets);
                rxPacketsMetric.setTags("interface=" + name);
                metrics.add(rxPacketsMetric);

                MetricData txPacketsMetric = createMetric("network.tx.packets", txPackets);
                txPacketsMetric.setTags("interface=" + name);
                metrics.add(txPacketsMetric);

                MetricData rxErrorsMetric = createMetric("network.rx.errors", rxErrors);
                rxErrorsMetric.setTags("interface=" + name);
                metrics.add(rxErrorsMetric);

                MetricData txErrorsMetric = createMetric("network.tx.errors", txErrors);
                txErrorsMetric.setTags("interface=" + name);
                metrics.add(txErrorsMetric);
            }

        } catch (Exception e) {
            log.error("采集网络指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集 JVM 指标
     */
    private List<MetricData> collectJvmMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            // 堆内存指标
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

            // 转换为 MB
            double heapUsed = heapUsage.getUsed() / (1024.0 * 1024.0);
            double heapMax = heapUsage.getMax() / (1024.0 * 1024.0);
            double heapCommitted = heapUsage.getCommitted() / (1024.0 * 1024.0);
            double heapInit = heapUsage.getInit() / (1024.0 * 1024.0);

            metrics.add(createMetric("jvm.heap.used", heapUsed));
            metrics.add(createMetric("jvm.heap.max", heapMax));
            metrics.add(createMetric("jvm.heap.committed", heapCommitted));
            metrics.add(createMetric("jvm.heap.init", heapInit));

            // 堆内存使用率
            if (heapMax > 0) {
                double heapUsagePercent = (heapUsed / heapMax) * 100;
                metrics.add(createMetric("jvm.heap.usage", heapUsagePercent));
            }

            // 非堆内存指标
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            double nonHeapUsed = nonHeapUsage.getUsed() / (1024.0 * 1024.0);
            double nonHeapMax = nonHeapUsage.getMax() / (1024.0 * 1024.0);

            metrics.add(createMetric("jvm.non-heap.used", nonHeapUsed));
            metrics.add(createMetric("jvm.non-heap.max", nonHeapMax));

            // 线程数
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();
            long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

            metrics.add(createMetric("jvm.thread.count", threadCount));
            metrics.add(createMetric("jvm.thread.peak", peakThreadCount));
            metrics.add(createMetric("jvm.thread.totalStarted", totalStartedThreadCount));

            // 类加载指标
            ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
            int loadedClassCount = classLoadingBean.getLoadedClassCount();
            long totalLoadedClassCount = classLoadingBean.getTotalLoadedClassCount();
            long unloadedClassCount = classLoadingBean.getUnloadedClassCount();

            metrics.add(createMetric("jvm.class.loaded", loadedClassCount));
            metrics.add(createMetric("jvm.class.total", totalLoadedClassCount));
            metrics.add(createMetric("jvm.class.unloaded", unloadedClassCount));

            // 运行时指标
            Runtime runtime = Runtime.getRuntime();
            int availableProcessors = runtime.availableProcessors();
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long maxMemory = runtime.maxMemory() / (1024 * 1024);

            metrics.add(createMetric("jvm.runtime.processors", availableProcessors));
            metrics.add(createMetric("jvm.runtime.freeMemory", freeMemory));
            metrics.add(createMetric("jvm.runtime.totalMemory", totalMemory));
            metrics.add(createMetric("jvm.runtime.maxMemory", maxMemory));

        } catch (Exception e) {
            log.error("采集 JVM 指标失败", e);
        }

        return metrics;
    }

    /**
     * 采集操作系统信息指标（新增）
     */
    private List<MetricData> collectOsMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            // 操作系统信息
            String osName = operatingSystem.getFamily();
            String osVersion = operatingSystem.getVersionInfo().toString();
            int processCount = operatingSystem.getProcessCount();
            int threadCount = operatingSystem.getThreadCount();

            metrics.add(createMetric("os.process.count", processCount));
            metrics.add(createMetric("os.thread.count", threadCount));

            // 系统启动时间
            long systemBootTime = operatingSystem.getSystemUptime();
            metrics.add(createMetric("os.uptime.seconds", systemBootTime));

            // 服务器状态（1=正常运行）
            metrics.add(createMetric("os.server.status", 1));

        } catch (Exception e) {
            log.error("采集操作系统信息失败", e);
        }

        return metrics;
    }

    /**
     * 采集当前进程信息指标（新增）
     */
    private List<MetricData> collectProcessMetrics() {
        List<MetricData> metrics = new ArrayList<>();

        try {
            // 当前进程ID
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            metrics.add(createMetric("process.id", Double.parseDouble(pid)));

            // JVM启动时间（毫秒）
            long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
            long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime();

            metrics.add(createMetric("process.jvm.startTime", jvmStartTime));
            metrics.add(createMetric("process.jvm.uptime.seconds", jvmUptime / 1000.0));

            // 进程CPU使用率
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double processCpuLoad = osBean.getProcessCpuLoad() * 100;
            if (!Double.isNaN(processCpuLoad)) {
                metrics.add(createMetric("process.cpu.usage", processCpuLoad));
            }

            // 进程CPU时间
            long processCpuTime = osBean.getProcessCpuTime() / 1000000; // 纳秒转毫秒
            metrics.add(createMetric("process.cpu.time.ms", processCpuTime));

            // 进程内存使用（修正：使用RSS常驻内存大小）
            long processResidentMemory = 0;
            long processVirtualMemory = osBean.getCommittedVirtualMemorySize() / (1024 * 1024); // MB

            // 尝试获取进程的实际物理内存使用
            try {
                // 使用JVM的内存管理API
                Runtime runtime = Runtime.getRuntime();
                long jvmTotalMemory = runtime.totalMemory() / (1024 * 1024); // MB
                long jvmFreeMemory = runtime.freeMemory() / (1024 * 1024); // MB
                long jvmUsedMemory = jvmTotalMemory - jvmFreeMemory; // MB
                processResidentMemory = jvmUsedMemory;
            } catch (Exception e) {
                // 如果获取失败，使用虚拟内存作为fallback
                processResidentMemory = processVirtualMemory;
            }

            metrics.add(createMetric("process.memory.resident.mb", processResidentMemory));
            metrics.add(createMetric("process.memory.virtual.mb", processVirtualMemory));

            // 系统总内存（作为参考）
            long systemTotalMemory = osBean.getTotalPhysicalMemorySize() / (1024 * 1024); // MB
            metrics.add(createMetric("system.memory.total.mb", systemTotalMemory));

            // 文件描述符（Unix系统）
            try {
                java.lang.reflect.Field fdField = osBean.getClass().getDeclaredField("openFileDescriptorCount");
                fdField.setAccessible(true);
                long openFd = fdField.getLong(osBean);
                metrics.add(createMetric("process.fd.open", openFd));
            } catch (Exception e) {
                // 文件描述符信息在非Unix系统可能不可用
            }

        } catch (Exception e) {
            log.error("采集进程信息失败", e);
        }

        return metrics;
    }

    /**
     * 获取 CPU 使用率
     * 需要两次采样计算
     * 返回值范围: 0-100（百分比）
     *
     * 改进：首次调用使用异步初始化，避免阻塞
     */
    private double getProcessorCpuLoadBetweenTicks(CentralProcessor processor) {
        try {
            // 检查CPU初始化是否完成
            CompletableFuture<Void> initFuture = cpuInitFuture.get();
            if (initFuture != null && !initFuture.isDone()) {
                // 初始化未完成，返回0（不影响首次启动）
                log.debug("CPU采样初始化未完成，返回0");
                return 0;
            }

            // 第一次采样
            if (prevTicks == null) {
                prevTicks = processor.getSystemCpuLoadTicks();
                try {
                    Thread.sleep(500); // 等待 500ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            }

            // 第二次采样
            long[] ticks = processor.getSystemCpuLoadTicks();

            // OSHI 6.x: getSystemCpuLoadBetweenTicks() 返回 0.0-1.0 的小数
            double usage = processor.getSystemCpuLoadBetweenTicks(prevTicks);

            // 转换为百分比
            usage = usage * 100;

            prevTicks = ticks;

            // 确保返回值在合理范围内
            if (Double.isNaN(usage) || usage < 0 || usage > 100) {
                return 0;
            }

            return usage;
        } catch (Exception e) {
            log.error("获取CPU使用率失败", e);
            return 0;
        }
    }

    /**
     * 创建指标数据
     */
    private MetricData createMetric(String name, double value) {
        return MetricData.builder()
                .name(name)
                .value(value)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
