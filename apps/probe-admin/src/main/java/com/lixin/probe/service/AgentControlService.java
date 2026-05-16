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
 * Agent控制服务，负责启动/停止Agent进程、下发控制指令、管理Agent生命周期。
 * <p>
 * 本服务提供了多种Agent进程管理策略，按优先级依次尝试：
 * <ul>
 *   <li>systemd系统服务管理（适用于生产环境）</li>
 *   <li>Shell启动/重启脚本（适用于标准化部署环境）</li>
 *   <li>Maven直接启动jar包（适用于开发环境）</li>
 * </ul>
 * 同时提供进程状态检测能力，避免重复启动，并支持优雅停止操作。
 * </p>
 *
 * @author Claude Code
 * @since 2.0
 */
@Service
public class AgentControlService {

    /** 日志记录器，用于记录Agent控制操作的各个步骤和异常信息 */
    private static final Logger log = LoggerFactory.getLogger(AgentControlService.class);

    /**
     * 读取进程的标准输出内容。
     * <p>
     * 通过BufferedReader逐行读取进程的输入流（即标准输出），
     * 将所有行拼接为完整字符串后返回。读取完成后自动关闭流。
     * </p>
     *
     * @param process 需要读取输出的子进程对象
     * @return 进程的标准输出内容（以换行符分隔的多行文本）
     * @throws Exception 如果读取过程中发生I/O异常
     */
    private String readProcessOutput(Process process) throws Exception {
        // 使用StringBuilder拼接输出行，避免频繁创建字符串对象
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            // 逐行读取进程输出，直到流结束
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    /**
     * 通过系统服务启动Agent。
     * <p>
     * 按优先级依次尝试以下三种启动方式：
     * <ol>
     *   <li>首先检查Agent是否已经在运行，若已运行则直接返回成功</li>
     *   <li>尝试通过systemd系统服务启动（生产环境首选）</li>
     *   <li>尝试通过Shell启动脚本启动</li>
     *   <li>尝试通过java -jar直接启动（开发环境兜底方案）</li>
     * </ol>
     * 所有方式均失败时返回失败结果。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @return 启动结果Map，包含 success（是否成功）、message（描述信息）、method（使用的启动方式）等字段
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

        // 方法1: 尝试使用systemd启动（生产环境优先）
        if (trySystemdStart(agentCode, result)) {
            return result;
        }

        // 方法2: 使用启动脚本（标准化部署环境）
        if (tryScriptStart(agentCode, result)) {
            return result;
        }

        // 方法3: 直接使用 mvn spring-boot:run 启动（开发环境）
        if (tryMavenStart(agentCode, result)) {
            return result;
        }

        // 所有启动方式均失败，返回失败结果
        result.put("success", false);
        result.put("message", "无法启动Agent：请检查systemd服务或启动脚本");
        result.put("method", "none");
        return result;
    }

    /**
     * 优雅停止Agent进程。
     * <p>
     * 通过执行系统命令 {@code pkill -f probe-agent} 来停止Agent进程。
     * 该命令会向所有匹配"probe-agent"关键字的进程发送终止信号。
     * <ul>
     *   <li>exitCode=0：表示找到并成功终止了Agent进程</li>
     *   <li>exitCode=1：表示未找到匹配的进程（Agent可能已停止），此时也视为成功</li>
     * </ul>
     * 注意：无论进程是否存在，该方法始终返回success=true，因为最终状态都是"已停止"。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @return 停止结果Map，包含 success、message、method、exitCode、output等字段
     */
    public Map<String, Object> stopAgentGraceful(String agentCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("尝试通过pkill停止Agent: agentCode={}", agentCode);

            // 构建pkill命令：-f 参数表示匹配完整命令行中的关键字
            ProcessBuilder pb = new ProcessBuilder(
                "pkill",
                "-f",
                "probe-agent"
            );

            // 合并标准错误流到标准输出流，便于统一读取
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取进程输出（pkill通常无输出，但读取可以确保进程正常结束）
            String output = readProcessOutput(process);

            // 等待进程执行完毕并获取退出码
            int exitCode = process.waitFor();
            log.info("pkill exitCode: {}, output: {}", exitCode, output);

            // pkill返回0表示找到并杀死了进程，返回1表示没有找到进程
            // 对于停止操作，两种情况都认为成功（最终状态都是"Agent已停止"）
            result.put("success", true);
            result.put("message", "Agent停止命令已发送");
            result.put("method", "pkill");
            result.put("command", "pkill -f probe-agent");
            result.put("exitCode", exitCode);
            result.put("output", output);

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
     * 通过系统服务重启Agent。
     * <p>
     * 重启操作采用以下策略按优先级依次尝试：
     * <ol>
     *   <li>尝试通过systemd执行restart命令（原子操作，systemd自动处理停止再启动）</li>
     *   <li>尝试通过Shell重启脚本执行</li>
     * </ol>
     * 与手动"先停止再启动"不同，systemd的restart命令是原子化的，
     * 由系统服务管理器保证停止完成后才执行启动，避免竞态条件。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @return 重启结果Map，包含 success、message、method等字段
     */
    public Map<String, Object> restartAgentSystem(String agentCode) {
        Map<String, Object> result = new HashMap<>();

        // 方法1: 尝试使用systemd重启（原子操作，优先使用）
        if (trySystemdRestart(agentCode, result)) {
            return result;
        }

        // 方法2: 使用重启脚本
        if (tryScriptRestart(agentCode, result)) {
            return result;
        }

        // 所有重启方式均失败
        result.put("success", false);
        result.put("message", "无法重启Agent：请检查systemd服务或启动脚本");
        result.put("method", "none");
        return result;
    }

    /**
     * 尝试使用systemd系统服务启动Agent。
     * <p>
     * 执行 {@code systemctl start probe-agent.service} 命令启动Agent。
     * 要求系统中已注册probe-agent.service服务单元。
     * 适用于通过RPM/DEB包或手动注册方式部署的生产环境。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @param result    结果Map，成功时填入success、message、method、serviceName、command等信息
     * @return true表示启动成功，false表示启动失败或发生异常
     */
    private boolean trySystemdStart(String agentCode, Map<String, Object> result) {
        try {
            String serviceName = "probe-agent.service";
            log.info("尝试使用systemd启动Agent: service={}", serviceName);

            // 构建systemctl start命令
            ProcessBuilder pb = new ProcessBuilder(
                "systemctl",
                "start",
                serviceName
            );

            // 合并错误流到输出流
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取命令输出（通常为空，systemctl通过退出码反馈结果）
            String output = readProcessOutput(process);

            // 等待命令执行完成
            int exitCode = process.waitFor();
            log.info("systemctl start exitCode: {}", exitCode);

            if (exitCode == 0) {
                // 启动成功，记录详细信息到结果Map
                result.put("success", true);
                result.put("message", "Agent已通过systemd启动");
                result.put("method", "systemd");
                result.put("serviceName", serviceName);
                result.put("command", String.format("systemctl start %s", serviceName));
                result.put("output", output);
                return true;
            } else {
                // 启动失败，退出码非0（可能服务未注册或权限不足）
                log.warn("systemd启动失败，exitCode: {}", exitCode);
                return false;
            }

        } catch (Exception e) {
            // 捕获异常（可能systemctl命令不存在，如非Linux环境）
            log.warn("systemd启动失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用Shell启动脚本启动Agent。
     * <p>
     * 按优先级依次在多个候选路径中查找启动脚本，找到第一个存在的脚本即执行。
     * 候选路径包括：
     * <ul>
     *   <li>绝对路径指向infra/scripts目录下的开发环境和生产环境脚本</li>
     *   <li>基于user.dir（当前工作目录）向上推导的相对路径</li>
     *   <li>Agent应用目录下的start.sh脚本</li>
     * </ul>
     * 开发环境脚本（start-agent-dev.sh）优先于生产环境脚本（start-agent.sh）。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @param result    结果Map，成功时填入success、message、method、scriptPath、command等信息
     * @return true表示通过脚本启动成功，false表示未找到脚本或执行失败
     */
    private boolean tryScriptStart(String agentCode, Map<String, Object> result) {
        try {
            // 候选启动脚本路径列表，按优先级排列
            String[] scriptPaths = {
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/start-agent-dev.sh",  // ⭐ 开发环境脚本（优先）
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/start-agent.sh",  // 生产环境脚本
                System.getProperty("user.dir") + "/../../infra/scripts/start-agent-dev.sh",
                System.getProperty("user.dir") + "/../../infra/scripts/start-agent.sh",
                System.getProperty("user.dir") + "/infra/scripts/start-agent-dev.sh",
                System.getProperty("user.dir") + "/infra/scripts/start-agent.sh",
                System.getProperty("user.dir") + "/apps/probe-agent/start.sh"
            };

            // 遍历候选路径，查找第一个存在的脚本文件
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
                // 所有候选路径均未找到脚本文件
                log.warn("未找到启动脚本，尝试的路径: {}", String.join(", ", scriptPaths));
                return false;
            }

            log.info("使用启动脚本: {}", scriptPath);

            // 确保脚本文件具有可执行权限（以防文件权限丢失）
            File scriptFile = new File(scriptPath);
            scriptFile.setExecutable(true);

            // 构建并执行启动脚本的进程
            ProcessBuilder pb = new ProcessBuilder(scriptPath);
            // 合并错误流到输出流，便于统一捕获所有输出
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取脚本执行过程中的所有输出
            String output = readProcessOutput(process);

            // 等待脚本执行完毕并获取退出码
            int exitCode = process.waitFor();
            log.info("启动脚本exitCode: {}", exitCode);

            if (exitCode == 0) {
                // 脚本执行成功，将详细信息写入结果Map
                result.put("success", true);
                result.put("message", "Agent已通过启动脚本启动");
                result.put("method", "script");
                result.put("scriptPath", scriptPath);
                result.put("command", scriptPath);
                result.put("output", output);
                return true;
            } else {
                // 脚本执行失败，记录输出以便排查问题
                log.warn("启动脚本执行失败，exitCode: {}", exitCode);
                result.put("scriptOutput", output);
                return false;
            }

        } catch (Exception e) {
            log.error("启动脚本执行异常", e);
            return false;
        }
    }

    /**
     * 尝试使用systemd系统服务重启Agent。
     * <p>
     * 执行 {@code systemctl restart probe-agent.service} 命令重启Agent。
     * systemctl restart是原子操作：systemd会先执行stop，等待进程完全停止后，
     * 再执行start。相比手动"先停止再启动"的方式更可靠，避免竞态条件。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @param result    结果Map，成功时填入success、message、method、serviceName、command等信息
     * @return true表示重启成功，false表示重启失败或发生异常
     */
    private boolean trySystemdRestart(String agentCode, Map<String, Object> result) {
        try {
            String serviceName = "probe-agent.service";
            log.info("尝试使用systemd重启Agent: service={}", serviceName);

            // 构建systemctl restart命令（原子操作，systemd自动处理停止→启动）
            ProcessBuilder pb = new ProcessBuilder(
                "systemctl",
                "restart",
                serviceName
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readProcessOutput(process);

            int exitCode = process.waitFor();
            log.info("systemctl restart exitCode: {}", exitCode);

            if (exitCode == 0) {
                result.put("success", true);
                result.put("message", "Agent已通过systemd重启");
                result.put("method", "systemd");
                result.put("serviceName", serviceName);
                result.put("command", String.format("systemctl restart %s", serviceName));
                result.put("output", output);
                return true;
            } else {
                log.warn("systemd重启失败，exitCode: {}", exitCode);
                return false;
            }

        } catch (Exception e) {
            log.warn("systemd重启失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用Shell重启脚本重启Agent。
     * <p>
     * 按优先级依次在多个候选路径中查找重启脚本，找到第一个存在的脚本即执行。
     * 候选路径包括绝对路径和基于当前工作目录推导的相对路径。
     * 脚本内部通常包含"先停止旧进程，再启动新进程"的逻辑。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @param result    结果Map，成功时填入success、message、method、scriptPath、command等信息
     * @return true表示通过脚本重启成功，false表示未找到脚本或执行失败
     */
    private boolean tryScriptRestart(String agentCode, Map<String, Object> result) {
        try {
            // 候选重启脚本路径列表，按优先级排列
            String[] scriptPaths = {
                "/home/ovo/Workspace/data-probe-manager/infra/scripts/restart-agent.sh",  // 绝对路径
                System.getProperty("user.dir") + "/../../infra/scripts/restart-agent.sh",  // Admin目录的父目录
                System.getProperty("user.dir") + "/infra/scripts/restart-agent.sh",  // 当前目录
                System.getProperty("user.dir") + "/apps/probe-agent/restart.sh"  // Agent目录
            };

            // 遍历候选路径，查找第一个存在的重启脚本
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

            // 确保脚本文件具有可执行权限
            File scriptFile = new File(scriptPath);
            scriptFile.setExecutable(true);

            // 构建并执行重启脚本的进程
            ProcessBuilder pb = new ProcessBuilder(scriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取脚本执行过程中的所有输出
            String output = readProcessOutput(process);

            // 等待脚本执行完毕
            int exitCode = process.waitFor();
            log.info("重启脚本exitCode: {}", exitCode);
            log.info("完整输出:\n{}", output);

            if (exitCode == 0) {
                // 重启成功
                result.put("success", true);
                result.put("message", "Agent已通过重启脚本重启");
                result.put("method", "script");
                result.put("scriptPath", scriptPath);
                result.put("command", scriptPath);
                result.put("output", output);
                return true;
            } else {
                // 重启失败，记录脚本输出用于排查
                log.warn("重启脚本执行失败，exitCode: {}", exitCode);
                result.put("scriptOutput", output);
                return false;
            }

        } catch (Exception e) {
            log.error("重启脚本执行异常", e);
            return false;
        }
    }

    /**
     * 检查Agent进程是否已经在运行。
     * <p>
     * 通过执行 {@code pgrep -f probe-agent} 命令检测系统中是否存在
     * 命令行包含"probe-agent"关键字的进程。
     * <ul>
     *   <li>exitCode=0：表示找到了匹配的进程，Agent正在运行</li>
     *   <li>exitCode=1：表示未找到匹配的进程，Agent未运行</li>
     * </ul>
     * 该方法通常在启动Agent之前调用，用于避免重复启动。
     * </p>
     *
     * @return true表示Agent进程正在运行，false表示未运行或检测失败
     */
    private boolean isAgentRunning() {
        try {
            // 使用pgrep命令查找匹配"probe-agent"的进程
            // -f参数表示匹配完整命令行（而不仅仅是进程名）
            ProcessBuilder pb = new ProcessBuilder(
                "pgrep",
                "-f",
                "probe-agent"
            );
            Process process = pb.start();
            // pgrep的退出码：0=找到进程，1=未找到进程
            int exitCode = process.waitFor();
            boolean running = (exitCode == 0);

            if (running) {
                log.info("✓ Agent进程检测到正在运行");
            } else {
                log.info("✗ Agent进程未运行");
            }

            return running;

        } catch (Exception e) {
            // 检测过程出现异常（如pgrep命令不可用），保守返回false
            log.warn("检查Agent进程失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 尝试使用java -jar命令直接启动Agent（开发环境兜底方案）。
     * <p>
     * 该方法作为最后的启动手段，适用于没有systemd服务也没有Shell脚本的纯开发环境。
     * 启动流程：
     * <ol>
     *   <li>从当前工作目录向上逐级查找项目根目录（包含apps/probe-agent的目录）</li>
     *   <li>定位probe-agent的可执行jar包（target/probe-agent-1.0.0.jar）</li>
     *   <li>使用 {@code java -jar} 命令后台启动Agent</li>
     *   <li>将标准输出和错误输出重定向到日志文件（logs/agent.log）</li>
     * </ol>
     * 注意：此方法启动的进程是后台进程，方法返回时Agent可能尚未完全启动，
     * 通常需要等待10-20秒才能就绪。
     * </p>
     *
     * @param agentCode Agent代码（标识符），用于日志追踪
     * @param result    结果Map，填入success、message、method、command、pid、logFile等信息
     * @return true表示启动命令已成功发送（不保证Agent已完全启动），false表示jar包不存在或启动失败
     */
    private boolean tryMavenStart(String agentCode, Map<String, Object> result) {
        try {
            String projectDir = System.getProperty("user.dir", "/home/ovo/Workspace/data-probe-manager");
            // 向上逐级查找项目根目录（可能从 apps/probe-admin 目录启动，需要向上回溯）
            File dir = new File(projectDir);
            while (dir != null && !new File(dir, "apps/probe-agent").exists()) {
                dir = dir.getParentFile();
            }
            // 如果向上遍历到根目录仍未找到，回退到默认路径
            if (dir == null) {
                dir = new File("/home/ovo/Workspace/data-probe-manager");
            }

            // 定位Agent的jar包路径和日志文件路径
            String jarPath = dir.getAbsolutePath() + "/apps/probe-agent/target/probe-agent-1.0.0.jar";
            String logPath = dir.getAbsolutePath() + "/logs/agent.log";

            // 检查jar包是否存在，不存在则直接返回失败
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                log.warn("Agent jar不存在: {}", jarPath);
                return false;
            }

            log.info("尝试使用java -jar启动Agent: {}", jarPath);

            // 构建java -jar启动命令
            ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", jarPath
            );
            // 设置工作目录为项目根目录
            pb.directory(dir);
            // 合并错误流到输出流
            pb.redirectErrorStream(true);
            // 将所有输出重定向到日志文件（后台运行，不阻塞当前进程）
            pb.redirectOutput(new File(logPath));

            // 启动进程（异步执行，不等待启动完成）
            Process process = pb.start();

            // 记录进程PID，便于后续管理和监控
            log.info("Agent启动命令已发送，PID: {}", process.pid());

            // 注意：此处返回success=true仅表示命令已发送，不表示Agent已完全启动
            result.put("success", true);
            result.put("message", "Agent启动命令已发送，请等待10-20秒启动完成");
            result.put("method", "java-jar");
            result.put("command", "java -jar " + jarPath);
            result.put("pid", process.pid());
            result.put("logFile", logPath);

            return true;

        } catch (Exception e) {
            log.warn("Maven启动失败: {}", e.getMessage());
            return false;
        }
    }
}
