package com.lixin.probe.udp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * UDP数据包解码器
 * 协议格式: 版本(1字节) | 探针KEY(32字节) | 指标数量(1字节) | 指标数据(N字节)
 */
public class MetricDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetricDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        ByteBuf buf = packet.content();
        
        if (buf.readableBytes() < 34) { // 最小包长度: 1+32+1
            log.warn("UDP数据包长度不足: {}", buf.readableBytes());
            return;
        }
        
        try {
            // 读取协议版本 (1字节)
            byte version = buf.readByte();
            if (version != 1) {
                log.warn("不支持的协议版本: {}", version);
                return;
            }
            
            // 读取探针KEY (32字节)
            byte[] keyBytes = new byte[32];
            buf.readBytes(keyBytes);
            String probeKey = new String(keyBytes, StandardCharsets.UTF_8).trim();
            
            // 读取指标数量 (1字节)
            byte metricCount = buf.readByte();
            
            log.debug("收到UDP数据: probeKey={}, metricCount={}, dataLen={}", probeKey, metricCount, buf.readableBytes());
            
            // 读取每个指标
            for (int i = 0; i < metricCount; i++) {
                if (buf.readableBytes() < 18) { // 1+1+8+8 最小
                    log.warn("指标数据长度不足");
                    break;
                }
                
                // 读取指标名称长度 (1字节)
                byte nameLen = buf.readByte();
                
                // 读取指标名称
                byte[] nameBytes = new byte[nameLen];
                buf.readBytes(nameBytes);
                String metricName = new String(nameBytes, StandardCharsets.UTF_8);
                
                // 读取指标值 (8字节 double)
                double metricValue = buf.readDouble();
                
                // 读取时间戳 (8字节 long)
                long timestamp = buf.readLong();
                
                // 创建MetricData对象
                MetricDataMessage metric = new MetricDataMessage();
                metric.setProbeKey(probeKey);
                metric.setMetricName(metricName);
                metric.setMetricValue(metricValue);
                metric.setTimestamp(timestamp);
                metric.setSenderAddress(packet.sender().getAddress().getHostAddress());
                
                out.add(metric);
            }
            
        } catch (Exception e) {
            log.error("解析UDP数据包异常", e);
        }
    }
}
