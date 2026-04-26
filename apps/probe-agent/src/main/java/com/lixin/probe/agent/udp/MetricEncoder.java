package com.lixin.probe.agent.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * UDP 指标编码器
 * 将指标数据编码为 UDP 数据包格式
 *
 * 数据包格式：
 * 版本(1B) | 探针KEY(32B) | 指标数量(1B) | [指标数据...]
 * 每个指标：名称长度(1B) | 名称(NB) | 值(8B double) | 时间戳(8B long)
 */
public class MetricEncoder {

    private static final Logger log = LoggerFactory.getLogger(MetricEncoder.class);
    /**
     * 协议版本
     */
    private static final byte PROTOCOL_VERSION = 1;

    /**
     * 探针 KEY 固定长度
     */
    private static final int PROBE_KEY_LENGTH = 32;

    /**
     * 编码指标数据列表为 UDP 数据包
     *
     * @param probeKey 探针 KEY
     * @param metrics  指标数据列表
     * @return 编码后的 ByteBuf（调用方负责释放）
     */
    public ByteBuf encode(String probeKey, List<MetricData> metrics) {
        // 估算缓冲区大小
        int estimatedSize = estimateBufferSize(metrics.size());
        ByteBuf buffer = Unpooled.buffer(estimatedSize);

        try {
            // 1. 写入版本号（1字节）
            buffer.writeByte(PROTOCOL_VERSION);

            // 2. 写入探针KEY（32字节，不足补0）
            byte[] probeKeyBytes = probeKey.getBytes(StandardCharsets.UTF_8);
            buffer.writeBytes(probeKeyBytes);
            for (int i = probeKeyBytes.length; i < PROBE_KEY_LENGTH; i++) {
                buffer.writeByte(0);
            }

            // 3. 写入指标数量（1字节）
            buffer.writeByte(metrics.size());

            // 4. 写入每个指标数据
            for (MetricData metric : metrics) {
                encodeMetric(buffer, metric);
            }

            return buffer;
        } catch (Exception e) {
            log.error("编码指标数据失败", e);
            // 确保 buffer 在异常路径被释放
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
            throw new RuntimeException("编码指标数据失败", e);
        }
    }

    /**
     * 编码单个指标数据
     *
     * @param buffer 缓冲区
     * @param metric 指标数据
     */
    private void encodeMetric(ByteBuf buffer, MetricData metric) {
        // 1. 写入指标名称长度（1字节）
        byte[] nameBytes = metric.getName().getBytes(StandardCharsets.UTF_8);
        buffer.writeByte(nameBytes.length);

        // 2. 写入指标名称
        buffer.writeBytes(nameBytes);

        // 3. 写入指标值（8字节 double，大端序）
        buffer.writeDouble(metric.getValue());

        // 4. 写入时间戳（8字节 long，大端序）
        buffer.writeLong(metric.getTimestamp());
    }

    /**
     * 估算缓冲区大小
     *
     * @param metricCount 指标数量
     * @return 估算的字节数
     */
    private int estimateBufferSize(int metricCount) {
        // 版本(1) + 探针KEY(32) + 指标数量(1)
        int fixedSize = 1 + PROBE_KEY_LENGTH + 1;

        // 每个指标：名称长度(1) + 名称(平均20) + 值(8) + 时间戳(8)
        int metricSize = 1 + 20 + 8 + 8;

        return fixedSize + metricCount * metricSize;
    }

    /**
     * 将 ByteBuf 转换为字节数组
     *
     * @param buffer ByteBuf
     * @return 字节数组
     */
    public byte[] toBytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /**
     * 释放 ByteBuf
     *
     * @param buffer ByteBuf
     */
    public void release(ByteBuf buffer) {
        if (buffer != null && buffer.refCnt() > 0) {
            buffer.release();
        }
    }
}
