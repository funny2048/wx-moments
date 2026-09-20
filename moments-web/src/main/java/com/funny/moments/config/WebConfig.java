package com.funny.moments.config;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.funny.framework.sign.interceptor.SignVerifyInterceptor;

/**
 * @Author: funny2048
 * @Date: 2024/12/9
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 可选注入：funny.sign.enabled 未开启时 DemoSignConfig 整体不装配，此时不注册签名拦截器 */
    @Autowired(required = false)
    private SignVerifyInterceptor signVerifyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(loginInterceptor())
//                .excludePathPatterns("/api/**")
//                .excludePathPatterns("/auth/sys/cas");

        // C 端写接口签名认证：@SignVerify 标注的方法校验 appId + _timestamp + _sign（防篡改/防重放）
        if (Objects.nonNull(signVerifyInterceptor)) {
            registry.addInterceptor(signVerifyInterceptor)
                    .addPathPatterns("/leads/**");
        }
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:8090", "http://localhost:8080",
                        "http://test.funny.com:8090",
                        "http://test.funny.com",
                        "http://localhost",
                        "https://test.funny.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type","Authorization","Cookie","Accept","Referer","User-Agent")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
