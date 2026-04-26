package com.lixin.probe.agent.udp;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * UDP 指标发送器
 * 负责将编码后的指标数据通过 UDP 协议发送到服务端
 */
public class UdpMetricSender {

    private static final Logger log = LoggerFactory.getLogger(UdpMetricSender.class);
    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;
    private final MetricEncoder encoder;
    private final String probeKey;

    /**
     * 构造函数
     *
     * @param host     服务端主机地址
     * @param port     服务端端口
     * @param probeKey 探针 KEY
     */
    public UdpMetricSender(String host, int port, String probeKey) throws Exception {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.encoder = new MetricEncoder();
        this.probeKey = probeKey;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(5000); // 5秒超时

        log.info("UDP发送器初始化成功: {}:{}", host, port);
    }

    /**
     * 连接到服务端
     */
    public void connect() throws Exception {
        // DatagramSocket不需要显式连接，已经准备就绪
        log.info("UDP发送器已就绪: {}:{}", serverAddress.getHostAddress(), serverPort);
    }

    /**
     * 发送指标数据
     *
     * @param metrics 指标数据列表
     * @return 是否发送成功
     */
    public boolean sendMetrics(java.util.List<MetricData> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            log.warn("指标数据为空，跳过发送");
            return false;
        }

        if (socket.isClosed()) {
            log.warn("UDP socket已关闭，无法发送数据");
            return false;
        }

        ByteBuf buffer = null;
        try {
            // 1. 编码指标数据
            buffer = encoder.encode(probeKey, metrics);

            // 2. 将 ByteBuf 转换为字节数组
            byte[] data = encoder.toBytes(buffer);

            // 3. 创建 UDP 数据包
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);

            // 4. 发送数据包
            socket.send(packet);

            log.debug("成功发送 {} 个指标到服务端", metrics.size());
            return true;

        } catch (Exception e) {
            log.error("发送指标数据失败", e);
            return false;
        } finally {
            // 5. 释放缓冲区
            if (buffer != null) {
                encoder.release(buffer);
            }
        }
    }

    /**
     * 关闭发送器
     */
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                log.info("UDP发送器已关闭");
            }
        } catch (Exception e) {
            log.error("关闭UDP发送器失败", e);
        }
    }

    /**
     * 获取服务端地址
     */
    public InetAddress getServerAddress() {
        return serverAddress;
    }

    /**
     * 获取服务端端口
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * 获取探针 KEY
     */
    public String getProbeKey() {
        return probeKey;
    }
}
