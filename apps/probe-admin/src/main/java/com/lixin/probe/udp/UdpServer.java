package com.lixin.probe.udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * UDP服务器 - 用于接收探针上报的监控数据
 */
@Component
public class UdpServer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UdpServer.class);

    @Value("${udp.server.enabled:false}")
    private boolean enabled;

    @Value("${udp.server.port:9999}")
    private int udpPort;

    @Value("${udp.server.boss-threads:1}")
    private int bossThreads;

    @Value("${udp.server.worker-threads:4}")
    private int workerThreads;

    @Autowired
    private UdpMetricHandler udpMetricHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    @PostConstruct
    public void start() {
        if (!isEnabled()) {
            log.info("UDP服务器未启用");
            return;
        }

        bossGroup = new NioEventLoopGroup(bossThreads);
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
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new MetricDecoder());
                            pipeline.addLast(udpMetricHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(udpPort).sync();
            channel = future.channel();

            log.info("========================================");
            log.info("UDP服务器启动成功！");
            log.info("监听端口: {}", udpPort);
            log.info("Boss线程: {}", bossThreads);
            log.info("Worker线程: {}", workerThreads);
            log.info("========================================");

            channel.closeFuture().addListener((ChannelFutureListener) future1 -> {
                shutdown();
            });

        } catch (Exception e) {
            log.error("UDP服务器启动失败", e);
            shutdown();
            throw new RuntimeException("UDP服务器启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (channel != null) {
            log.info("UDP服务器关闭");
            channel.close();
        }
        shutdown();
    }

    private void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    private boolean isEnabled() {
        return enabled;
    }
}
