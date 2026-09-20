# 业务样例:线索收集(leads)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`表单收集` / `参数校验` / `幂等提交` / `落库即完结`
- 适用任务示例:C 端/开放接口留资表单(线索收集、报名、投票)、敏感字段密文提交、需要防重复提交的一次性写入、无状态流转的单表落库

## 全链组成

```
签名拦截器(框架前置,拦截 /leads/**) → Controller → Service(接口 + Impl) → Mapper(接口 + XML) → DO / In / 常量
```

对比 campaign 域:无 Manager 事务层、无事件、无缓存——单条 insert 落库即完结;验签由框架拦截器完成,不在本域文件内。

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口 | controller/LeadsController.java |
| 服务 | service/leads/ILeadsService.java、service/leads/impl/LeadsServiceImpl.java |
| 数据 | dao/entity/LeadsDO.java、dao/mapper/LeadsMapper.java、resources/mapper/common/LeadsMapper.xml |
| DTO | dto/in/LeadsSubmitIn.java |
| 常量 | consts/BaseCacheKeyConsts.java(leads 相关 key) |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 本项目统一返回,保持 `succ / fail` 用法 |
| `SignVerify` / `CryptoJsAesDecrypt` | 接口验签注解 / CryptoJS 兼容 AES 解密 | 项目签名方案(自研拦截器/网关验签);解密换项目统一加密组件,保持"解密仅校验、落库存密文"约定 |
| `RedisLockManager` / `RedisLock` / `LockFailException` | Redis 非阻塞锁封装 | Redisson `tryLock` 或自研锁封装,保持非阻塞语义 |
| `OperateLog` | 方法级操作日志注解 | 项目操作日志方案;无则删注解,保留方法级日志 |
| `ReturnCode` | 错误码枚举 | 项目内错误码枚举 |
| `com.baomidou.mybatisplus.*`(IService/ServiceImpl/BaseMapper/@TableName/@TableId/@TableField/@TableLogic) | 开源·MyBatis-Plus | 可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| lombok / fastjson / commons-lang3 | 开源·工具库 | 直接引入 |

## 风格要点(看什么)

**分层契约(最简垂直链)**
1. 链路只有 Controller → ServiceImpl → Mapper 三层:单表单条 insert、无批量、无多写事务、写后无缓存清理与事件联动时不建 Manager 层(campaign 引入 Manager 是为批量状态事务更新,leads 落库即完结,直接 `leadsMapper.insert`);ServiceImpl 照旧 `extends ServiceImpl<Mapper, DO>` 并注入 Mapper 做自定义查询
2. Controller 比 B 端更薄:验签已在拦截器完成,方法体只有 定位日志 → 调 service → 结果日志 三步;不加 try-catch 兜底,异常统一收在 ServiceImpl 业务方法的整体 catch 里
3. C 端无登录态:不取 UserHolder、无 tenantId 注入,调用方身份 = URL 参数 appId(拦截器识别参数名 appid/_appid/appId)+ body 的 source 渠道字段,落库留痕

**表单参数校验组织**
4. 校验独立成 `checkSubmitParam(submitIn)`,逐条不合法即 `return ApiResult.fail(中文文案)`,不抛异常;submit 先调校验,非 SUCCESS 直接透传返回
5. 常量先行:手机号正则 `static final Pattern` 预编译(线程安全,避免每次提交重编译),姓名长度边界抽 `NAME_MIN_LENGTH/NAME_MAX_LENGTH`,不写魔法值
6. 密文字段先解密再校验:解密失败单独文案("解密失败,请检查加密数据")与格式校验分开;校验文案面向 C 端用户可读,不带内部术语

**幂等提交(三层防线)**
7. 第一层 Redis 防重锁前置:窗口期内同一 requestId 只放行一个请求,抢不到快速 fail"提交处理中",不打到 DB;TTL 到期自动释放,不手动 unlock
8. 第二层锁内查重:INSERT 前先 `selectByRequestId`,命中即 `succ(原 leadsId)` 幂等返回(不是 fail),并记 info 日志标明"幂等命中"
9. 第三层 DB 唯一索引 uk_request_id 兜底:极端并发下撞唯一键走 catch 统一 fail,客户端按同 requestId 重试可经第二层拿到原 id

**敏感数据红线**
10. 姓名/手机号密文上送、密文落库:服务端解密仅用于格式校验,DO 字段名 `xxxCipher` 明示存储形态;新增 PII 字段一律延续密文约定
11. 明文绝不落日志:入参日志只打 requestId/source 定位字段,不打密文全文;成功日志手机号走 `maskMobile` 脱敏(137****0969,长度异常回退 ****),姓名不打日志

**数据与常量**
12. 插入复用 `BaseMapper.insert`(自增主键自动回填 DO,直接取 id 返回),自定义查询走 XML;手写 SQL 一律 `is_del = 0`,单条命中加 `limit 1`
13. In 类全部字段用 String:签名拦截器把 body 扁平化为 Map<String,String> 参与验签,`_timestamp/_sign` 由拦截器消费,业务代码不感知
14. 锁 key 集中在常量类,格式 `业务:模块:用途:参数占位`;防重窗口复用共享常量 `LOCK_TIME_IDEMPOTENT_MILLIS`(5 分钟),不单独造时间值

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### Controller

```java
package com.example.demo.controller;

// ……(标准 import 区:spring-web 注解 / fastjson JSONObject / jakarta / lombok / demo 业务类)

/**
 * C 端收线索 demo（写接口参考形态）：签名认证（@SignVerify：appId + _timestamp + _sign，
 * WebConfig 注册拦截器拦 /leads/**）→ 姓名/手机号 AES 解密校验 → 密文落库；解密与幂等在 Service 层完成。
 */
@RestController
@RequestMapping("/leads")
@Slf4j
public class LeadsController {

    @Resource
    private ILeadsService leadsService;

    /** 线索提交（幂等：同 requestId 重复提交返回原 leadsId；签名规则见 LeadsSubmitIn 注释） */
    @OperateLog(module = "leads", type = "insert", businessId = "#result.data")
    @SignVerify
    @PostMapping("/submit")
    public ApiResult<Integer> submit(String appId, HttpServletRequest request, @RequestBody LeadsSubmitIn submitIn) {
        // 入参含敏感密文，日志只打定位字段，不打密文全文
        log.info("LeadsController.submit appId={}, requestId={}, source={}",
                appId, submitIn.getRequestId(), submitIn.getSource());
        ApiResult<Integer> protocol = leadsService.submit(appId, submitIn);
        log.info("LeadsController.submit 线索提交 result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }
}
```

### Service 接口

```java
package com.example.demo.service.leads;

// ……(标准 import 区:mybatis-plus IService、demo 业务类)

/** C 端线索 Service：解密校验 + requestId 幂等 + 密文落库 */
public interface ILeadsService extends IService<LeadsDO> {

    /** 入参校验（幂等号 / 密文非空） */
    ApiResult checkSubmitParam(LeadsSubmitIn submitIn);

    /** 线索提交：requestId 幂等 → 解密姓名/手机号 → 格式校验 → 密文落库，返回 leadsId */
    ApiResult<Integer> submit(String appId, LeadsSubmitIn submitIn);
}
```

### ServiceImpl

```java
package com.example.demo.service.leads.impl;

// ……(标准 import 区:java.util.Objects / java.util.regex.Pattern / commons-lang3 StringUtils /
//      fastjson JSON / mybatis-plus ServiceImpl / demo 业务类)

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * C 端线索 Service。安全约定（红线）：
 * 姓名/手机号密文上送，解密仅用于格式校验，落库一律存原始密文；明文绝不写日志（手机号仅脱敏形式）。
 * 幂等三层：Redis 锁前置防重（快速失败不打到 DB）+ 锁内 requestId 查重幂等返回 + DB 唯一索引兜底。
 */
@Service
@Slf4j
public class LeadsServiceImpl extends ServiceImpl<LeadsMapper, LeadsDO> implements ILeadsService {

    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位。正则必须预编译（线程安全） */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 姓名明文长度边界（解密后校验用） */
    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 20;

    @Resource
    private LeadsMapper leadsMapper;

    @Resource
    private RedisLockManager redisLockManager;

    @Override
    public ApiResult checkSubmitParam(LeadsSubmitIn submitIn) {
        if (Objects.isNull(submitIn) || StringUtils.isBlank(submitIn.getRequestId())) {
            return ApiResult.fail("requestId不能为空");
        }
        if (StringUtils.isBlank(submitIn.getName()) || StringUtils.isBlank(submitIn.getMobile())) {
            return ApiResult.fail("姓名和手机号不能为空");
        }
        return ApiResult.succ();
    }

    @Override
    public ApiResult<Integer> submit(String appId, LeadsSubmitIn submitIn) {
        ApiResult checkResult = checkSubmitParam(submitIn);
        if (!ReturnCode.SUCCESS.getValue().equals(checkResult.getCode())) {
            return checkResult;
        }
        String requestId = submitIn.getRequestId();
        try {
            // 1. 并发防重锁前置：窗口期内同一 requestId 只放行一个请求，重试/并发洪峰在 Redis 层快速失败，不打到 DB（TTL 到期自动释放，不手动 unlock）
            if (!tryLock(String.format(BaseCacheKeyConsts.LOCK_LEADS_SUBMIT_REQUEST_KEY, requestId))) {
                return ApiResult.fail("提交处理中，请勿重复提交");
            }

            // 2. 锁内查重（INSERT 前先 SELECT）：幂等返回锁过期后迟到重试的原 leadsId
            LeadsDO exist = leadsMapper.selectByRequestId(requestId);
            if (Objects.nonNull(exist)) {
                log.info("LeadsServiceImpl.submit 幂等命中 requestId={}, leadsId={}", requestId, exist.getId());
                return ApiResult.succ(exist.getId());
            }

            // 3. 解密（仅用于校验；明文不出现在任何日志），失败说明密文被篡改或密钥不匹配
            String name = CryptoJsAesDecrypt.decrypt(submitIn.getName());
            String mobile = CryptoJsAesDecrypt.decrypt(submitIn.getMobile());
            if (StringUtils.isBlank(name) || StringUtils.isBlank(mobile)) {
                log.warn("LeadsServiceImpl.submit 解密失败 requestId={}", requestId);
                return ApiResult.fail("姓名或手机号解密失败，请检查加密数据");
            }
            if (name.length() < NAME_MIN_LENGTH || name.length() > NAME_MAX_LENGTH) {
                return ApiResult.fail("姓名长度需在2-20字之间");
            }
            if (!MOBILE_PATTERN.matcher(mobile).matches()) {
                return ApiResult.fail("手机号格式不正确");
            }

            // 4. 落库：存上送的原始密文，不存明文
            LeadsDO leadsDO = new LeadsDO();
            leadsDO.setRequestId(requestId);
            leadsDO.setNameCipher(submitIn.getName());
            leadsDO.setMobileCipher(submitIn.getMobile());
            leadsDO.setAppId(appId);
            leadsDO.setSource(submitIn.getSource());
            leadsDO.setRemark(submitIn.getRemark());
            leadsMapper.insert(leadsDO);

            log.info("LeadsServiceImpl.submit 线索落库成功 requestId={}, leadsId={}, mobile={}, source={}",
                    requestId, leadsDO.getId(), maskMobile(mobile), submitIn.getSource());
            return ApiResult.succ(leadsDO.getId());
        } catch (Exception e) {
            // DB 唯一索引 uk_request_id 兜底：极端并发下撞唯一键也走这里，客户端按 requestId 重试可幂等返回
            log.error("LeadsServiceImpl.submit 线索提交异常 requestId={}, submitIn={}",
                    requestId, JSON.toJSONString(submitIn), e);
            return ApiResult.fail("线索提交失败，请稍后重试");
        }
    }

    /** 尝试获取 Redis 防重锁（非阻塞），获取失败或异常统一返回 false */
    private boolean tryLock(String lockKey) {
        try {
            RedisLock redisLock = redisLockManager.fetchAndTryLock(lockKey,
                    BaseCacheKeyConsts.LOCK_TIME_IDEMPOTENT_MILLIS);
            return redisLock.isAcquired();
        } catch (LockFailException e) {
            log.warn("LeadsServiceImpl.tryLock 获取锁失败 lockKey={}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("LeadsServiceImpl.tryLock 获取锁异常 lockKey={}", lockKey, e);
            return false;
        }
    }

    /** 手机号脱敏：137****0969；长度异常时统一回退为 ****，绝不外露明文 */
    private String maskMobile(String mobile) {
        if (StringUtils.isBlank(mobile) || mobile.length() != 11) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }
}
```

### Mapper(接口 + XML)

```java
package com.example.demo.dao.mapper;

// ……(标准 import 区:mybatis @Mapper/@Param、mybatis-plus BaseMapper、demo 业务类)

/** C端线索表 Mapper：插入复用 BaseMapper.insert，查询走 XML */
@Mapper
public interface LeadsMapper extends BaseMapper<LeadsDO> {

    /** 按幂等号查线索（INSERT 前查重 / 幂等返回已有记录） */
    LeadsDO selectByRequestId(@Param("requestId") String requestId);
}
```

```xml
<mapper namespace="com.example.demo.dao.mapper.LeadsMapper">

    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.LeadsDO">
        <id column="id" property="id"/>
        <result column="request_id" property="requestId"/>
        <result column="name_cipher" property="nameCipher"/>
        <result column="mobile_cipher" property="mobileCipher"/>
        <result column="app_id" property="appId"/>
        <result column="source" property="source"/>
        <result column="remark" property="remark"/>
        <result column="created_stime" property="createdStime"/>
        <result column="modified_stime" property="modifiedStime"/>
        <result column="is_del" property="isDel"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, request_id, name_cipher, mobile_cipher, app_id, source, remark, created_stime, modified_stime, is_del
    </sql>
    <select id="selectByRequestId" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from leads
        where is_del = 0
          and request_id = #{requestId}
        limit 1
    </select>

</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
/** C端线索表（敏感字段密文存储） */
@TableName("leads")
public class LeadsDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    /** 客户端幂等号，同一 requestId 重复提交只落一条 */
    @TableField("request_id")
    private String requestId;

    /** 姓名密文（AES加密上送，原样落库） */
    @TableField("name_cipher")
    private String nameCipher;

    /** 手机号密文（AES加密上送，原样落库） */
    @TableField("mobile_cipher")
    private String mobileCipher;

    @TableField("app_id")
    private String appId;

    /** 投放渠道 */
    @TableField("source")
    private String source;

    @TableField("remark")
    private String remark;
    @TableField("created_stime")
    private Date createdStime;
    @TableField("modified_stime")
    private Date modifiedStime;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;

    // ……(省略:全字段 getter/setter,由 DAO 生成工具产出,String 字段 setter 内做 null 安全 trim)
}
```

### DTO(In)

```java
package com.example.demo.dto.in;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * C 端线索提交入参（POST JSON body）。
 * 【签名约束】拦截器把 body 扁平化为 Map&lt;String,String&gt; 参与验签,故所有字段必须 String
 * 且必须携带 _timestamp/_sign;appId 走 URL 参数(拦截器识别 appid/_appid/appId),不在 body 内。
 */
@Getter
@Setter
public class LeadsSubmitIn implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 客户端幂等号（每次提交唯一，重复提交返回原结果） */
    private String requestId;

    /** 姓名，AES 密文（Base64，CryptoJS 兼容 AES/CBC/PKCS5Padding） */
    private String name;

    /** 手机号，AES 密文（Base64，CryptoJS 兼容 AES/CBC/PKCS5Padding） */
    private String mobile;

    /** 投放渠道，如 douyin / baidu */
    private String source;

    /** 备注 */
    private String remark;

    /** 签名时间戳（秒），由签名拦截器校验有效窗口（±10 分钟）防重放 */
    private String _timestamp;

    /** 请求签名，MD5(appKey + 字典序(key+value) + appKey) 转大写，由签名拦截器校验 */
    private String _sign;
}
```

### 常量(leads 相关锁 key)

```java
public final class BaseCacheKeyConsts {
    /** 线索提交幂等锁 key 模板，参数为 requestId */
    public static final String LOCK_LEADS_SUBMIT_REQUEST_KEY = "demo:leads:lock:submit:%s";
    /** 提交幂等防重窗口（毫秒），5 分钟内同一 requestId 视为重复提交；leads/order 等提交类场景共用 */
    public static final long LOCK_TIME_IDEMPOTENT_MILLIS = 5 * 60 * 1000L;
}
```
