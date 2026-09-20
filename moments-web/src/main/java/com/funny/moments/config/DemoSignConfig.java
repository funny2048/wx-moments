package com.funny.moments.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.funny.framework.sign.client.AppIdClient;
import com.funny.framework.sign.crypto.CryptoJsAesDecrypt;
import com.funny.framework.sign.interceptor.SignVerifyInterceptor;
import com.funny.framework.sign.model.AppIdDTO;
import com.funny.framework.sign.model.RpcResult;

import jakarta.annotation.PostConstruct;

/**
 * C 端签名认证 + 字段解密演示装配（配合 example-demo 的 LeadsController 参考）。
 * <p>
 * 客户端签名算法（SignHelper，MD5 摘要）：
 * <ol>
 *   <li>取全部业务参数 + {@code _timestamp}（秒级时间戳，剔除 {@code _sign} 本身），按参数名字典序排序</li>
 *   <li>拼接：{@code appKey + (key1+value1)(key2+value2)... + appKey}（value 为空串时只拼 key）</li>
 *   <li>MD5 后转大写，作为 {@code _sign} 上送；服务端校验 {@code _timestamp} 有效窗口（±10 分钟）防重放</li>
 * </ol>
 * POST JSON 请求签名对象是 body 内的全部 String 字段（{@code _timestamp}/{@code _sign} 也在 body 内），
 * {@code appId} 作为 URL 参数上送（拦截器识别参数名：appid / _appid / appId）。
 *
 * @author fangli
 * @since 2026-09-20
 */
@Configuration
@ConditionalOnProperty(prefix = "funny.sign", name = "enabled", havingValue = "true")
public class DemoSignConfig {

    /** 演示 appId（C 端线索提交方）。生产环境：appId 与密钥由 funny.sign.host 的 /appid/list 下发 */
    private static final String DEMO_APP_ID = "demo_c_app";

    /** 演示签名密钥 —— 仅本地演示用，生产环境必须使用密钥服务下发的真实 md5Key，禁止提交真实密钥 */
    private static final String DEMO_MD5_KEY = "demo_md5_key_2026";

    /** CryptoJS 兼容 AES 演示密钥/向量（各 16 字节）—— 仅本地演示用，生产环境从密钥管理服务获取 */
    private static final String DEMO_AES_KEY = "demoaeskey123456";
    private static final String DEMO_AES_IV = "demoaesiv1234567";

    /**
     * 演示用 AppIdClient：覆盖 starter 的同名 bean（其声明为 @ConditionalOnMissingBean），
     * 返回写死的 appId/md5Key 列表，使 example 不依赖远端密钥服务即可自包含跑通签名认证。
     * 生产环境删除本 bean，由 funny.sign.host 配置的远端服务提供。
     */
    @Bean
    public AppIdClient appIdClient() {
        return () -> {
            AppIdDTO demoApp = new AppIdDTO();
            demoApp.setAppId(DEMO_APP_ID);
            demoApp.setAppName("C端演示应用");
            demoApp.setMd5Key(DEMO_MD5_KEY);
            demoApp.setStatus(0);
            RpcResult<List<AppIdDTO>> rpcResult = new RpcResult<>();
            rpcResult.setResult(List.of(demoApp));
            return rpcResult;
        };
    }

    /**
     * 签名认证拦截器（funny-framework-sign 未自动注册，需应用声明为 bean 并在 WebConfig 注册）。
     * 声明为 bean 让其 @Autowired 的 AppIdService 生效。
     */
    @Bean
    public SignVerifyInterceptor signVerifyInterceptor() {
        return new SignVerifyInterceptor();
    }

    /**
     * 注入 C 端字段解密密钥（AES/CBC/PKCS5Padding，CryptoJS 兼容），
     * 供 CryptoJsAesDecrypt.decrypt 解密线索上送的姓名/手机号密文。
     */
    @PostConstruct
    public void initAesKey() {
        CryptoJsAesDecrypt.setKey(DEMO_AES_IV, DEMO_AES_KEY);
    }
}
