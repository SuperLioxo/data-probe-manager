package com.lixin.probe.decorator;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;

import java.util.List;

/**
 * 探针服务装饰器基类
 *
 * <p>实现装饰器模式，为ProbeService提供动态增强功能。
 * 所有装饰器都继承此类并 selectively 重写需要增强的方法。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * ProbeService service = new CachingProbeServiceDecorator(
 *     new LoggingProbeServiceDecorator(
 *         new MonitoringProbeServiceDecorator(
 *             probeServiceImpl
 *         )
 *     )
 * );
 * }</pre></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public abstract class ProbeServiceDecorator implements ProbeService {

    /**
     * 被装饰的原始服务
     */
    protected final ProbeService delegate;

    /**
     * 构造装饰器
     *
     * @param delegate 被装饰的服务
     */
    public ProbeServiceDecorator(ProbeService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate service cannot be null");
        }
        this.delegate = delegate;
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        return delegate.getPage(pageNum, pageSize);
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type) {
        return delegate.getPage(pageNum, pageSize, name, status, type);
    }

    @Override
    public Probe getById(Long id) {
        return delegate.getById(id);
    }

    @Override
    public Probe getByProbeKey(String probeKey) {
        return delegate.getByProbeKey(probeKey);
    }

    @Override
    public void create(Probe probe) {
        delegate.create(probe);
    }

    @Override
    public void update(Probe probe) {
        delegate.update(probe);
    }

    @Override
    public void delete(Long id) {
        delegate.delete(id);
    }

    @Override
    public void updateHeartbeat(String probeKey) {
        delegate.updateHeartbeat(probeKey);
    }

    @Override
    public List<Probe> batchCreate(List<Probe> probes) {
        return delegate.batchCreate(probes);
    }

    /**
     * 获取被装饰的服务
     *
     * @return 委托的服务对象
     */
    protected ProbeService getDelegate() {
        return delegate;
    }
}
