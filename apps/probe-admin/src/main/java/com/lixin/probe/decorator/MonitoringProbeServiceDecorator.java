package com.lixin.probe.decorator;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 探针服务监控装饰器
 *
 * <p>为探针服务添加性能监控指标，使用Micrometer收集：
 * <ul>
 *   <li>方法调用次数（Counter）</li>
 *   <li>方法执行时间（Timer）</li>
 *   <li>错误率（失败次数/总次数）</li>
 *   <li>当前正在执行的请求数</li>
 * </ul></p>
 *
 * <p>指标命名规范：
 * <pre>
 * probe.service.method.calls - 方法调用次数
 * probe.service.method.duration - 方法执行时间
 * probe.service.method.errors - 方法错误次数
 * </pre></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class MonitoringProbeServiceDecorator extends ProbeServiceDecorator {

    private static final Logger log = LoggerFactory.getLogger(MonitoringProbeServiceDecorator.class);

    private final MeterRegistry meterRegistry;

    // 指标名称前缀
    private static final String METRIC_PREFIX = "probe.service";

    public MonitoringProbeServiceDecorator(ProbeService delegate, MeterRegistry meterRegistry) {
        super(delegate);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        return monitor("getPage", "page", () -> delegate.getPage(pageNum, pageSize));
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type) {
        return monitor("getPage.filtered", "page", () ->
                delegate.getPage(pageNum, pageSize, name, status, type));
    }

    @Override
    public Probe getById(Long id) {
        return monitor("getById", "query", () -> delegate.getById(id));
    }

    @Override
    public Probe getByProbeKey(String probeKey) {
        return monitor("getByProbeKey", "query", () -> delegate.getByProbeKey(probeKey));
    }

    @Override
    public void create(Probe probe) {
        monitor("create", "mutation", () -> {
            delegate.create(probe);
            return null;
        });
    }

    @Override
    public void update(Probe probe) {
        monitor("update", "mutation", () -> {
            delegate.update(probe);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        monitor("delete", "mutation", () -> {
            delegate.delete(id);
            return null;
        });
    }

    @Override
    public void updateHeartbeat(String probeKey) {
        monitor("updateHeartbeat", "mutation", () -> {
            delegate.updateHeartbeat(probeKey);
            return null;
        });
    }

    @Override
    public List<Probe> batchCreate(List<Probe> probes) {
        return monitor("batchCreate", "mutation", () -> delegate.batchCreate(probes));
    }

    @Override
    public byte[] exportProbesToExcel(String name, String status, String type) {
        return monitor("exportProbesToExcel", "export", () -> delegate.exportProbesToExcel(name, status, type));
    }

    @Override
    public String exportProbesToJson(String name, String status, String type) {
        return monitor("exportProbesToJson", "export", () -> delegate.exportProbesToJson(name, status, type));
    }

    /**
     * 监控方法执行
     *
     * @param methodName 方法名
     * @param operationType 操作类型（query/mutation）
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    private <T> T monitor(String methodName, String operationType, java.util.function.Supplier<T> action) {
        // 构建指标标签
        String methodTagName = "method";
        String typeTagName = "type";
        String statusTagName = "status";

        Counter.Builder counterBuilder = Counter.builder(METRIC_PREFIX + ".calls")
                .tag(methodTagName, methodName)
                .tag(typeTagName, operationType)
                .description("ProbeService method call count");

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 执行操作
            T result = action.get();

            // 记录成功调用
            counterBuilder.tag(statusTagName, "success").register(meterRegistry).increment();

            // 记录执行时间
            sample.stop(Timer.builder(METRIC_PREFIX + ".duration")
                    .tag(methodTagName, methodName)
                    .tag(typeTagName, operationType)
                    .tag(statusTagName, "success")
                    .description("ProbeService method execution time")
                    .register(meterRegistry));

            return result;

        } catch (Exception e) {
            // 记录失败调用
            counterBuilder.tag(statusTagName, "error").register(meterRegistry).increment();

            // 记录错误执行时间
            sample.stop(Timer.builder(METRIC_PREFIX + ".duration")
                    .tag(methodTagName, methodName)
                    .tag(typeTagName, operationType)
                    .tag(statusTagName, "error")
                    .description("ProbeService method execution time")
                    .register(meterRegistry));

            log.error("方法执行监控捕获异常: method={}", methodName, e);
            throw e;
        }
    }

    /**
     * 获取方法调用次数
     *
     * @param methodName 方法名
     * @return 调用次数
     */
    public double getMethodCallCount(String methodName) {
        return meterRegistry.get(METRIC_PREFIX + ".calls")
                .tag("method", methodName)
                .counter()
                .count();
    }

    /**
     * 获取方法平均执行时间
     *
     * @param methodName 方法名
     * @return 平均执行时间（毫秒）
     */
    public double getMethodAverageDuration(String methodName) {
        return meterRegistry.get(METRIC_PREFIX + ".duration")
                .tag("method", methodName)
                .tag("status", "success")
                .timer()
                .mean(TimeUnit.MILLISECONDS);
    }

    @Override
    public List<Probe> list() {
        return monitor("list", "query", () -> delegate.list());
    }

    @Override
    public List<Probe> listByIds(List<Long> ids) {
        return monitor("listByIds", "query", () -> delegate.listByIds(ids));
    }

    @Override
    public List<String> getAllProbeKeys() {
        return monitor("getAllProbeKeys", "query", () -> delegate.getAllProbeKeys());
    }

    @Override
    public List<com.lixin.probe.dto.DatabaseTypeInfo> getAvailableDatabaseTypes() {
        return monitor("getAvailableDatabaseTypes", "query", () -> delegate.getAvailableDatabaseTypes());
    }

    @Override
    public Probe getSystemProbeByIp(String hostIp) {
        return monitor("getSystemProbeByIp", "query", () -> delegate.getSystemProbeByIp(hostIp));
    }

    @Override
    public Probe getSystemProbeByIpExclude(String hostIp, String excludeProbeKey) {
        return monitor("getSystemProbeByIpExclude", "query", () -> delegate.getSystemProbeByIpExclude(hostIp, excludeProbeKey));
    }

    @Override
    public List<Probe> getOnlineProbes() {
        return monitor("getOnlineProbes", "query", () -> delegate.getOnlineProbes());
    }
}
