package com.lixin.probe.udp;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.dto.AgentDiscoveryMessage;
import com.lixin.probe.dto.DiscoveryResponse;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.enums.ProbeStatus;
import com.lixin.probe.service.ProbeService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * UDP发现服务器 - 接收Agent的发现请求并自动创建探针
 */
@Component
public class ProbeDiscoveryServer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeDiscoveryServer.class);

    @Value("${probe.discovery.port:9090}")
    private int discoveryPort;

    @Value("${probe.discovery.enabled:true}")
    private boolean enabled;

    @Value("${probe.discovery.threads:2}")
    private int workerThreads;

    @Value("${probe.discovery.allowed-ips:127.0.0.1,::1,localhost}")
    private String allowedIps;

    @Value("${probe.discovery.max-probes-per-agent:10}")
    private int maxProbesPerAgent;

    @Value("${server.websocket.port:8080}")
    private int websocketPort;

    @Autowired
    private ProbeService probeService;

    private EventLoopGroup workerGroup;
    private Channel channel;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("UDP发现服务器未启用");
            return;
        }

        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new DiscoveryHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(discoveryPort).sync();
            channel = future.channel();

            log.info("========================================");
            log.info("UDP发现服务器启动成功！");
            log.info("监听端口: {}", discoveryPort);
            log.info("Worker线程: {}", workerThreads);
            log.info("========================================");

            channel.closeFuture().addListener((ChannelFutureListener) future1 -> {
                shutdown();
            });

        } catch (Exception e) {
            log.error("UDP发现服务器启动失败", e);
            shutdown();
            throw new RuntimeException("UDP发现服务器启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (channel != null) {
            log.info("UDP发现服务器关闭");
            channel.close();
        }
        shutdown();
    }

    private void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 发现消息处理器
     */
    private class DiscoveryHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ByteBuf buf = packet.content();
            String message = buf.toString(StandardCharsets.UTF_8);
            InetSocketAddress sender = packet.sender();

            log.info("收到UDP发现消息，来自: {}，内容: {}", sender.getAddress(), message);

            try {
                // 验证来源IP
                if (!isAllowedIp(sender.getAddress())) {
                    log.warn("拒绝来自未授权IP的发现请求: {}", sender.getAddress());
                    sendErrorResponse(null, sender, "Unauthorized IP address");
                    return;
                }

                // 解析发现消息
                // 解析发现消息
                AgentDiscoveryMessage discovery = JSON.parseObject(message, AgentDiscoveryMessage.class);

                // 验证消息内容
                if (discovery.getAgentId() == null || discovery.getAgentId().trim().isEmpty()) {
                    throw new IllegalArgumentException("Agent ID不能为空");
                }
                if (discovery.getHostIp() == null || discovery.getHostIp().trim().isEmpty()) {
                    throw new IllegalArgumentException("Host IP不能为空");
                }
                if (discovery.getCapabilities() == null || discovery.getCapabilities().isEmpty()) {
                    throw new IllegalArgumentException("探针能力列表不能为空");
                }

                // 处理发现并自动创建探针
                handleDiscovery(discovery, sender);

            } catch (com.alibaba.fastjson2.JSONException e) {
                log.error("JSON解析失败，消息格式错误: {}", message, e);
                sendErrorResponse(null, sender, "Invalid JSON format: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                log.error("消息验证失败: {}", e.getMessage());
                sendErrorResponse(null, sender, "Validation failed: " + e.getMessage());
            } catch (Exception e) {
                log.error("处理UDP发现消息失败", e);
                sendErrorResponse(null, sender, "Internal server error: " + e.getMessage());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("UDP发现服务器异常", cause);
        }
    }

    /**
     * 处理发现消息并自动创建探针
     */
    private void handleDiscovery(AgentDiscoveryMessage discovery, InetSocketAddress sender) {
        try {
            List<Probe> createdProbes = new ArrayList<>();

            // 遍历Agent的能力列表
            for (AgentDiscoveryMessage.ProbeCapability capability : discovery.getCapabilities()) {
                try {
                    if (!capability.getEnabled()) {
                        log.debug("跳过未启用的能力: {}", capability.getType());
                        continue;
                    }

                    // 验证探针类型
                    if (!isValidProbeType(capability.getType())) {
                        log.warn("无效的探针类型: {}, 跳过", capability.getType());
                        continue;
                    }

                    // 限制每个Agent创建的探针数量
                    if (createdProbes.size() >= maxProbesPerAgent) {
                        log.warn("已达到最大探针数量限制: {}/{}", createdProbes.size(), maxProbesPerAgent);
                        break;
                    }

                    // 生成探针Key
                    String probeKey = generateProbeKey(discovery.getAgentId(), capability.getType());

                    // 检查探针是否已存在
                    Probe existingProbe = probeService.getByProbeKey(probeKey);

                    if (existingProbe == null) {
                        // 创建新探针
                        Probe probe = Probe.builder()
                                .probeKey(probeKey)
                                .name(discovery.getAgentName() + "-" + capability.getType())
                                .type(capability.getType())
                                .hostIp(discovery.getHostIp())
                                .port(discovery.getAgentPort() != null ? discovery.getAgentPort() : 58081)  // 使用发现消息中的端口
                                .collectInterval(capability.getCollectInterval())
                                .status(ProbeStatus.OFFLINE.getCode())
                                .version(discovery.getVersion() != null ? discovery.getVersion() : "")
                                .config(buildProbeConfig(discovery, capability))  // 添加配置信息
                                .description("通过UDP发现自动创建的" + capability.getType() + "探针")
                                .build();

                        probeService.create(probe);
                        createdProbes.add(probe);
                        log.info("自动创建探针成功: probeKey={}, type={}, port={}", probeKey, capability.getType(), probe.getPort());
                    } else {
                        // 探针已存在，更新信息
                        existingProbe.setHostIp(discovery.getHostIp());
                        existingProbe.setPort(discovery.getAgentPort() != null ? discovery.getAgentPort() : existingProbe.getPort());
                        existingProbe.setVersion(discovery.getVersion());
                        probeService.update(existingProbe);
                        createdProbes.add(existingProbe);
                        log.info("探针已存在，更新信息: probeKey={}", probeKey);
                    }
                } catch (Exception e) {
                    log.error("处理能力 {} 失败", capability.getType(), e);
                    // 继续处理其他能力，不中断整个流程
                }
            }

            // 发送发现响应
            sendDiscoveryResponse(discovery, sender, createdProbes);

        } catch (Exception e) {
            log.error("处理发现消息失败", e);
            // 发送错误响应
            sendErrorResponse(discovery, sender, e.getMessage());
        }
    }

    /**
     * 验证探针类型是否有效
     */
    private boolean isValidProbeType(String type) {
        return "SYSTEM".equals(type) || "DATABASE".equals(type) || "FILE".equals(type);
    }

    /**
     * 发送发现响应
     */
    private void sendDiscoveryResponse(AgentDiscoveryMessage discovery, InetSocketAddress sender, List<Probe> probes) {
        try {
            DiscoveryResponse response = new DiscoveryResponse();
            response.setMessageType("DISCOVERY_RESPONSE");
            response.setServerId("server-001");
            // 返回Admin基础URL，Agent根据探针类型自行拼接WebSocket路径
            response.setAdminBaseUrl("ws://" + sender.getAddress().getHostAddress() + ":" + websocketPort);
            response.setProbes(probes);
            response.setCode(200);
            response.setMessage("Discovery successful");

            String jsonResponse = JSON.toJSONString(response);
            byte[] data = jsonResponse.getBytes(StandardCharsets.UTF_8);

            ByteBuf buffer = Unpooled.wrappedBuffer(data);
            DatagramPacket packet = new DatagramPacket(buffer, sender);

            channel.writeAndFlush(packet).sync();
            log.info("发送发现响应: {}", jsonResponse);

        } catch (Exception e) {
            log.error("发送发现响应失败", e);
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(AgentDiscoveryMessage discovery, InetSocketAddress sender, String errorMessage) {
        try {
            DiscoveryResponse response = new DiscoveryResponse();
            response.setMessageType("DISCOVERY_RESPONSE");
            response.setServerId("server-001");
            response.setCode(500);
            response.setMessage(errorMessage);
            response.setProbes(new ArrayList<>());

            String jsonResponse = JSON.toJSONString(response);
            byte[] data = jsonResponse.getBytes(StandardCharsets.UTF_8);

            ByteBuf buffer = Unpooled.wrappedBuffer(data);
            DatagramPacket packet = new DatagramPacket(buffer, sender);

            channel.writeAndFlush(packet).sync();
            log.info("发送错误响应: {}", jsonResponse);

        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    /**
     * 生成探针Key
     * 使用随机ID格式，与现有系统保持一致
     * 例如：AGENT-database-x4k2p8-m3n
     */
    private String generateProbeKey(String agentId, String type) {
        // 生成随机ID部分（8位随机字符）
        String randomId = generateRandomId(8);
        return agentId.toLowerCase() + "-" + type.toLowerCase() + "-" + randomId;
    }

    /**
     * 生成随机ID
     */
    private String generateRandomId(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 构建探针配置信息
     */
    private String buildProbeConfig(AgentDiscoveryMessage discovery, AgentDiscoveryMessage.ProbeCapability capability) {
        try {
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("agentId", discovery.getAgentId());
            config.put("agentName", discovery.getAgentName());
            config.put("agentVersion", discovery.getVersion());
            config.put("agentIp", discovery.getHostIp());
            config.put("agentPort", discovery.getAgentPort());
            config.put("collectInterval", capability.getCollectInterval());
            config.put("discoveryTimestamp", discovery.getTimestamp());

            // 根据探针类型添加特定配置
            if ("DATABASE".equals(capability.getType())) {
                config.put("databaseType", "PostgreSQL");  // 默认PostgreSQL，Agent会提供更详细信息
                config.put("description", "数据库连接配置由Agent管理");
            } else if ("FILE".equals(capability.getType())) {
                config.put("description", "文件系统监控");
            } else if ("SYSTEM".equals(capability.getType())) {
                config.put("description", "系统资源监控");
            }

            return JSON.toJSONString(config);
        } catch (Exception e) {
            log.warn("构建探针配置失败", e);
            return "{}";
        }
    }

    /**
     * 验证IP地址是否在白名单中
     */
    private boolean isAllowedIp(java.net.InetAddress ipAddress) {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            // 如果没有配置白名单，允许所有IP（不安全，仅用于开发）
            return true;
        }

        String[] allowedIpArray = allowedIps.split(",");
        String clientIp = ipAddress.getHostAddress();

        for (String allowedIp : allowedIpArray) {
            allowedIp = allowedIp.trim();
            if ("*".equals(allowedIp) || "0.0.0.0/0".equals(allowedIp)) {
                // 通配符，允许所有IP
                return true;
            }
            if (allowedIp.equals(clientIp) || allowedIp.equals(ipAddress.getHostAddress())) {
                return true;
            }
            // 支持localhost别名
            if ("localhost".equals(allowedIp) &&
                ("127.0.0.1".equals(clientIp) || "::1".equals(clientIp))) {
                return true;
            }
        }

        return false;
    }
}
