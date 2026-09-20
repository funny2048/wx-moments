package com.funny.moments.config;

import java.lang.reflect.Field;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cola.extension.ExtensionExecutor;
import com.alibaba.cola.extension.ExtensionRepository;
import com.alibaba.cola.extension.register.ExtensionBootstrap;
import com.alibaba.cola.extension.register.ExtensionRegister;

/**
 * COLA Extension 4.3.2 兼容 Spring Boot 3.x。
 * 原版 @javax.annotation.Resource 在 Jakarta EE 下失效，手动接线。
 */
@Configuration
public class ColaExtensionConfig {

    @Bean
    public ExtensionRepository extensionRepository() {
        return new ExtensionRepository();
    }

    @Bean
    public ExtensionRegister extensionRegister(ExtensionRepository repository) throws Exception {
        ExtensionRegister register = new ExtensionRegister();
        setField(register, "extensionRepository", repository);
        return register;
    }

    @Bean
    public ExtensionExecutor extensionExecutor(ExtensionRepository repository) throws Exception {
        ExtensionExecutor executor = new ExtensionExecutor();
        setField(executor, "extensionRepository", repository);
        return executor;
    }

    @Bean(initMethod = "init")
    public ExtensionBootstrap extensionBootstrap(ApplicationContext ctx, ExtensionRegister register) throws Exception {
        ExtensionBootstrap bootstrap = new ExtensionBootstrap();
        bootstrap.setApplicationContext(ctx);
        setField(bootstrap, "extensionRegister", register);
        return bootstrap;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
