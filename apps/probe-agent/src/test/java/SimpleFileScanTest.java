import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.service.FileService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 简单的文件扫描测试程序（不启动Web服务器）
 */
@Configuration
@ComponentScan(basePackages = "com.lixin.probe.agent")
public class SimpleFileScanTest {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        System.setProperty("server.port", "0"); // 使用随机端口避免冲突

        SpringApplication app = new SpringApplication(SimpleFileScanTest.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE); // 不启动Web服务器

        ConfigurableApplicationContext context = app.run(args);

        try {
            FileService fileService = context.getBean(FileService.class);
            AgentProperties properties = context.getBean(AgentProperties.class);

            System.out.println("=== 文件扫描测试 ===");
            System.out.println("配置的扫描路径:");
            properties.getModules().getFile().getScanPaths().forEach(path ->
                System.out.println("  - " + path)
            );

            System.out.println("\n配置的文件扩展名:");
            System.out.println("  " + properties.getModules().getFile().getFileExtensions());

            System.out.println("\n最大深度:");
            System.out.println("  " + properties.getModules().getFile().getMaxDepth());

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

        } finally {
            context.close();
        }
    }
}
