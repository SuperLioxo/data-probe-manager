package com.lixin.probe.agent;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

import java.util.Map;

/**
 * 文件扫描测试程序
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = "com.lixin.probe.agent")
public class FileScanTest implements CommandLineRunner {

    @Autowired
    private FileService fileService;

    @Autowired
    private AgentProperties agentProperties;

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        SpringApplication.run(FileScanTest.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 文件扫描测试 ===");
        System.out.println("配置的扫描路径:");
        agentProperties.getModules().getFile().getScanPaths().forEach(path ->
            System.out.println("  - " + path)
        );

        System.out.println("\n配置的文件扩展名:");
        System.out.println("  " + agentProperties.getModules().getFile().getFileExtensions());

        System.out.println("\n最大深度:");
        System.out.println("  " + agentProperties.getModules().getFile().getMaxDepth());

        System.out.println("\n开始扫描...");
        ProbeResponse.DataFile result = fileService.scanFiles();

        System.out.println("\n=== 扫描结果 ===");
        System.out.println("成功: " + result.getSuccess());
        System.out.println("扫描路径数: " + result.getDirectories().size());

        for (Map.Entry<String, ProbeResponse.DataFile.Directory> entry : result.getDirectories().entrySet()) {
            ProbeResponse.DataFile.Directory dir = entry.getValue();
            System.out.println("\n路径: " + entry.getKey());
            System.out.println("  文件数: " + dir.getFileCount());
            System.out.println("  目录数: " + dir.getDirectoryCount());
            System.out.println("  总大小: " + dir.getSize() + " bytes");

            if (!dir.getFiles().isEmpty()) {
                System.out.println("  找到的文件:");
                dir.getFiles().forEach((name, file) ->
                    System.out.println("    - " + name + " (" + file.getSize() + " bytes, " + file.getExtension() + ")")
                );
            }
        }

        System.exit(0);
    }
}
