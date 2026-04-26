package com.lixin.probe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 探针管理系统启动类
 */
@SpringBootApplication(scanBasePackages = "com.lixin.probe")
@EnableAsync
@EnableScheduling
public class ProbeManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProbeManagementApplication.class, args);
        System.out.println("""

            ======================================
               数据探针管理系统启动成功！
               Probe Management System
            ======================================
            接口文档: http://localhost:8080/doc.html
            探针列表: http://localhost:8080/api/probes

            ======================================
        """);
    }
}
