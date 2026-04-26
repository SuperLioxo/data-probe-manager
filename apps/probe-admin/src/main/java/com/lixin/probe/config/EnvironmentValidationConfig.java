package com.lixin.probe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 环境变量验证配置
 * 在应用启动时验证必需的环境变量是否已设置
 *
 * @author Claude Code
 * @since 1.0
 */
@Configuration
public class EnvironmentValidationConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentValidationConfig.class);

    /**
     * 生产环境必需的环境变量列表
     */
    private static final List<String> REQUIRED_ENV_VARS = Arrays.asList(
        "POSTGRES_PASSWORD",           // 数据库密码（Docker环境）
        "META_ENCRYPTION_KEY",         // Meta探针加密密钥
        "FILE_ENCRYPTION_KEY"          // File探针加密密钥
    );

    /**
     * 应用启动完成时验证环境变量
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();

        // 获取当前激活的profile
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));

        // 生产环境需要验证所有必需的环境变量
        if (isProduction) {
            validateProductionEnvironment(environment);
        } else {
            log.info("非生产环境，跳过必需环境变量验证。当前激活的profiles: {}", Arrays.toString(activeProfiles));
        }
    }

    /**
     * 验证生产环境的必需环境变量
     *
     * @param environment Spring环境对象
     * @throws IllegalStateException 如果缺少必需的环境变量
     */
    private void validateProductionEnvironment(Environment environment) {
        log.info("验证生产环境必需的环境变量...");

        List<String> missingVars = REQUIRED_ENV_VARS.stream()
                .filter(varName -> {
                    String value = environment.getProperty(varName);
                    return value == null || value.trim().isEmpty();
                })
                .collect(Collectors.toList());

        if (!missingVars.isEmpty()) {
            String errorMessage = String.format(
                "生产环境缺少必需的环境变量: %s。请设置这些环境变量后重启应用。",
                String.join(", ", missingVars)
            );
            log.error(errorMessage);
            log.error("提示: 可以通过以下方式设置环境变量:");
            log.error("  1. 在.env文件中设置（参考.env.example）");
            log.error("  2. 在docker-compose.yml中设置");
            log.error("  3. 在系统环境变量中设置");
            log.error("  4. 在启动命令中传递: export VAR=value && java -jar ...");

            throw new IllegalStateException(errorMessage);
        }

        log.info("✅ 所有必需的环境变量已设置");
    }
}
