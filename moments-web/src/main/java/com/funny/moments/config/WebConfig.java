package com.funny.moments.config;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.MultipartConfigElement;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.funny.framework.sign.interceptor.SignVerifyInterceptor;
import com.funny.moments.common.consts.PatternConsts;

/**
 * @Author: funny2048
 * @Date: 2024/12/9
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 可选注入：funny.sign.enabled 未开启时 DemoSignConfig 整体不装配，此时不注册签名拦截器 */
    @Autowired(required = false)
    private SignVerifyInterceptor signVerifyInterceptor;

    /**
     * 本地图片根目录（design §6.2 配置承载，R2-建议3/建议9）：与 T040 LocalImageServiceImpl 注入同一 key
     * 且嵌套默认值字符串逐字一致（实现与评审各核对一次），保证落盘目录与静态映射目录不分裂（上传 404 防护）。
     */
    @Value("${moments.image.root-path:${user.home}/moments-data/images}")
    private String imageRootPath;

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

    /**
     * 本地图片静态映射（design §6.1，G8/R6）：/images/{fileName} → file:{图片根目录}/。
     * 末尾斜杠必须（目录语义）；文件名白名单解析防路径穿越；与默认 classpath:/static（前端静态页）共存互不影响。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + imageRootPath + "/")
                .resourceChain(true)
                .addResolver(new FileNameSafeResourceResolver());
    }

    /**
     * 文件名白名单解析器（R6 防路径穿越）：文件名须整串匹配 PatternConsts.IMAGE_FILE_NAME
     * （^[A-Za-z0-9._-]+$，上传 UUID 名与占位图名天然满足），不匹配返回 null → 404；
     * 叠加 PathResourceResolver 自带的资源越界（../ 逃逸根目录）校验双保险。
     */
    static class FileNameSafeResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (StringUtils.isEmpty(resourcePath) || !PatternConsts.IMAGE_FILE_NAME.matcher(resourcePath).matches()) {
                return null;
            }
            return super.getResource(resourcePath, location);
        }
    }

    /**
     * multipart 上限编程式装配（Stage 6 裁决4：主 agent 裁决，禁止改 application*.yml、禁止散落硬编码）：
     * 容器层单文件 6MB / 单请求 12MB，均高于业务校验上限（单文件 5MB→1021），保证 5MB 内的越界文件
     * 由业务码 1021 拒绝而非容器层 500；@Bean 注册后 MultipartAutoConfiguration 的同名兜底自动退让。
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse("6MB"));
        factory.setMaxRequestSize(DataSize.parse("12MB"));
        return factory.createMultipartConfig();
    }

}
