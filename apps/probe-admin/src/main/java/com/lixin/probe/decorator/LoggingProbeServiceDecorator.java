package com.lixin.probe.decorator;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

/**
 * 探针服务日志装饰器
 *
 * <p>为探针服务添加详细的日志记录功能，包括：
 * <ul>
 *   <li>方法调用日志：记录方法名和参数</li>
 *   <li>执行时间：记录方法执行耗时</li>
 *   <li>返回结果：记录返回值（可选）</li>
 *   <li>异常日志：记录异常堆栈</li>
 *   <li>MDC支持：支持分布式追踪上下文</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class LoggingProbeServiceDecorator extends ProbeServiceDecorator {

    private static final Logger log = LoggerFactory.getLogger(LoggingProbeServiceDecorator.class);
    private static final Logger performanceLog = LoggerFactory.getLogger("performance.probe");

    /**
     * 构造日志装饰器
     *
     * @param delegate 被装饰的服务
     */
    public LoggingProbeServiceDecorator(ProbeService delegate) {
        super(delegate);
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        return logWithTiming("getPage",
                () -> String.format("pageNum=%d, pageSize=%d", pageNum, pageSize),
                () -> delegate.getPage(pageNum, pageSize));
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type) {
        return logWithTiming("getPage",
                () -> String.format("pageNum=%d, pageSize=%d, name=%s, status=%s, type=%s",
                        pageNum, pageSize, name, status, type),
                () -> delegate.getPage(pageNum, pageSize, name, status, type));
    }

    @Override
    public Probe getById(Long id) {
        return logWithTiming("getById",
                () -> "id=" + id,
                () -> delegate.getById(id));
    }

    @Override
    public Probe getByProbeKey(String probeKey) {
        return logWithTiming("getByProbeKey",
                () -> "probeKey=" + probeKey,
                () -> delegate.getByProbeKey(probeKey));
    }

    @Override
    public void create(Probe probe) {
        logWithTiming("create",
                () -> String.format("probeKey=%s, name=%s", probe.getProbeKey(), probe.getName()),
                () -> {
                    delegate.create(probe);
                    return null;
                });
    }

    @Override
    public void update(Probe probe) {
        logWithTiming("update",
                () -> String.format("id=%d, probeKey=%s", probe.getId(), probe.getProbeKey()),
                () -> {
                    delegate.update(probe);
                    return null;
                });
    }

    @Override
    public void delete(Long id) {
        logWithTiming("delete",
                () -> "id=" + id,
                () -> {
                    delegate.delete(id);
                    return null;
                });
    }

    @Override
    public void updateHeartbeat(String probeKey) {
        // 心跳日志使用debug级别，避免日志过多
        if (log.isDebugEnabled()) {
            logWithTiming("updateHeartbeat",
                    () -> "probeKey=" + probeKey,
                    () -> {
                        delegate.updateHeartbeat(probeKey);
                        return null;
                    },
                    true); // 使用debug级别
        } else {
            delegate.updateHeartbeat(probeKey);
        }
    }

    @Override
    public List<Probe> batchCreate(List<Probe> probes) {
        return logWithTiming("batchCreate",
                () -> "count=" + probes.size(),
                () -> delegate.batchCreate(probes));
    }

    @Override
    public byte[] exportProbesToExcel(String name, String status, String type) {
        return logWithTiming("exportProbesToExcel",
                () -> String.format("name=%s, status=%s, type=%s", name, status, type),
                () -> delegate.exportProbesToExcel(name, status, type));
    }

    @Override
    public String exportProbesToJson(String name, String status, String type) {
        return logWithTiming("exportProbesToJson",
                () -> String.format("name=%s, status=%s, type=%s", name, status, type),
                () -> delegate.exportProbesToJson(name, status, type));
    }

    /**
     * 带计时和日志记录的方法执行
     *
     * @param methodName 方法名
     * @param paramsSupplier 参数字符串提供者
     * @param action 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    private <T> T logWithTiming(String methodName,
                                java.util.function.Supplier<String> paramsSupplier,
                                java.util.function.Supplier<T> action) {
        return logWithTiming(methodName, paramsSupplier, action, false);
    }

    /**
     * 带计时和日志记录的方法执行
     *
     * @param methodName 方法名
     * @param paramsSupplier 参数字符串提供者
     * @param action 要执行的操作
     * @param debugOnly 是否只使用debug级别日志
     * @param <T> 返回类型
     * @return 操作结果
     */
    private <T> T logWithTiming(String methodName,
                                java.util.function.Supplier<String> paramsSupplier,
                                java.util.function.Supplier<T> action,
                                boolean debugOnly) {
        long startTime = System.currentTimeMillis();
        String params = paramsSupplier.get();

        // 设置MDC
        MDC.put("method", methodName);
        MDC.put("params", params);

        try {
            if (!debugOnly) {
                log.info("调用方法: {}({})", methodName, params);
            } else {
                log.debug("调用方法: {}({})", methodName, params);
            }

            T result = action.get();

            long duration = System.currentTimeMillis() - startTime;

            if (!debugOnly) {
                log.info("方法完成: {}, 耗时={}ms", methodName, duration);
                performanceLog.info("ProbeService.{} 耗时: {}ms", methodName, duration);
            } else {
                log.debug("方法完成: {}, 耗时={}ms", methodName, duration);
            }

            // 记录慢查询（超过1秒）
            if (duration > 1000) {
                log.warn("检测到慢操作: {} 耗时: {}ms, 参数: {}", methodName, duration, params);
            }

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("方法执行异常: {}, 耗时={}ms, 参数: {}", methodName, duration, params, e);
            throw e;

        } finally {
            MDC.remove("method");
            MDC.remove("params");
        }
    }

    @Override
    public List<Probe> list() {
        return logWithTiming("list", () -> "", () -> delegate.list());
    }

    @Override
    public List<Probe> listByIds(List<Long> ids) {
        return logWithTiming("listByIds", () -> "ids=" + ids, () -> delegate.listByIds(ids));
    }

    @Override
    public List<String> getAllProbeKeys() {
        return logWithTiming("getAllProbeKeys", () -> "", () -> delegate.getAllProbeKeys());
    }

    @Override
    public List<com.lixin.probe.dto.DatabaseTypeInfo> getAvailableDatabaseTypes() {
        return logWithTiming("getAvailableDatabaseTypes", () -> "", () -> delegate.getAvailableDatabaseTypes());
    }

    @Override
    public Probe getSystemProbeByIp(String hostIp) {
        return logWithTiming("getSystemProbeByIp", () -> "hostIp=" + hostIp, () -> delegate.getSystemProbeByIp(hostIp));
    }

    @Override
    public Probe getSystemProbeByIpExclude(String hostIp, String excludeProbeKey) {
        return logWithTiming("getSystemProbeByIpExclude",
                () -> String.format("hostIp=%s, excludeProbeKey=%s", hostIp, excludeProbeKey),
                () -> delegate.getSystemProbeByIpExclude(hostIp, excludeProbeKey));
    }

    @Override
    public List<Probe> getOnlineProbes() {
        return logWithTiming("getOnlineProbes", () -> "", () -> delegate.getOnlineProbes());
    }
}
