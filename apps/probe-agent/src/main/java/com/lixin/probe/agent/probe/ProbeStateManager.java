package com.lixin.probe.agent.probe;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探针状态管理器
 * 负责管理所有探针的运行状态，支持持久化
 */
@Component
public class ProbeStateManager {

    private static final Logger log = LoggerFactory.getLogger(ProbeStateManager.class);

    private static final String STATE_DIR = "data";
    private static final String STATE_FILE = "probe-states.json";

    // 探针状态存储
    private final Map<String, ProbeState> probeStates = new ConcurrentHashMap<>();
    // 持久化：记录探针的probeType，用于恢复时知道类型
    private final Map<String, String> probeTypes = new ConcurrentHashMap<>();

    private final Path stateFilePath;

    public ProbeStateManager() {
        this.stateFilePath = Paths.get(STATE_DIR, STATE_FILE);
    }

    @PostConstruct
    public void loadPersistedState() {
        try {
            File file = stateFilePath.toFile();
            if (!file.exists()) {
                log.info("[状态持久化] 无历史状态文件，跳过恢复");
                return;
            }

            String content = Files.readString(file.toPath());
            JSONObject json = JSON.parseObject(content);

            if (json.containsKey("probes")) {
                JSONObject probes = json.getJSONObject("probes");
                for (Map.Entry<String, Object> entry : probes.entrySet()) {
                    String probeKey = entry.getKey();
                    JSONObject probeData = (JSONObject) entry.getValue();

                    ProbeState state = ProbeState.fromCode(probeData.getString("state"));
                    String type = probeData.getString("probeType");

                    // 只恢复之前运行中的探针，其他状态忽略
                    if (state == ProbeState.RUNNING) {
                        probeStates.put(probeKey, ProbeState.STOPPED); // 标记为STOPPED，等待自动恢复
                        if (type != null) {
                            probeTypes.put(probeKey, type);
                        }
                        log.info("[状态持久化] 将恢复探针: probeKey={}, type={}", probeKey, type);
                    }
                }
            }

            log.info("[状态持久化] 加载完成，{} 个探针待恢复", probeTypes.size());
        } catch (Exception e) {
            log.warn("[状态持久化] 加载状态文件失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void persistOnShutdown() {
        saveToFile();
    }

    /**
     * 更新探针状态
     */
    public void updateState(String probeKey, ProbeState state) {
        probeStates.put(probeKey, state);
        log.debug("探针状态更新: probeKey={}, state={}", probeKey, state);
        // 状态变更时异步持久化
        saveToFile();
    }

    /**
     * 更新探针状态并记录类型
     */
    public void updateState(String probeKey, ProbeState state, String probeType) {
        probeStates.put(probeKey, state);
        if (probeType != null) {
            probeTypes.put(probeKey, probeType);
        }
        log.debug("探针状态更新: probeKey={}, state={}, type={}", probeKey, state, probeType);
        saveToFile();
    }

    /**
     * 获取探针状态
     */
    public ProbeState getState(String probeKey) {
        return probeStates.getOrDefault(probeKey, ProbeState.UNKNOWN);
    }

    /**
     * 检查探针是否在线
     */
    public boolean isOnline(String probeKey) {
        ProbeState state = getState(probeKey);
        return state == ProbeState.RUNNING || state == ProbeState.STARTING;
    }

    /**
     * 获取所有探针状态
     */
    public Map<String, ProbeState> getAllStates() {
        return new ConcurrentHashMap<>(probeStates);
    }

    /**
     * 获取需要恢复的探针（之前为RUNNING状态的）及其类型
     */
    public Map<String, String> getProbesToRecover() {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : probeTypes.entrySet()) {
            if (probeStates.get(entry.getKey()) == ProbeState.STOPPED) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 移除探针状态
     */
    public void removeState(String probeKey) {
        probeStates.remove(probeKey);
        probeTypes.remove(probeKey);
        log.debug("探针状态移除: probeKey={}", probeKey);
        saveToFile();
    }

    /**
     * 清空所有状态
     */
    public void clear() {
        probeStates.clear();
        probeTypes.clear();
        log.info("已清空所有探针状态");
        saveToFile();
    }

    /**
     * 保存状态到文件
     */
    private synchronized void saveToFile() {
        try {
            Path dir = Paths.get(STATE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            JSONObject root = new JSONObject();
            JSONObject probes = new JSONObject();

            for (Map.Entry<String, ProbeState> entry : probeStates.entrySet()) {
                JSONObject probeData = new JSONObject();
                probeData.put("state", entry.getValue().getCode());
                probeData.put("probeType", probeTypes.get(entry.getKey()));
                probes.put(entry.getKey(), probeData);
            }

            root.put("probes", probes);
            Files.writeString(stateFilePath, root.toJSONString());
        } catch (IOException e) {
            log.warn("[状态持久化] 保存状态文件失败: {}", e.getMessage());
        }
    }
}
