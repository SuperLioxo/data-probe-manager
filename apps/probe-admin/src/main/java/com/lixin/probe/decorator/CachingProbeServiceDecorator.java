package com.lixin.probe.decorator;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 探针服务缓存装饰器
 *
 * <p>为探针服务添加缓存功能，减少数据库访问。
 * 使用Spring Cache抽象，支持多种缓存实现（Redis、Caffeine等）。</p>
 *
 * <p>缓存策略：
 * <ul>
 *   <li>getById(): 使用探针ID作为缓存键</li>
 *   <li>getByProbeKey(): 使用探针Key作为缓存键</li>
 *   <li>getPage(): 使用查询参数组合作为缓存键</li>
 *   <li>写操作(create/update/delete): 自动清除相关缓存</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class CachingProbeServiceDecorator extends ProbeServiceDecorator {

    private static final Logger log = LoggerFactory.getLogger(CachingProbeServiceDecorator.class);

    private static final String CACHE_NAME_PROBE = "probe";
    private static final String CACHE_NAME_PROBE_LIST = "probeList";

    private final CacheManager cacheManager;

    /**
     * 构造缓存装饰器
     *
     * @param delegate 被装饰的服务
     * @param cacheManager 缓存管理器
     */
    public CachingProbeServiceDecorator(ProbeService delegate, CacheManager cacheManager) {
        super(delegate);
        this.cacheManager = cacheManager;
    }

    @Override
    public Probe getById(Long id) {
        return getCached("id:" + id, () -> delegate.getById(id));
    }

    @Override
    public Probe getByProbeKey(String probeKey) {
        return getCached("key:" + probeKey, () -> delegate.getByProbeKey(probeKey));
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        String cacheKey = "page:" + pageNum + ":" + pageSize;
        return getCached(cacheKey, () -> delegate.getPage(pageNum, pageSize));
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type) {
        String cacheKey = String.format("page:%d:%d:%s:%s:%s",
                pageNum, pageSize,
                Objects.toString(name, ""),
                Objects.toString(status, ""),
                Objects.toString(type, ""));
        return getCached(cacheKey, () -> delegate.getPage(pageNum, pageSize, name, status, type));
    }

    @Override
    public void create(Probe probe) {
        delegate.create(probe);
        // 创建新探针后需要清除列表缓存，确保新探针出现在列表中
        evictListCache();
        log.info("创建探针，已清除列表缓存: probeKey={}", probe.getProbeKey());
    }

    @Override
    public void update(Probe probe) {
        delegate.update(probe);
        // 清除相关缓存
        evictProbeCache(probe.getId(), probe.getProbeKey());
        evictListCache();
        log.debug("更新探针，已清除缓存: probeKey={}", probe.getProbeKey());
    }

    @Override
    public void delete(Long id) {
        // 先查询以获取probeKey，用于清除缓存
        Probe probe = delegate.getById(id);
        delegate.delete(id);
        if (probe != null) {
            evictProbeCache(id, probe.getProbeKey());
        }
        evictListCache();
        log.debug("删除探针，已清除缓存: id={}", id);
    }

    @Override
    public void updateHeartbeat(String probeKey) {
        delegate.updateHeartbeat(probeKey);
        // 清除探针缓存和列表缓存，确保状态更新立即生效
        evictProbeCache(null, probeKey);
        evictListCache();
        log.debug("更新探针心跳，已清除缓存: probeKey={}", probeKey);
    }

    @Override
    public List<Probe> batchCreate(List<Probe> probes) {
        List<Probe> result = delegate.batchCreate(probes);
        evictListCache();
        log.debug("批量创建探针，已清除列表缓存: count={}", probes.size());
        return result;
    }

    @Override
    public byte[] exportProbesToExcel(String name, String status, String type) {
        // 导出操作不使用缓存，直接委托
        return delegate.exportProbesToExcel(name, status, type);
    }

    @Override
    public String exportProbesToJson(String name, String status, String type) {
        // 导出操作不使用缓存，直接委托
        return delegate.exportProbesToJson(name, status, type);
    }

    /**
     * 获取缓存数据
     *
     * @param key 缓存键
     * @param loader 数据加载器
     * @return 缓存的数据或加载的数据
     */
    private <T> T getCached(String key, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(CACHE_NAME_PROBE);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                T cached = (T) wrapper.get();
                if (cached != null) {
                    log.debug("缓存命中: key={}", key);
                    return cached;
                }
            }
        }

        log.debug("缓存未命中，加载数据: key={}", key);
        T value = loader.get();

        // 写入缓存
        if (cache != null && value != null) {
            cache.put(key, value);
            log.debug("数据已缓存: key={}", key);
        }

        return value;
    }

    /**
     * 清除探针缓存
     *
     * @param id 探针ID
     * @param probeKey 探针Key
     */
    private void evictProbeCache(Long id, String probeKey) {
        Cache cache = cacheManager.getCache(CACHE_NAME_PROBE);
        if (cache != null) {
            if (id != null) {
                cache.evict("id:" + id);
            }
            if (probeKey != null) {
                cache.evict("key:" + probeKey);
            }
        }
    }

    /**
     * 清除列表缓存
     */
    private void evictListCache() {
        // 清除probe缓存（因为列表数据存在probe缓存中）
        Cache probeCache = cacheManager.getCache(CACHE_NAME_PROBE);
        if (probeCache != null) {
            probeCache.clear();
            log.info("已清除probe缓存中的所有列表数据");
        }

        // 也清除probeList缓存（如果存在）
        Cache listCache = cacheManager.getCache(CACHE_NAME_PROBE_LIST);
        if (listCache != null) {
            listCache.clear();
            log.info("已清除probeList缓存中的所有数据");
        }
    }

    @Override
    public List<Probe> list() {
        log.debug("[CachingDecorator] 查询所有探针列表（无缓存）");
        return delegate.list();
    }

    @Override
    public List<Probe> listByIds(List<Long> ids) {
        log.debug("[CachingDecorator] 根据ID列表查询探针（无缓存）");
        return delegate.listByIds(ids);
    }

    @Override
    public List<String> getAllProbeKeys() {
        log.debug("[CachingDecorator] 获取所有探针的probeKey列表（无缓存）");
        return delegate.getAllProbeKeys();
    }

    @Override
    public List<com.lixin.probe.dto.DatabaseTypeInfo> getAvailableDatabaseTypes() {
        log.debug("[CachingDecorator] 获取可用的数据库类型列表（无缓存）");
        return delegate.getAvailableDatabaseTypes();
    }

    @Override
    public Probe getSystemProbeByIp(String hostIp) {
        log.debug("[CachingDecorator] 根据IP查询系统探针（无缓存）");
        return delegate.getSystemProbeByIp(hostIp);
    }

    @Override
    public Probe getSystemProbeByIpExclude(String hostIp, String excludeProbeKey) {
        log.debug("[CachingDecorator] 根据IP查询系统探针（排除指定probeKey，无缓存）");
        return delegate.getSystemProbeByIpExclude(hostIp, excludeProbeKey);
    }

    @Override
    public List<Probe> getOnlineProbes() {
        log.debug("[CachingDecorator] 获取在线探针列表（无缓存）");
        return delegate.getOnlineProbes();
    }
}
