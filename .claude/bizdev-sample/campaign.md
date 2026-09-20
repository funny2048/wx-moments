# 业务样例:广告计划管理(campaign)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`CRUD+分页查询` / `状态机批量操作` / `幂等防重提交` / `操作限流` / `进程内事件` / `单条缓存+写后清理`
- 适用任务示例:后台管理列表(增删改查+分页)、带状态流转的批量启停用、防重复提交的表单保存、修改后需要联动处理的业务

## 全链组成

```
Controller → Service(接口 + Impl) → Manager(事务层) → Mapper(接口 + XML) → DO / In / Out / 枚举 / 事件 / 常量
```

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口 | controller/CampaignController.java |
| 服务 | service/campaign/ICampaignService.java、service/campaign/impl/CampaignServiceImpl.java |
| 事件 | service/campaign/event/CampaignModifyEvent.java、CampaignModifyListener.java、config/EventBusConfig.java |
| 事务 | manager/ICampaignManager.java、manager/impl/CampaignManagerImpl.java |
| 数据 | dao/entity/CampaignDO.java、dao/mapper/CampaignMapper.java、resources/mapper/common/CampaignMapper.xml |
| DTO | dto/in/CampaignSaveIn.java、CampaignQueryIn.java、CampaignUpdateStatusIn.java、dto/out/CampaignOut.java |
| 枚举 | enums/CampaignStatusEnum.java、CampaignUpdateTypeEnum.java、CampaignBizCode.java |
| 常量 | consts/BaseCacheKeyConsts.java(campaign 相关 key) |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 本项目统一返回类,保持 `succ / fail / buildFailure(code, msg)` 三态用法 |
| `UserHolder` / `LoginUser` | 登录态获取 | 项目内登录上下文(自研 UserContext / Security principal 等) |
| `OperateLog` | 操作日志切面注解 | 项目操作日志方案;无则删注解,保留方法级日志 |
| `RedisLockManager` / `RedisLock` / `LockFailException` | 非阻塞分布式锁 | Redisson 或自研锁封装,保持"失败/异常统一返回 false"形态 |
| `RedisClient` | Redis 缓存客户端 | RedisTemplate 封装 |
| `DateUtils` / `ReturnCode` / `CacheKeyConsts` | 日期工具 / 错误码 / 缓存 key 常量 | 项目内等价物 |
| `com.baomidou.mybatisplus.*`(IService/ServiceImpl/BaseMapper/@TableLogic/@TableName) | 开源·ORM 基座 | 可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| `com.github.pagehelper.*`(PageHelper/PageInfo) | 开源·物理分页 | 可直接引入 |
| `EventBus`(`@Subscribe`,Guava) | 开源·进程内事件 | 可直接引入;或换 Spring ApplicationEvent,用法等价 |
| lombok / fastjson / commons-lang3 / commons-collections4 / jakarta | 开源·基础库 | 直接引入 |

## 风格要点(看什么)

**分层契约**
1. Controller 只做:接参 → 入口 info 日志(参数 JSON 化)→ UserHolder 取登录用户并注入 tenantId → 调 service → try-catch 兜底返 fail → finally 记结果日志;业务校验和规则一律不在 Controller 写
2. 校验独立成 `service.checkSaveParam(in, user)`,逐条不合法即 `return ApiResult.buildFailure(code, msg)`,不抛异常;Controller 先调校验再调业务
3. Manager 是事务层:只接 DO、只调 Mapper、方法上 `@Transactional(rollbackFor = Exception.class)`;ServiceImpl 不直接开事务
4. ServiceImpl 继承 `ServiceImpl<Mapper, DO>` 并实现接口,同时可注入 Mapper 做自定义查询

**出入参包装**
5. In 类:`@Getter @Setter + Serializable`,时间用 String(`startDate` yyyy-MM-dd)传输由服务端 parse;带 `requestId` 字段做幂等;QueryIn 内置分页默认值(pageNum=1/pageSize=10)与仅服务端注入字段(tenantId)
6. Out 类:与 DO 字段名解耦(`id→campaignId`、`status→campaignStatus`),数值字段给安全默认值(`BigDecimal.ZERO`),附 `xxxStr` 格式化展示字段;DO→Out 用 `BeanUtils.copyProperties` + 手工补差字段
7. DO:`@TableName/@TableId/@TableField` 全注解,软删字段 `@TableLogic isDel`,枚举值语义写在字段注释里

**写操作安全(三防)**
8. 防重:`requestId` 非空时先抢 Redis 幂等锁(`tryOperateLock(key, 5min)`),抢不到即拒"重复提交"
9. 限流:变更类操作前 `getOperateLimitLock(id)`(同一对象 1 分钟 1 次),用专门错误码 `OPERATE_LIMIT_CODE`
10. 查重:INSERT 前先 SELECT(同租户下唯一名);UPDATE 改名时排除自身 id 再查

**状态机批量操作**
11. 批量 ids → 一次查全量 → 逐条前置状态判断(非法状态整单失败 return,带对象名提示"【xx】已删除,无法…")→ 同状态跳过 → 构造**最小更新 DO**(只含主键+租户+变更字段+修改人,见 `buildStatusUpdateDO`)→ Manager 批量事务更新 → 清缓存
12. 目标状态可推导:open 时按投放周期推导落到 启用中/已下线/待投放,不硬编码

**缓存与事件**
13. 仅单条查询(id 精确命中)走缓存,列表条件组合多直接读库;写成功后在**事务之外**清缓存,清理失败仅记 error 不影响主流程
14. 修改后 `eventBus.post(new XxxModifyEvent(user, id))` 解耦联动;Listener `@Subscribe + @Async`,try-catch-finally 且 finally 记 costMs

**通用纪律**
15. 手写 SQL 一律 `is_del = 0` + `tenant_id` 条件;更新 SQL 用 `<set>+<if>` 动态最小列
16. 金额/比率展示统一 `scaleHalfUp` 保留 2 位;日志格式:`类名.方法名 中文动作 参数JSON`,异常分支必须带异常对象 e
17. 锁/缓存 key 集中在常量类,格式 `业务:模块:用途:参数占位`,过期时间常量化

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### Controller

```java
package com.example.demo.controller;

import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.example.demo.dto.in.CampaignQueryIn;
import com.example.demo.dto.in.CampaignSaveIn;
import com.example.demo.dto.in.CampaignUpdateStatusIn;
import com.example.demo.dto.out.CampaignOut;
import com.example.demo.enums.CampaignUpdateTypeEnum;
import com.example.demo.service.campaign.ICampaignService;
import com.github.pagehelper.PageInfo;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * @author dev
 * @version 1.0
 * @date 2024/12/23 19:25
 */
@RestController
@RequestMapping(value = "/campaign")
@Slf4j
public class CampaignController {

    @Resource
    private ICampaignService campaignService;

    @GetMapping("/listByPage")
    public ApiResult<PageInfo<CampaignOut>> listByPage(String _appId, HttpServletRequest request, CampaignQueryIn campaignQuery) {
        log.info("CampaignController.listByPage appId={}, campaignQuery={}", _appId, JSONObject.toJSONString(campaignQuery));
        LoginUser loginUser = UserHolder.getUser();
        try {
            campaignQuery.setTenantId(loginUser.getTenantId());
            return ApiResult.succ(campaignService.listByPage(campaignQuery));
        } catch (Exception e) {
            log.error("CampaignController.listByPage 计划列表分页查询异常 campaignQuery={}", JSONObject.toJSONString(campaignQuery), e);
            return ApiResult.fail("查询列表出错");
        }
    }

    // ……(省略 /query:与 listByPage 同构,无分页直返 List)

    @OperateLog(module = "campaign", type = "insert", businessId = "#result.data")
    @PostMapping("/save")
    public ApiResult save(String _appId, HttpServletRequest request, @RequestBody CampaignSaveIn saveParam) {
        log.info("CampaignController.save appId={}, saveParam={}", _appId, JSONObject.toJSONString(saveParam));
        LoginUser loginUser = UserHolder.getUser();
        ApiResult protocol = new ApiResult<>();
        try {
            ApiResult checkResult = campaignService.checkSaveParam(saveParam, loginUser);
            if (checkResult.getCode() != ReturnCode.SUCCESS.getValue()) {
                return checkResult;
            }
            protocol = campaignService.save(loginUser, saveParam);
        } catch (Exception e) {
            log.error("CampaignController.save 计划保存异常 saveParam={}", JSONObject.toJSONString(saveParam), e);
            protocol = ApiResult.fail("计划保存失败");
        } finally {
            log.info("CampaignController.save 计划保存 result={}", JSONObject.toJSONString(protocol));
        }
        return protocol;
    }

    // ……(省略 /update:与 /save 同构,多一步 campaignId 判空)

    /**
     * @param _appId 调用方应用标识
     * @param request 请求
     * @param updateStatusParam open:计划开启 pause:计划暂停 delete:计划删除
     * @return 操作结果
     */
    @OperateLog(module = "campaign", type = "update",
            dynamicType = "#updateStatusParam.updateType", businessId = "#updateStatusParam.ids")
    @PostMapping("/updateStatusByType")
    public ApiResult updateStatusByType(String _appId, HttpServletRequest request, @RequestBody CampaignUpdateStatusIn updateStatusParam) {
        log.info("CampaignController.updateStatusByType appId={}, updateStatusParam={}", _appId, JSONObject.toJSONString(updateStatusParam));
        if (CollectionUtils.isEmpty(updateStatusParam.getIds())) {
            return ApiResult.fail("计划id不能为空");
        }
        CampaignUpdateTypeEnum updateTypeEnum = CampaignUpdateTypeEnum.fromCode(updateStatusParam.getUpdateType());
        if (Objects.isNull(updateTypeEnum)) {
            return ApiResult.fail("不支持的操作类型");
        }
        ApiResult protocol = new ApiResult<>();
        LoginUser user = UserHolder.getUser();
        try {
            protocol = switch (updateTypeEnum) {
                case OPEN -> campaignService.open(user, updateStatusParam);
                case PAUSE -> campaignService.pause(user, updateStatusParam);
                case DELETE -> campaignService.delete(user, updateStatusParam);
            };
            return protocol;
        } catch (Exception e) {
            log.error("CampaignController.updateStatusByType 计划批量操作异常 updateStatusParam={}",
                    JSONObject.toJSONString(updateStatusParam), e);
            protocol = ApiResult.fail("计划批量操作失败");
        } finally {
            log.info("CampaignController.updateStatusByType 计划批量操作 result={}", JSONObject.toJSONString(protocol));
        }
        return protocol;
    }
}
```

### Service 接口

```java
package com.example.demo.service.campaign;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dao.entity.CampaignDO;
import com.example.demo.dto.in.CampaignQueryIn;
import com.example.demo.dto.in.CampaignSaveIn;
import com.example.demo.dto.in.CampaignUpdateStatusIn;
import com.example.demo.dto.out.CampaignOut;
import com.github.pagehelper.PageInfo;

public interface ICampaignService extends IService<CampaignDO> {
    ApiResult checkSaveParam(CampaignSaveIn saveParam, LoginUser userVo);

    PageInfo<CampaignOut> listByPage(CampaignQueryIn campaignQuery);

    List<CampaignOut> query(CampaignQueryIn campaignQuery);

    ApiResult open(LoginUser userVo, CampaignUpdateStatusIn updateType);

    ApiResult pause(LoginUser userVo, CampaignUpdateStatusIn updateType);

    ApiResult delete(LoginUser userVo, CampaignUpdateStatusIn updateType);

    ApiResult save(LoginUser userVo, CampaignSaveIn saveParam);

    ApiResult update(LoginUser userVo, CampaignSaveIn saveParam);

    boolean getOperateLimitLock(Integer campaignId);
}
```

### ServiceImpl(裁剪版)

```java
package com.example.demo.service.campaign.impl;

// ……(标准 import 区,基础设施依赖按文首对照表替换)

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.eventbus.EventBus;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CampaignServiceImpl extends ServiceImpl<CampaignMapper, CampaignDO> implements ICampaignService {

    @Resource
    private CampaignMapper campaignMapper;

    @Resource
    private ICampaignManager campaignManager;

    @Resource
    private RedisLockManager redisLockManager;

    @Resource
    private RedisClient redisClient;

    @Lazy
    @Resource
    private EventBus eventBus;

    @Override
    public ApiResult checkSaveParam(CampaignSaveIn saveParam, LoginUser userVo) {
        if (Objects.isNull(saveParam.getBidTarget())) {
            return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "投放目的不能为空");
        }
        if (StringUtils.isBlank(saveParam.getStartDate()) || StringUtils.isBlank(saveParam.getEndDate())) {
            return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "投放时段不能为空");
        }
        Date startTime = DateUtils.parseDate(saveParam.getStartDate());
        Date endTime = DateUtils.parseDate(saveParam.getEndDate());
        if (Objects.isNull(startTime) || Objects.isNull(endTime)) {
            return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "投放时段不能为空");
        }
        if (startTime.after(endTime)) {
            return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "结束时间需大于开始时间");
        }
        // ……(省略:开始时间>=今天 / budgetType / campaignName 判空+超长 / budget>0,均为同构逐条校验)
        return ApiResult.buildFailure(ReturnCode.SUCCESS.getValue(), ReturnCode.SUCCESS.getDesc());
    }

    @Override
    public PageInfo<CampaignOut> listByPage(CampaignQueryIn campaignQuery) {
        try {
            PageHelper.startPage(campaignQuery.getPageNum(), campaignQuery.getPageSize());
            List<CampaignDO> campaignDOList = campaignMapper.selectCampaignList(campaignQuery);
            if (CollectionUtils.isEmpty(campaignDOList)) {
                return new PageInfo<>();
            }

            // 替换分页
            PageInfo<CampaignDO> campaignDOPage = new PageInfo<>(campaignDOList);
            PageInfo<CampaignOut> campaignResponsePage = new PageInfo<>();
            BeanUtils.copyProperties(campaignDOPage, campaignResponsePage);

            // 组装返回结果:copyProperties 打底,再手工补差字段与格式化字段
            List<CampaignOut> campaignResponseList = new ArrayList<>();
            campaignDOList.forEach(s -> {
                CampaignOut campaignResponse = new CampaignOut();
                BeanUtils.copyProperties(s, campaignResponse);
                campaignResponse.setCampaignId(s.getId());
                campaignResponse.setCampaignStatus(s.getStatus());
                campaignResponse.setCampaignStatusStr(CampaignStatusEnum.getPageDesc(s.getStatus()));
                campaignResponse.setStartTimeStr(DateUtils.dateToString(s.getStartTime()));
                // ……(省略:endTimeStr / budget / ctr / consumption 等,均为 setTimeStr+scaleHalfUp 同构)
                campaignResponseList.add(campaignResponse);
            });
            campaignResponsePage.setList(campaignResponseList);
            return campaignResponsePage;
        } catch (Exception e) {
            log.error("CampaignServiceImpl.listByPage 计划分页查询异常 campaignQuery={}", JSON.toJSONString(campaignQuery), e);
        }
        return new PageInfo<>();
    }

    @Override
    public ApiResult open(LoginUser user, CampaignUpdateStatusIn updateType) {
        try {
            List<Integer> campaignIds = updateType.getIds();
            CampaignQueryIn campaignQuery = new CampaignQueryIn();
            campaignQuery.setTenantId(user.getTenantId());
            campaignQuery.setCampaignIds(campaignIds);
            List<CampaignDO> campaignDOList = campaignMapper.selectCampaignList(campaignQuery);
            if (CollectionUtils.isEmpty(campaignDOList)) {
                return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "计划不存在");
            }
            // 可开启状态判断
            List<CampaignDO> updateCampaignList = new ArrayList<>();
            for (CampaignDO campaignDO : campaignDOList) {
                // 如果已开启变开启 则跳过这条数据不处理
                if (campaignDO.getStatus().equals(CampaignStatusEnum.BIDDING.getValue())) {
                    log.info("计划id={}已开启，跳过处理", campaignDO.getId());
                    continue;
                }
                if (campaignDO.getStatus().equals(CampaignStatusEnum.PENDING.getValue())) {
                    return ApiResult.buildFailure(ReturnCode.FAIL.getValue(), "【" + campaignDO.getCampaignName() + "】待投放,无法开启");
                }
                // ……(省略:OFFLINE / DELETE 两个非法前置状态,同构 return fail)

                if (!getOperateLimitLock(campaignDO.getId())) {
                    return ApiResult.buildFailure(ReturnCode.OPERATE_LIMIT_CODE.getValue(), "【" + campaignDO.getCampaignName() + "】操作太快，请稍后再试!");
                }

                Integer targetStatus;
                boolean isPub = DateUtils.isInPubCycle(campaignDO.getStartTime(), campaignDO.getEndTime());
                if (isPub) {
                    targetStatus = CampaignStatusEnum.BIDDING.getValue();
                } else if (campaignDO.getEndTime() != null && !DateUtils.compareDay(new Date(), campaignDO.getEndTime())) {
                    // 不在投放期且投放已结束，置为已下线
                    targetStatus = CampaignStatusEnum.OFFLINE.getValue();
                } else {
                    // 不在投放期且尚未开始，置为待投放
                    targetStatus = CampaignStatusEnum.PENDING.getValue();
                }

                updateCampaignList.add(buildStatusUpdateDO(campaignDO, targetStatus, user));
            }
            int result = campaignManager.updateCampaignStatus(updateCampaignList);
            evictCampaignCache(campaignIds);
            return ApiResult.succ(result);
        } catch (Exception e) {
            log.error("CampaignServiceImpl.open 计划开启异常 updateType={}", JSONObject.toJSONString(updateType), e);
            return ApiResult.fail("计划开启失败，稍后重试");
        }
    }

    // ……(省略 pause / delete:与 open 同构。差异点——pause:PAUSE 为目标状态、DELETE/OFFLINE 非法;
    //      delete:先做创建人归属校验 !user.getUserCode().equals(campaignDO.getCreatedStaffNo()) 则拒)

    @Override
    public ApiResult save(LoginUser user, CampaignSaveIn saveParam) {
        try {
            // requestId 幂等防重：同一 requestId 的重复提交在窗口期内直接拒绝
            if (StringUtils.isNotBlank(saveParam.getRequestId())
                    && !tryOperateLock(String.format(BaseCacheKeyConsts.LOCK_CAMPAIGN_SAVE_REQUEST_KEY, saveParam.getRequestId()),
                    BaseCacheKeyConsts.LOCK_TIME_IDEMPOTENT_MILLIS)) {
                return ApiResult.fail("重复提交，请稍后再试");
            }

            // INSERT 前先 SELECT：同租户下计划名称查重
            CampaignDO nameConflict = campaignMapper.selectByTenantAndName(user.getTenantId(), saveParam.getCampaignName());
            if (Objects.nonNull(nameConflict)) {
                return ApiResult.fail("计划名称已存在");
            }

            CampaignDO save = new CampaignDO();
            BeanUtils.copyProperties(saveParam, save);
            save.setTenantId(user.getTenantId());
            save.setStartTime(DateUtils.parseDate(saveParam.getStartDate()));
            save.setEndTime(DateUtils.parseDate(saveParam.getEndDate()));
            save.setStatus(CampaignStatusEnum.PENDING.getValue());
            save.setStatusRemark(CampaignStatusEnum.getPageDesc(save.getStatus()));
            save.setCreatedStaffNo(user.getUserCode());
            save.setCreatedStaffName(user.getUserName());
            save.setModifiedStaffNo(user.getUserCode());
            save.setModifiedStaffName(user.getUserName());
            int result = campaignManager.saveCampaign(save);

            return ApiResult.succ(result);
        } catch (Exception e) {
            log.error("CampaignServiceImpl.save 计划保存异常 saveParam={}", JSONObject.toJSONString(saveParam), e);
            return ApiResult.fail("计划保存失败，稍后重试");
        }
    }

    @Override
    public ApiResult update(LoginUser user, CampaignSaveIn saveParam) {
        try {
            CampaignDO campaignDO = campaignMapper.selectCampaignById(saveParam.getCampaignId());
            if (Objects.isNull(campaignDO)) {
                return ApiResult.fail("计划不存在");
            }

            // 租户归属校验，防跨租户越权修改
            if (!Objects.equals(user.getTenantId(), campaignDO.getTenantId())) {
                return ApiResult.fail("计划不存在");
            }

            if (campaignDO.getStatus().equals(CampaignStatusEnum.DELETE.getValue())) {
                return ApiResult.fail("计划已删除,不可修改");
            }

            if (!saveParam.getCampaignName().equals(campaignDO.getCampaignName())) {
                CampaignDO nameConflict = campaignMapper.selectByTenantAndName(user.getTenantId(), saveParam.getCampaignName());
                if (Objects.nonNull(nameConflict) && !nameConflict.getId().equals(saveParam.getCampaignId())) {
                    return ApiResult.fail("计划名称已存在");
                }
            }

            // 操作频率控制
            if (!getOperateLimitLock(campaignDO.getId())) {
                return ApiResult.buildFailure(ReturnCode.OPERATE_LIMIT_CODE.getValue(), "【" + campaignDO.getCampaignName() + "】操作太快，请稍后再试!");
            }

            // 只更新必要的字段
            CampaignDO save = new CampaignDO();
            save.setCampaignName(saveParam.getCampaignName());
            save.setId(saveParam.getCampaignId());
            // ……(省略:startTime/endTime/budgetType/budget/budgetMode/修改人,同构 set)
            int result = campaignManager.updateCampaign(save);

            eventBus.post(new CampaignModifyEvent(user, saveParam.getCampaignId()));

            evictCampaignCache(Collections.singletonList(saveParam.getCampaignId()));
            return ApiResult.succ(result);
        } catch (Exception e) {
            log.error("CampaignServiceImpl.update 计划修改异常 saveParam={}", JSONObject.toJSONString(saveParam), e);
            return ApiResult.fail("计划修改失败，稍后重试");
        }
    }

    /**
     * 计划操作限流 1分钟1次
     */
    @Override
    public boolean getOperateLimitLock(Integer campaignId) {
        String lockKey = BaseCacheKeyConsts.LOCK_CAMPAIGN_OPERATE_KEY + campaignId;
        return tryOperateLock(lockKey, BaseCacheKeyConsts.LOCK_TIME_MILLIS);
    }

    /**
     * 构造只含主键 + 租户 + 待更新字段的最小更新实体，避免把查出的完整实体直接传入更新。
     */
    private CampaignDO buildStatusUpdateDO(CampaignDO campaignDO, Integer targetStatus, LoginUser user) {
        CampaignDO updateDO = new CampaignDO();
        updateDO.setId(campaignDO.getId());
        updateDO.setTenantId(campaignDO.getTenantId());
        updateDO.setStatus(targetStatus);
        updateDO.setStatusRemark(CampaignStatusEnum.getPageDesc(targetStatus));
        updateDO.setModifiedStaffNo(user.getUserCode());
        updateDO.setModifiedStaffName(user.getUserName());
        return updateDO;
    }

    /**
     * 尝试获取 Redis 锁（非阻塞），获取失败或异常统一返回 false。
     */
    private boolean tryOperateLock(String lockKey, long expireMs) {
        try {
            RedisLock redisLock = redisLockManager.fetchAndTryLock(lockKey, expireMs);
            return redisLock.isAcquired();
        } catch (LockFailException e) {
            log.warn("获取锁失败 lockKey={}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("获取锁异常 lockKey={}", lockKey, e);
            return false;
        }
    }

    /**
     * 写操作成功后清理计划缓存（在事务方法之外调用，避免事务内操作 Redis）。
     */
    private void evictCampaignCache(List<Integer> campaignIds) {
        if (CollectionUtils.isEmpty(campaignIds)) {
            return;
        }
        for (Integer campaignId : campaignIds) {
            try {
                redisClient.del(String.format(CacheKeyConsts.CACHE_CAMPAIGN, campaignId));
            } catch (Exception e) {
                log.error("清理计划缓存失败 campaignId={}", campaignId, e);
            }
        }
    }

    /**
     * 金额/比率展示统一保留 2 位小数，空值安全。
     */
    private static BigDecimal scaleHalfUp(BigDecimal value) {
        return Objects.isNull(value) ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
```

### Manager(接口 + 事务实现)

```java
public interface ICampaignManager {
    int updateCampaignStatus(List<CampaignDO> campaignDOList);
    int saveCampaign(CampaignDO campaignDO);
    int updateCampaign(CampaignDO campaignDO);
}

@Service
@Slf4j
public class CampaignManagerImpl implements ICampaignManager {

    @Autowired
    private CampaignMapper campaignMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCampaignStatus(List<CampaignDO> campaignDOList) {
        campaignDOList.forEach(campaignMapper::updateCampaignStatusById);
        return campaignDOList.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveCampaign(CampaignDO campaignDO) {
        campaignMapper.insert(campaignDO);
        return campaignDO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCampaign(CampaignDO campaignDO) {
        return campaignMapper.updateById(campaignDO);
    }
}
```

### Mapper(接口 + XML)

```java
@Mapper
public interface CampaignMapper extends BaseMapper<CampaignDO> {

    CampaignDO selectCampaignById(@Param("campaignId") Integer campaignId);

    List<CampaignDO> selectCampaignList(@Param("queryParam") CampaignQueryIn campaignQuery);

    CampaignDO selectByTenantAndName(@Param("tenantId") Long tenantId, @Param("campaignName") String campaignName);

    int updateCampaignStatusById(@Param("campaignDO") CampaignDO campaignDO);
}
```

```xml
<mapper namespace="com.example.demo.dao.mapper.CampaignMapper">

    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.CampaignDO">
        <id column="id" property="id"/>
        <result column="tenant_id" property="tenantId"/>
        <result column="campaign_name" property="campaignName"/>
        <result column="start_time" property="startTime"/>
        <result column="status" property="status"/>
        <!-- ……(省略:其余 20+ 列映射,生成工具产出) -->
        <result column="is_del" property="isDel"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, tenant_id, campaign_name, start_time, end_time, <!-- ……(省略其余列) --> is_del
    </sql>

    <select id="selectCampaignById" resultMap="BaseResultMap" parameterType="java.lang.Integer">
        select
        <include refid="Base_Column_List"/>
        from campaign
        where is_del = 0 and id = #{campaignId}
    </select>

    <select id="selectCampaignList" resultMap="BaseResultMap"
            parameterType="com.example.demo.dto.in.CampaignQueryIn">
        select
        <include refid="Base_Column_List"/>
        from campaign
        where is_del = 0
        <if test="queryParam.tenantId != null">
            and tenant_id = #{queryParam.tenantId}
        </if>
        <if test="queryParam.campaignId != null">
            and id = #{queryParam.campaignId}
        </if>
        <if test="queryParam.campaignIds != null and queryParam.campaignIds.size() > 0">
            and id in
            <foreach item="campaignId" index="index" collection="queryParam.campaignIds" open="(" separator="," close=")">
                #{campaignId}
            </foreach>
        </if>
        order by id desc
    </select>

    <select id="selectByTenantAndName" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from campaign
        where is_del = 0 and tenant_id = #{tenantId} and campaign_name = #{campaignName}
        limit 1
    </select>

    <update id="updateCampaignStatusById" parameterType="com.example.demo.dao.entity.CampaignDO">
        update campaign
        <set>
            <if test="campaignDO.status != null">
                status = #{campaignDO.status,jdbcType=INTEGER},
            </if>
            <if test="campaignDO.statusRemark != null">
                status_remark = #{campaignDO.statusRemark,jdbcType=VARCHAR},
            </if>
            <!-- ……(省略:modifiedStaffNo/Name、modifiedBy 三个同构 <if>) -->
        </set>
        where id = #{campaignDO.id} and tenant_id = #{campaignDO.tenantId} and is_del = 0
    </update>
</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
@TableName("campaign")
public class CampaignDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 多租户隔离字段，所有 SQL 必须按此过滤 */
    @TableField("tenant_id")
    private Long tenantId;

    @TableField("campaign_name")
    private String campaignName;

    @TableField("start_time")
    private Date startTime;

    @TableField("end_time")
    private Date endTime;

    /** 投放目的 1 线索 2 UV 3 点击 */
    @TableField("bid_target")
    private Integer bidTarget;

    @TableField("budget")
    private BigDecimal budget;

    /** 预算类型 1 不限制 2 自定义 */
    @TableField("budget_type")
    private Integer budgetType;

    /** 1 启用中 2 待投放 3 已暂停 4 已下线 999 已删除 */
    @TableField("status")
    private Integer status;

    // ……(省略:hangupStatus / statusRemark / publishBiz / 审计四字段(created_staff_no/name、modified_staff_no/name) /
    //      createdBy/Stime、modifiedBy/Stime / 投放数据字段(explore_total 等 8 个) / appId / businessId / budgetMode / dayBudget)

    @TableField("is_del")
    @TableLogic
    private Integer isDel;

    // ……(省略:全字段 getter/setter,由 DAO 生成工具产出)
}
```

### DTO(In / Out)

```java
/** 保存入参:save 与 update 共用,campaignId 为空即新增 */
@Getter
@Setter
public class CampaignSaveIn implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer campaignId;

    private String campaignName;

    /** 投放目的 1 线索 2 UV 3 点击 */
    private Integer bidTarget;

    /** 幂等请求唯一标识，服务端按其做防重复提交（可选） */
    private String requestId;

    /** 开始时间 yyyy-MM-dd */
    private String startDate;

    /** 结束时间 yyyy-MM-dd */
    private String endDate;

    // ……(省略:budgetType / budget / chargeMode(默认1) / businessId / budgetMode / dayBudget)
}

/** 查询入参:分页默认值 + 服务端注入字段 */
@Getter
@Setter
public class CampaignQueryIn implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    private Integer campaignId;
    private List<Integer> campaignIds;

    /** 租户隔离字段，仅服务端从 LoginUser.currentTenantId 注入，前端传入会被覆盖 */
    private Long tenantId;
}

/** 批量状态变更入参 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignUpdateStatusIn implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型 OPEN / PAUSE / DELETE */
    private String updateType;

    /** 计划 id 列表 */
    private List<Integer> ids;
}

/** 返回:字段名与 DO 解耦,数值给安全默认值,附格式化展示字段 */
@Getter
@Setter
public class CampaignOut implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer campaignId;
    private String campaignName;
    private Integer campaignStatus;
    private String campaignStatusStr;

    private BigDecimal budget;

    private Date startTime;
    private String startTimeStr;

    private Date endTime;
    private String endTimeStr;

    // ……(省略:bidTarget / budgetType / chargeMode / budgetMode / dayBudget /
    //      投放数据字段 8 个(均 ZERO 默认) / 创建修改人四字段 / businessId)
}
```

### 枚举

```java
/** 状态枚举:value + desc,静态 getPageDesc 供展示转换,查不到返 null */
public enum CampaignStatusEnum {

    BIDDING(1, "启用中"),
    PENDING(2, "待投放"),
    PAUSE(3, "已暂停"),
    OFFLINE(4, "已下线"),
    DELETE(999, "已删除"),
    ;

    private final Integer value;
    private final String desc;

    CampaignStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() { return value; }

    public String getDesc() { return desc; }

    public static String getPageDesc(Integer value) {
        if (Objects.isNull(value)) {
            return null;
        }
        for (CampaignStatusEnum e : values()) {
            if (e.getValue().equals(value)) {
                return e.getDesc();
            }
        }
        return null;
    }
}

/** 操作类型枚举:code + desc + fromCode 解析,未匹配返 null 由调用方兜底 */
public enum CampaignUpdateTypeEnum {

    OPEN("OPEN", "开启"),
    PAUSE("PAUSE", "暂停"),
    DELETE("DELETE", "删除"),
    ;

    private final String code;
    private final String desc;

    // ……(构造器与 getter 同上;fromCode(String) 遍历匹配 code,blank 或未匹配返回 null)
}
```

### 事件(Event / Listener / Config)

```java
/** 事件对象:@Data + 语义字段,谁发布谁定义 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignModifyEvent {

    private LoginUser user;

    /** 计划 id */
    private Integer campaignId;
}

/** 监听器:@Subscribe + @Async,catch 兜底,finally 记耗时 */
@Component
@Slf4j
public class CampaignModifyListener {

    @Subscribe
    @Async
    public void onEvent(CampaignModifyEvent event) {
        long start = System.currentTimeMillis();
        try {
            // 计划修改后的联动处理：同步报表、推送通知等
        } catch (Exception e) {
            log.error("CampaignModifyListener error, event={}", JSON.toJSONString(event), e);
        } finally {
            log.info("CampaignModifyListener end, event={}, costMs={}", JSON.toJSONString(event), System.currentTimeMillis() - start);
        }
    }
}

/** EventBus 以 Bean 注入,使用方 @Lazy @Resource 引用防循环依赖 */
@Configuration
public class EventBusConfig {

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }
}
```

### 常量(campaign 相关锁/缓存 key)

```java
public final class BaseCacheKeyConsts {

    /** Campaign 操作限流锁前缀,使用方式:LOCK_CAMPAIGN_OPERATE_KEY + campaignId */
    public static final String LOCK_CAMPAIGN_OPERATE_KEY = "demo:admin:campaign:lock:operate:";

    /** 锁过期时间(毫秒)—— 限流窗口 1 分钟 */
    public static final long LOCK_TIME_MILLIS = 60_000L;

    /** 计划保存幂等锁 key 模板,参数为 requestId */
    public static final String LOCK_CAMPAIGN_SAVE_REQUEST_KEY = "demo:admin:campaign:lock:save:%s";

    /** 保存幂等防重窗口(毫秒),5 分钟内同一 requestId 视为重复提交 */
    public static final long LOCK_TIME_IDEMPOTENT_MILLIS = 5 * 60 * 1000L;
}
```
