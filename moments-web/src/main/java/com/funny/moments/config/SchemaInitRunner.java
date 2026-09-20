package com.funny.moments.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * dev 环境建表 Runner（design §6.1，D1/A6）：启动时执行 classpath:schema/social_timeline.sql
 * （sql.md §1 同源落盘，moments-dao 资源目录）——全表 CREATE TABLE IF NOT EXISTS，幂等可重复启动。
 *
 * <p>事务口径：MySQL DDL 逐语句隐式提交（无跨语句回滚语义），ScriptUtils 默认按分号切分、
 * 剔除 -- 注释行逐条执行，无需显式事务包裹；部分失败时已建表保留（IF NOT EXISTS 幂等重跑补齐）。
 * 失败 fail-fast：上抛中止启动（design §9），error 日志含失败语句（带表名）。
 *
 * @Author: funny2048
 * @Date: 2026-09-21
 */
@Profile("dev")
@Component
public class SchemaInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitRunner.class);

    /** 建表脚本：与 sql.md §1 同源（moments-dao/src/main/resources/schema/social_timeline.sql） */
    private static final String SCHEMA_LOCATION = "schema/social_timeline.sql";

    private final DataSource dataSource;

    public SchemaInitRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        Resource schemaResource = new ClassPathResource(SCHEMA_LOCATION);
        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            // ScriptException 为 RuntimeException，消息含失败 SQL 语句（可定位到表名）
            ScriptUtils.executeSqlScript(connection, schemaResource);
        } catch (ScriptException | SQLException e) {
            log.error("social_timeline 建表脚本执行失败（fail-fast）location={}, cause={}", SCHEMA_LOCATION,
                    e.getMessage(), e);
            // 上抛中止启动：dev 库结构缺失时快速暴露，禁止带病运行
            throw new IllegalStateException("social_timeline schema 初始化失败: " + e.getMessage(), e);
        }
        log.info("social_timeline schema 初始化完成（幂等执行 8 表 DDL），costMs={}",
                System.currentTimeMillis() - start);
    }
}
