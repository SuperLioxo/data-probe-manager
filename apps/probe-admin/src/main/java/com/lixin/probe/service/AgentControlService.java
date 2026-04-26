package com.lixin.probe.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent控制服务
 * 负责Agent的启动、停止、重启操作
 *
 * @author Claude Code
 * @since 2.0
 */
@Service
public class AgentControlService {

    private static final Logger log = LoggerFactory.getLogger(AgentControlService.class);

    /**
     * 通过系统服务启动Agent
     * 尝试使用systemd或启动脚本
     *
     * @param agentCode Agent代码
     * @return 启动结果
     */
    public Map<String, Object> startAgentSystem(String agentCode) {
        Map<String, Object> result = new HashMap<>();

        // ⭐ 优化：首先检查Agent是否已经在运行
        if (isAgentRunning()) {
            log.info("✓ Agent已在运行中，无需启动");
            result.put("success", true);
            result.put("message", "Agent已在运行中");
            result.put("method", "check");
            result.put("alreadyRunning", true);
            return result;
        }

        // 方法1: 尝试使用systemd启动
        if (trySystemdStart(agentCode, result)) {
            return result;
        }

        // 方法2: 使用启动脚本
        if (tryScriptStart(agentCode, result)) {
            return result;
        }

        // 方法3: 直接使用 mvn spring-boot:run 启动（开发环境）
        if (tryMavenStart(agentCode, result)) {
            return result;
        }

        result.put("success", false);
        result.put("message", "无法启动Agent：请检查systemd服务或启动脚本");
        result.put("method", "none");
        return result;
    }

    /**
     * 优雅停止Agent
     * 通过pkill命令停止Agent进程
     *
     * @param agentCode Agent代码
     * @return 停止结果
     */
    public Map<String, Object> stopAgentGraceful(String agentCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("尝试通过pkill停止Agent: agentCode={}", agentCode);

            ProcessBuilder pb = new ProcessBuilder(
                "pkill",
                "-f",
                "probe-agent"
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            log.info("pkill exitCode: {}, output: {}", exitCode, output);

            // pkill返回0表示找到并杀死了进程，返回1表示没有找到进程
            // 对于停止操作，两种情况都认为成功
            result.put("success", true);
            result.put("message", "Agent停止命令已发送");
            result.put("method", "pkill");
            result.put("command", "pkill -f probe-agent");
            result.put("exitCode", exitCode);

            if (exitCode == 0) {
                log.info("✓ Agent进程已找到并停止");
            } else {
                log.info("✓ Agent进程未运行（已停止）");
            }

        } catch (Exception e) {
            log.error("停止Agent失败: agentCode={}", agentCode, e);
            result.put("success", false);
            result.put("message", "停止Agent失败: " + e.getMessage());
            result.put("method", "pkill");
        }

        return result;
    }

    /**
     * 通过系统服务重启Agent
     * 先停止，再启动
     *
     * @param agentCode Agent代码
     * @return 重启结果
     */
    public Map<String, Object> restartAgentSystem(String agentCode) {
        Map<String, Object> result = new HashMap<>();

        // 方法1: 尝试使用systemd重启
        if (trySystemdRestart(agentCode, result)) {
            return result;
        }

        // 方法2: 使用重启脚本
        if (tryScriptRestart(agentCode, result)) {
            return result;
        }

        result.put("success", false);
        result.put("message", "无法重启Agent：请检查systemd服务或启动脚本");
        result.put("method", "none");
        return result;
    }

    /**
     * 尝试使用systemd启动Agent
     *
     * @param agentCode Agent代码
     * @param result 结果Map
     * @return 是否成功
     */
    private boolean trySystemdStart(String agentCode, Map<String, Object> result) {
        try {
            String serviceName = "probe-agent.service";
            log.info("尝试使用systemd启动Agent: service={}", serviceName);

            // 使用systemctl启动服务
            ProcessBuilder pb = new ProcessBuilder(
                "systemctl",
                "start",
                serviceName
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("systemctl输出: {}", line);
            }

            int exitCode = process.waitFor();
            log.info("systemctl start exitCode: {}", exitCode);
            log.info("完整输出:\n{}", output);

            if (exitCode == 0) {
                result.put("success", true);
                result.put("message", "Agent已通过systemd启动");
                result.put("method", "systemd");
                result.put("serviceName", serviceName);
                result.put("command", String.format("systemctl start %s", serviceName));
                result.put("output", output.toString());
                return true;
            } else {
                log.warn("systemd启动失败，exitCode: {}, output: {}", exitCode, output);
                return false;
            }

        } catch (Exception e) {
            log.warn("systemd启动失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用启动脚本启动Agent
     *
     * @param agentCode Agent代码
     * @param result 结果Map
     * @return 是否成功
     */
    private boolean tryScriptStart(String agentCode, Map<String, Object> result) {
        try {
            // 查找启动脚本 - 尝试多个可能的路径（优先使用开发脚本）
            String[] scriptPaths = {
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/start-agent-dev.sh",  // ⭐ 开发环境脚本（优先）
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/start-agent.sh",  // 生产环境脚本
                System.getProperty("user.dir") + "/../../infra/scripts/start-agent-dev.sh",
                System.getProperty("user.dir") + "/../../infra/scripts/start-agent.sh",
                System.getProperty("user.dir") + "/infra/scripts/start-agent-dev.sh",
                System.getProperty("user.dir") + "/infra/scripts/start-agent.sh",
                System.getProperty("user.dir") + "/apps/probe-agent/start.sh"
            };

            String scriptPath = null;
            for (String path : scriptPaths) {
                File scriptFile = new File(path);
                if (scriptFile.exists()) {
                    scriptPath = path;
                    log.info("找到启动脚本: {}", path);
                    break;
                }
            }

            if (scriptPath == null) {
                log.warn("未找到启动脚本，尝试的路径: {}", String.join(", ", scriptPaths));
                return false;
            }

            log.info("使用启动脚本: {}", scriptPath);

            // 确保脚本可执行
            File scriptFile = new File(scriptPath);
            scriptFile.setExecutable(true);

            // 执行启动脚本
            ProcessBuilder pb = new ProcessBuilder(scriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("脚本输出: {}", line);
            }

            int exitCode = process.waitFor();
            log.info("启动脚本exitCode: {}", exitCode);
            log.info("完整输出:\n{}", output);

            if (exitCode == 0) {
                result.put("success", true);
                result.put("message", "Agent已通过启动脚本启动");
                result.put("method", "script");
                result.put("scriptPath", scriptPath);
                result.put("command", scriptPath);
                result.put("output", output.toString());
                return true;
            } else {
                log.warn("启动脚本执行失败，exitCode: {}", exitCode);
                result.put("scriptOutput", output.toString());
                return false;
            }

        } catch (Exception e) {
            log.error("启动脚本执行异常", e);
            return false;
        }
    }

    /**
     * 尝试使用systemd重启Agent
     *
     * @param agentCode Agent代码
     * @param result 结果Map
     * @return 是否成功
     */
    private boolean trySystemdRestart(String agentCode, Map<String, Object> result) {
        try {
            String serviceName = "probe-agent.service";
            log.info("尝试使用systemd重启Agent: service={}", serviceName);

            // 使用systemctl重启服务
            ProcessBuilder pb = new ProcessBuilder(
                "systemctl",
                "restart",
                serviceName
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("systemctl输出: {}", line);
            }

            int exitCode = process.waitFor();
            log.info("systemctl restart exitCode: {}", exitCode);
            log.info("完整输出:\n{}", output);

            if (exitCode == 0) {
                result.put("success", true);
                result.put("message", "Agent已通过systemd重启");
                result.put("method", "systemd");
                result.put("serviceName", serviceName);
                result.put("command", String.format("systemctl restart %s", serviceName));
                result.put("output", output.toString());
                return true;
            } else {
                log.warn("systemd重启失败，exitCode: {}, output: {}", exitCode, output);
                return false;
            }

        } catch (Exception e) {
            log.warn("systemd重启失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用重启脚本重启Agent
     *
     * @param agentCode Agent代码
     * @param result 结果Map
     * @return 是否成功
     */
    private boolean tryScriptRestart(String agentCode, Map<String, Object> result) {
        try {
            // 查找重启脚本 - 尝试多个可能的路径
            String[] scriptPaths = {
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/restart-agent.sh",  // 绝对路径
                System.getProperty("user.dir") + "/../../infra/scripts/restart-agent.sh",  // Admin目录的父目录
                System.getProperty("user.dir") + "/infra/scripts/restart-agent.sh",  // 当前目录
                System.getProperty("user.dir") + "/apps/probe-agent/restart.sh"  // Agent目录
            };

            String scriptPath = null;
            for (String path : scriptPaths) {
                File scriptFile = new File(path);
                if (scriptFile.exists()) {
                    scriptPath = path;
                    log.info("找到重启脚本: {}", path);
                    break;
                }
            }

            if (scriptPath == null) {
                log.warn("未找到重启脚本，尝试的路径: {}", String.join(", ", scriptPaths));
                return false;
            }

            log.info("使用重启脚本: {}", scriptPath);

            // 确保脚本可执行
            File scriptFile = new File(scriptPath);
            scriptFile.setExecutable(true);

            // 执行重启脚本
            ProcessBuilder pb = new ProcessBuilder(scriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("脚本输出: {}", line);
            }

            int exitCode = process.waitFor();
            log.info("重启脚本exitCode: {}", exitCode);
            log.info("完整输出:\n{}", output);

            if (exitCode == 0) {
                result.put("success", true);
                result.put("message", "Agent已通过重启脚本重启");
                result.put("method", "script");
                result.put("scriptPath", scriptPath);
                result.put("command", scriptPath);
                result.put("output", output.toString());
                return true;
            } else {
                log.warn("重启脚本执行失败，exitCode: {}", exitCode);
                result.put("scriptOutput", output.toString());
                return false;
            }

        } catch (Exception e) {
            log.error("重启脚本执行异常", e);
            return false;
        }
    }

    /**
     * 检查Agent是否已经在运行
     *
     * @return 是否运行中
     */
    private boolean isAgentRunning() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "pgrep",
                "-f",
                "probe-agent"
            );
            Process process = pb.start();
            int exitCode = process.waitFor();
            boolean running = (exitCode == 0);

            if (running) {
                log.info("✓ Agent进程检测到正在运行");
            } else {
                log.info("✗ Agent进程未运行");
            }

            return running;

        } catch (Exception e) {
            log.warn("检查Agent进程失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用Maven启动Agent（开发环境）
     *
     * @param agentCode Agent代码
     * @param result 结果Map
     * @return 是否成功
     */
    private boolean tryMavenStart(String agentCode, Map<String, Object> result) {
        try {
            String projectDir = System.getProperty("user.dir", "/home/ovo/Workspace/data-probe-manager");
            String agentDir = projectDir + "/apps/probe-agent";

            log.info("尝试使用Maven启动Agent: agentDir={}", agentDir);

            // 检查Agent目录是否存在
            File dir = new File(agentDir);
            if (!dir.exists()) {
                log.warn("Agent目录不存在: {}", agentDir);
                return false;
            }

            // 构建Maven命令
            ProcessBuilder pb = new ProcessBuilder(
                "mvn",
                "spring-boot:run",
                "-Dspring-boot.run.fork=false",
                "-DskipTests=true"
            );
            pb.directory(dir);
            pb.redirectErrorStream(true);

            // 启动进程（异步，不等待）
            Process process = pb.start();

            log.info("Maven启动命令已发送，PID: {}", process.pid());
            log.info("查看日志: tail -f /tmp/probe-agent.log");

            result.put("success", true);
            result.put("message", "Agent启动命令已发送（Maven模式），请等待30-60秒启动完成");
            result.put("method", "maven");
            result.put("command", "mvn spring-boot:run");
            result.put("agentDir", agentDir);
            result.put("pid", process.pid());
            result.put("logFile", "/tmp/probe-agent.log");

            return true;

        } catch (Exception e) {
            log.warn("Maven启动失败: {}", e.getMessage());
            return false;
        }
    }
}
