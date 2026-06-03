package com.lixin.probe.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 核心配置类
 * <p>
 * 主要职责：
 * 1. 注册分页拦截器，使 MyBatis-Plus 的 selectPage 能自动拼接 LIMIT/OFFSET
 * 2. 自定义 SqlSessionFactory，将拦截器、驼峰映射、逻辑删除等配置注入到 MyBatis 运行时
 * <p>
 * 注意：setPlugins() 必须调用，否则分页拦截器不会生效，selectPage 会返回全量数据导致接口超时
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器，注册分页插件
     * <p>
     * PaginationInnerInterceptor 会在 SQL 执行前自动改写：
     * - SELECT ... FROM table → SELECT ... FROM table LIMIT 20 OFFSET 0
     * - 额外发送一条 COUNT(*) 查询获取总行数用于分页计算
     * <p>
     * DbType.POSTGRE_SQL 指定数据库方言，生成兼容 PostgreSQL 的分页语法
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * 自定义 SqlSessionFactory（覆盖 Spring Boot 自动配置）
     * <p>
     * 使用 @ConditionalOnMissingBean 保证如果已有其他配置提供了 SqlSessionFactory 则不重复创建。
     * 关键操作是 setPlugins(mybatisPlusInterceptor())，将上面的分页拦截器注册到 MyBatis 执行链中，
     * 否则拦截器虽然被 Spring 容器管理，但不会被 MyBatis 调用。
     *
     * @param dataSource Spring 自动注入的数据源（来自 application.yml 的 spring.datasource 配置）
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactory = new MybatisSqlSessionFactoryBean();
        sqlSessionFactory.setDataSource(dataSource);

        // 将分页拦截器注入 MyBatis 插件链（关键！漏掉会导致分页失效）
        sqlSessionFactory.setPlugins(mybatisPlusInterceptor());

        // 开启数据库字段下划线命名 → Java 驼峰命名自动映射
        // 例如：database_name 列自动映射到 databaseName 字段
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        sqlSessionFactory.setConfiguration(configuration);

        // 逻辑删除配置：del_flag=1 表示已删除，del_flag=0 表示正常
        // 执行 deleteById 时会变成 UPDATE SET del_flag=1 而不是真正 DELETE
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setLogicDeleteValue("1");
        dbConfig.setLogicNotDeleteValue("0");
        globalConfig.setDbConfig(dbConfig);
        sqlSessionFactory.setGlobalConfig(globalConfig);

        // 扫描 mapper 目录下的 XML 映射文件
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sqlSessionFactory.setMapperLocations(resolver.getResources("classpath*:mapper/**/*Mapper.xml"));

        return sqlSessionFactory.getObject();
    }
}
