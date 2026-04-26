package com.lixin.probe.agent.discovery;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.agent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent发现客户端 - 通过UDP广播自动发现服务端并创建探针
 */
@Component
public class ProbeDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(ProbeDiscoveryClient.class);
    @Autowired
    private AgentProperties properties;

    private DatagramSocket socket;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(5000); // 5秒超时
            initialized = true;
            log.info("UDP发现客户端初始化成功，监听端口: {}", socket.getLocalPort());
        } catch (SocketException e) {
            log.error("UDP发现客户端初始化失败", e);
            initialized = false;
        }
    }

    /**
     * 执行UDP发现
     * @return DiscoveryResponse 服务端响应
     */
    public DiscoveryResponse discover() {
        if (!initialized) {
            log.error("UDP发现客户端未初始化");
            return null;
        }

        try {
            // 构建发现消息
            AgentDiscoveryMessage message = buildDiscoveryMessage();

            // 发送UDP广播
            String jsonMessage = JSON.toJSONString(message);
            byte[] data = jsonMessage.getBytes(StandardCharsets.UTF_8);

            InetAddress address = InetAddress.getByName(properties.getServer().getHost());
            int port = properties.getServer().getDiscoveryPort();

            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);

            log.info("发送UDP发现消息到: {}:{}", address.getHostAddress(), port);
            log.debug("发现消息内容: {}", jsonMessage);

            socket.send(packet);

            // 等待响应
            DiscoveryResponse response = waitForResponse();

            if (response != null) {
                log.info("收到服务端发现响应，WebSocket URL: {}", response.getWebsocketUrl());
            }

            return response;

        } catch (Exception e) {
            log.error("UDP发现失败", e);
            return null;
        }
    }

    /**
     * 等待服务端响应
     */
    private DiscoveryResponse waitForResponse() {
        try {
            byte[] buffer = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            log.debug("等待服务端响应...");
            socket.receive(packet);

            String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            log.debug("收到响应: {}", response);

            return JSON.parseObject(response, DiscoveryResponse.class);

        } catch (SocketTimeoutException e) {
            log.error("等待服务端响应超时");
            return null;
        } catch (Exception e) {
            log.error("接收服务端响应失败", e);
            return null;
        }
    }

    /**
     * 构建发现消息
     */
    private AgentDiscoveryMessage buildDiscoveryMessage() {
        AgentDiscoveryMessage message = new AgentDiscoveryMessage();
        message.setMessageType("AGENT_DISCOVERY");
        message.setAgentId(properties.getCode());
        message.setAgentName("Probe Agent - " + properties.getCode());
        message.setVersion("1.0.0");
        message.setTimestamp(System.currentTimeMillis());
        message.setDiscoveryPort(socket.getLocalPort());
        message.setAgentPort(properties.getAgent().getPort());  // 添加Agent HTTP端口

        // 获取本机IP
        try {
            String hostIp = getLocalHostIp();
            message.setHostIp(hostIp);
        } catch (Exception e) {
            log.warn("获取本机IP失败", e);
            message.setHostIp("127.0.0.1");
        }

        // 构建能力列表
        List<AgentDiscoveryMessage.ProbeCapability> capabilities = new ArrayList<>();

        // 系统监控能力
        if (properties.getModules().getSystem().getEnabled()) {
            AgentDiscoveryMessage.ProbeCapability systemCapability = new AgentDiscoveryMessage.ProbeCapability();
            systemCapability.setType("SYSTEM");
            systemCapability.setEnabled(true);
            systemCapability.setCollectInterval((int) (properties.getModules().getSystem().getCollectInterval() / 1000));
            capabilities.add(systemCapability);
        }

        // 数据库监控能力
        if (properties.getModules().getDatabase().getEnabled()) {
            AgentDiscoveryMessage.ProbeCapability dbCapability = new AgentDiscoveryMessage.ProbeCapability();
            dbCapability.setType("DATABASE");
            dbCapability.setEnabled(true);
            dbCapability.setCollectInterval(30); // 默认30秒
            capabilities.add(dbCapability);
        }

        // 文件监控能力
        if (properties.getModules().getFile().getEnabled()) {
            AgentDiscoveryMessage.ProbeCapability fileCapability = new AgentDiscoveryMessage.ProbeCapability();
            fileCapability.setType("FILE");
            fileCapability.setEnabled(true);
            fileCapability.setCollectInterval(300); // 默认5分钟
            capabilities.add(fileCapability);
        }

        message.setCapabilities(capabilities);

        return message;
    }

    /**
     * 获取本机IP地址
     */
    private String getLocalHostIp() throws UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        return localHost.getHostAddress();
    }

    /**
     * 关闭socket
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("UDP发现客户端已关闭");
        }
    }
}
