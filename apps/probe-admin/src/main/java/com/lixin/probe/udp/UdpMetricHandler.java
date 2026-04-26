package com.lixin.probe.udp;

import com.lixin.probe.entity.MetricData;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.engine.MetricDataEvent;
import com.lixin.probe.service.MetricDataService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.service.ProbeWhitelistService;
import com.lixin.probe.service.SecurityEventService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * UDP数据处理器
 */
@Component
public class UdpMetricHandler extends SimpleChannelInboundHandler<MetricDataMessage> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UdpMetricHandler.class);

    @Autowired
    private ProbeService probeService;

    @Autowired
    private MetricDataService metricDataService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private ProbeWhitelistService probeWhitelistService;

    @Autowired(required = false)
    private SecurityEventService securityEventService;

    @Autowired
    private com.lixin.probe.service.ProbeStatusValidationService statusValidationService;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MetricDataMessage message) throws Exception {
        log.info("收到UDP指标数据: probeKey={}, metricName={}, value={}",
            message.getProbeKey(), message.getMetricName(), message.getMetricValue());

        try {
            // 1. 根据probeKey查询探针，不存在则检查白名单后自动注册
            Probe probe = probeService.getByProbeKey(message.getProbeKey());
            if (probe == null) {
                // 检查白名单是否启用
                if (probeWhitelistService != null && probeWhitelistService.isRedisAvailable()) {
                    // 检查探针是否在白名单中
                    if (!probeWhitelistService.isWhitelisted(message.getProbeKey())) {
                        log.warn("探针不在白名单中，拒绝自动注册: probeKey={}, senderIp={}",
                                message.getProbeKey(), message.getSenderAddress());
                        logSecurityEvent("UNAUTHORIZED_REGISTRATION_ATTEMPT",
                                message.getProbeKey(), message.getSenderAddress());
                        return;
                    }

                    // 检查IP是否在白名单中
                    if (!probeWhitelistService.isIpWhitelistedForProbe(
                            message.getProbeKey(), message.getSenderAddress())) {
                        log.warn("IP不在探针白名单中，拒绝注册: probeKey={}, senderIp={}",
                                message.getProbeKey(), message.getSenderAddress());
                        logSecurityEvent("IP_NOT_WHITELISTED",
                                message.getProbeKey(), message.getSenderAddress());
                        return;
                    }
                } else {
                    // 白名单功能未启用，记录警告但仍允许注册（向后兼容）
                    log.warn("白名单功能未启用，允许探针自动注册（建议配置Redis并启用白名单）: probeKey={}",
                            message.getProbeKey());
                }

                log.info("探针不存在且通过白名单验证，自动注册: probeKey={}", message.getProbeKey());

                // 检查是否为系统探针（通过probeKey判断，包含"system"关键词）
                boolean isSystemProbe = message.getProbeKey().toLowerCase().contains("system");

                if (isSystemProbe) {
                    // 检查该IP是否已存在系统探针
                    Probe existingSystemProbe = probeService.getSystemProbeByIp(message.getSenderAddress());
                    if (existingSystemProbe != null) {
                        log.warn("拒绝注册：该IP已存在系统探针 - existingProbeKey={}, existingIp={}, newProbeKey={}",
                                existingSystemProbe.getProbeKey(), existingSystemProbe.getHostIp(), message.getProbeKey());
                        log.info("将使用现有探针处理数据: existingProbeKey={}", existingSystemProbe.getProbeKey());
                        probe = existingSystemProbe;
                        // 跳过创建，继续使用现有探针
                    } else {
                        // 不存在同IP系统探针，创建新的
                        probe = new Probe();
                        probe.setProbeKey(message.getProbeKey());
                        probe.setName("自动注册系统探针-" + message.getProbeKey());
                        probe.setType("SYSTEM");
                        probe.setStatus("online");
                        probe.setHostIp(message.getSenderAddress());
                        probe.setCollectInterval(60);
                        probeService.create(probe);

                        // 重新查询获取ID
                        probe = probeService.getByProbeKey(message.getProbeKey());
                        log.info("系统探针自动注册成功: id={}, probeKey={}, ip={}",
                                probe.getId(), probe.getProbeKey(), probe.getHostIp());
                    }
                } else {
                    // 非系统探针，正常注册
                    probe = new Probe();
                    probe.setProbeKey(message.getProbeKey());
                    probe.setName("自动注册探针-" + message.getProbeKey());
                    probe.setType("UNKNOWN");
                    probe.setStatus("online");
                    probe.setHostIp(message.getSenderAddress());
                    probe.setCollectInterval(60);
                    probeService.create(probe);

                    // 重新查询获取ID
                    probe = probeService.getByProbeKey(message.getProbeKey());
                    log.info("探针自动注册成功: id={}, probeKey={}", probe.getId(), probe.getProbeKey());
                }
            }

            // 2. 验证探针是否在线（拒绝离线探针的数据）
            if (!statusValidationService.isProbeOnline(message.getProbeKey())) {
                log.warn("拒绝离线探针的UDP数据上报: probeKey={}, metricName={}, senderIp={}",
                        message.getProbeKey(), message.getMetricName(), message.getSenderAddress());
                return;
            }

            // 3. 更新探针心跳时间
            probeService.updateHeartbeat(message.getProbeKey());

            // 3. 构建MetricData实体并保存
            LocalDateTime timestamp = message.getTimestamp() != null ?
                LocalDateTime.ofInstant(Instant.ofEpochMilli(message.getTimestamp()), ZoneId.systemDefault()) :
                LocalDateTime.now();

            MetricData metricData = MetricData.builder()
                .probeId(probe.getId())
                .probeKey(probe.getProbeKey())
                .metricName(message.getMetricName())
                .metricValue(BigDecimal.valueOf(message.getMetricValue()))
                .timestamp(timestamp)
                .build();

            metricDataService.save(metricData);
            log.debug("监控数据保存成功: probeId={}, metricName={}, value={}",
                probe.getId(), message.getMetricName(), message.getMetricValue());

            // 4. 发布事件触发告警检测
            eventPublisher.publishEvent(new MetricDataEvent(this, metricData));
            log.debug("已发布监控数据事件");

        } catch (Exception e) {
            log.error("处理UDP指标数据异常: probeKey={}", message.getProbeKey(), e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("UDP处理器异常", cause);
        ctx.close();
    }

    /**
     * 记录安全事件
     *
     * @param eventType 事件类型
     * @param probeKey 探针标识
     * @param ip IP地址
     */
    private void logSecurityEvent(String eventType, String probeKey, String ip) {
        log.warn("安全事件: type={}, probeKey={}, ip={}, timestamp={}",
                eventType, probeKey, ip, System.currentTimeMillis());

        // 保存安全事件到数据库
        if (securityEventService != null) {
            try {
                // 根据事件类型确定严重程度
                String severity = determineSeverity(eventType);
                String details = String.format("检测到安全事件: 类型=%s, 探针=%s, IP=%s",
                        eventType, probeKey, ip);

                securityEventService.logSecurityEvent(eventType, probeKey, ip, severity, details);
            } catch (Exception e) {
                log.error("保存安全事件失败: type={}, probeKey={}, ip={}",
                        eventType, probeKey, ip, e);
            }
        }
    }

    /**
     * 根据事件类型确定严重程度
     */
    private String determineSeverity(String eventType) {
        if (eventType == null) {
            return "MEDIUM";
        }

        return switch (eventType) {
            case "UNAUTHORIZED_REGISTRATION_ATTEMPT" -> "HIGH";
            case "IP_NOT_WHITELISTED" -> "MEDIUM";
            case "RATE_LIMIT_EXCEEDED" -> "LOW";
            default -> "MEDIUM";
        };
    }
}
