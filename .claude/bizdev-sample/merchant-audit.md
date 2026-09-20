# 业务样例:商家资料修改审核(merchant-audit)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`双表快照审核` / `提交-审核分离` / `CAS 状态机` / `并发审核互斥` / `敏感数据脱敏`
- 适用任务示例:资料/内容修改需人工审核后才生效、变更留痕可追溯、运营后台审批流(列表→详情→通过/驳回)、多运营并发审核需互斥

## 全链组成

```
提交方(商家端):Controller → Service(写快照,不动正式表)
审核方(运营端):Controller → Service(状态机前置校验) → Manager(事务: CAS + 快照写正式表)
数据:正式表 merchant + 快照表 merchant_modify_record(1:N,同一商家同时仅 1 条待审核)
```

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口 | controller/MerchantController.java(提交方)、controller/MerchantAuditController.java(审核方) |
| 服务 | service/merchant/IMerchantService.java + impl、IMerchantAuditService.java + impl |
| 事务 | manager/IMerchantAuditManager.java、manager/impl/MerchantAuditManagerImpl.java |
| 数据 | dao/entity/MerchantDO.java(正式)、MerchantModifyRecordDO.java(快照)、dao/mapper/MerchantMapper.java、MerchantModifyRecordMapper.java、resources/mapper/common/ 对应两个 XML |
| DTO | dto/in/MerchantModifyIn.java(提交入参兼快照载体)、MerchantAuditCheckIn.java;dto/out/MerchantInfoOut.java、MerchantAuditListOut.java、MerchantAuditDetailOut.java |
| 枚举 | enums/MerchantAuditStatusEnum.java、MerchantAuditTypeEnum.java、MerchantStatusEnum.java |

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 本项目统一返回类,保持 `succ / fail / buildFailure(code, msg)` 三态用法 |
| `UserHolder` / `LoginUser` | 登录态获取(审核方 Controller 取操作人) | 项目内登录上下文 |
| `OperateLog` | 操作日志切面注解 | 项目操作日志方案;无则删注解,保留方法级日志 |
| `ReturnCode` / `DateUtils` / `DesensitizeUtils` / `PatternConsts` | 错误码 / 日期工具 / 手机号脱敏(maskMobile)/ 预编译正则常量 | 项目内等价物 |
| `com.baomidou.mybatisplus.*`(BaseMapper/@TableLogic/@TableName) | 开源·ORM 基座 | 可直接引入;纯 MyBatis 项目退化为生成的基础 Mapper |
| `com.github.pagehelper.*`(PageHelper/PageInfo) | 开源·物理分页 | 可直接引入 |
| lombok / fastjson / commons-lang3 / spring-web / spring-tx / jakarta | 开源·基础库 | 直接引入 |

## 风格要点(看什么)

**双表模式与数据源切换**
1. 正式表只写审核通过的数据;提交只写快照表(`modify_content` 存整体 JSON),审核通过前正式表不变——这是"修改审核"场景的核心不变量
2. 快照载体三处共用:提交入参 `MerchantModifyIn` 即快照 JSON 载体,提交时序列化入库、审核详情反解析展示、通过时反解析写正式表——一份结构保证三处口径一致,杜绝"提交的"和"审核的"不一致
3. 自查数据源切换:有待审核记录 → 资料字段取快照(提交方看到自己最新提交的内容)+ 审核状态;无 → 取正式表生效内容
4. 单待审约束:同一主体同时仅一条待审核记录——提交前查 PENDING,存在即拒("审核中,请等待完成后再提交");驳回后可重新提交(新记录)

**审核状态机与并发互斥**
5. 状态机极简:待审核 → 通过/驳回(终态),仅待审核可流转;Service 层前置校验(非 PENDING 提示"已审核,请刷新列表")
6. CAS 收敛并发:`casAudit ... WHERE audit_status=0 AND is_del=0` 单 SQL 驱动流转,双运营并发审核仅一个生效,失败方提示刷新;**不用分布式锁,数据库行锁就是互斥手段**
7. 通过与驳回不对称:APPROVE 走 Manager 事务(CAS + 快照写正式表,双表一致性);REJECT 仅 CAS 终态回写,不进事务不动正式表
8. 驳回必填原因(入参校验强制 auditRemark);审核人/时间/备注随 CAS 一并落库,留痕完整

**事务边界**
9. Manager 事务只含同库双表写,且 CAS 是事务内第一步(抢不到直接 return false,无副作用);事务内禁 RPC/MQ/Redis
10. 写正式表用"只含主键+资料字段的最小更新实体"+ 专用 SQL `updateMerchantByAudit`(整体覆盖资料字段,version+1 在 SQL 内)——正式表唯一写入口,串行性由快照表 CAS 保证

**敏感数据红线**
11. 手机号:入参预编译正则校验格式;出参一律 `maskMobile` 脱敏(字段名带 `Mask` 后缀明示);提交日志只打定位字段(merchantId),不打全量资料
12. 快照 JSON 含明文手机号时,任何读取展示路径都必须过脱敏(自查/列表/详情三处一致)

**接口组织**
13. 提交方与审核方拆两个 Controller(按操作角色分,不按表分);审核方 URL 前缀 `/merchant/audit` 自成命名空间
14. 审核列表分页参数用 `@RequestParam(defaultValue=...)` 兜底;筛选状态空=全部(XML `<if>` 动态条件)
15. 枚举形态对齐 campaign 域:`value+desc+getDescByValue`(状态)/ `code+desc+fromCode`(操作类型)

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### 提交方 Controller(商家端)

```java
/**
 * 商家端资料 demo(修改审核流-提交方参考形态):
 * 提交修改只写审核快照,审核通过前正式表不变;自查在审核中时看到的是最新提交内容。
 */
@RestController
@RequestMapping("/merchant")
@Slf4j
public class MerchantController {

    @Resource
    private IMerchantService merchantService;

    /**
     * 提交资料修改(写快照,不动正式表;已有待审核申请时拒绝重复提交)。
     */
    @OperateLog(module = "merchant_modify", type = "submit", businessId = "#modifyIn.merchantId")  // [占位]
    @PostMapping("/modify/submit")
    public ApiResult submit(String _appId, HttpServletRequest request, @RequestBody MerchantModifyIn modifyIn) {
        // 入参含手机号明文:日志只打定位字段,不打全量资料
        log.info("MerchantController.submit appId={}, merchantId={}", _appId, modifyIn == null ? null : modifyIn.getMerchantId());
        ApiResult protocol = merchantService.submit(modifyIn);
        log.info("MerchantController.submit 资料修改提交 result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }

    /**
     * 商家自查资料:审核中=最新提交内容(快照)+ 审核状态;无申请=正式表生效内容。
     */
    @GetMapping("/info")
    public ApiResult<MerchantInfoOut> info(String _appId, HttpServletRequest request, Integer merchantId) {
        log.info("MerchantController.info appId={}, merchantId={}", _appId, merchantId);
        return merchantService.info(merchantId);
    }
}
```

### 审核方 Controller(运营端)

```java
/**
 * 后台运营审核 demo(修改审核流-审核方参考形态):
 * 列表筛选 → 详情看最新提交的快照 → 通过(写正式表)/ 驳回(仅回写状态)。
 * 审核状态机仅「待审核」可流转,CAS 保证双运营并发审核仅一个生效。
 */
@RestController
@RequestMapping("/merchant/audit")
@Slf4j
public class MerchantAuditController {

    @Resource
    private IMerchantAuditService merchantAuditService;

    /**
     * 审核列表分页:auditStatus 0待审核 1已通过 2已驳回,空=全部。
     */
    @GetMapping("/listByPage")
    public ApiResult<PageInfo<MerchantAuditListOut>> listByPage(String _appId, HttpServletRequest request,
                                                                Integer auditStatus,
                                                                @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        log.info("MerchantAuditController.listByPage appId={}, auditStatus={}, pageNum={}, pageSize={}",
                _appId, auditStatus, pageNum, pageSize);
        return ApiResult.succ(merchantAuditService.listByPage(auditStatus, pageNum, pageSize));
    }

    /**
     * 审核详情:展示该申请最新提交的资料内容(快照,手机号脱敏)。
     */
    @GetMapping("/detail")
    public ApiResult<MerchantAuditDetailOut> detail(String _appId, HttpServletRequest request, Integer recordId) {
        log.info("MerchantAuditController.detail appId={}, recordId={}", _appId, recordId);
        return merchantAuditService.detail(recordId);
    }

    /**
     * 审核:APPROVE 通过(快照写入正式表生效)/ REJECT 驳回(必填原因,正式表不变)。
     */
    @OperateLog(module = "merchant_audit", type = "check", businessId = "#checkIn.recordId")  // [占位]
    @PostMapping("/check")
    public ApiResult check(String _appId, HttpServletRequest request, @RequestBody MerchantAuditCheckIn checkIn) {
        log.info("MerchantAuditController.check appId={}, param={}", _appId, JSONObject.toJSONString(checkIn));
        LoginUser loginUser = UserHolder.getUser();                                              // [占位] → 项目登录态
        ApiResult protocol = merchantAuditService.check(loginUser, checkIn);
        log.info("MerchantAuditController.check 审核 result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }
}
```

### Service 接口(双端)

```java
/** 商家资料 Service(商家端):修改提交写快照 + 自查(审核中=最新提交内容) */
public interface IMerchantService {
    /** 提交资料修改:只写审核快照记录,不动正式表;存在待审核申请时拒绝重复提交 */
    ApiResult submit(MerchantModifyIn modifyIn);

    /** 商家自查资料:有待审核申请时资料字段为最新提交内容(快照),否则为正式表生效内容 */
    ApiResult<MerchantInfoOut> info(Integer merchantId);
}

/** 商家资料审核 Service(运营端):列表 / 详情(快照)/ 审核操作(状态机) */
public interface IMerchantAuditService {
    /** 审核列表分页(auditStatus 空=全部) */
    PageInfo<MerchantAuditListOut> listByPage(Integer auditStatus, Integer pageNum, Integer pageSize);

    /** 审核详情:展示该申请最新提交的资料内容(快照反解析,手机号脱敏) */
    ApiResult<MerchantAuditDetailOut> detail(Integer recordId);

    /** 审核:APPROVE 走事务写正式表;REJECT 仅 CAS 驳回(正式表不变)。仅待审核状态可操作 */
    ApiResult check(LoginUser auditor, MerchantAuditCheckIn checkIn);
}
```

### 提交方 ServiceImpl(写快照 + 数据源切换)

```java
/**
 * 双表模式的数据源切换规则:
 * - 提交:只写 merchant_modify_record 快照,正式表不变(审核通过前不生效)
 * - 自查:有 PENDING 记录 → 资料字段取快照;无 → 取正式表
 * 同一商家同时仅一条待审核申请;出参手机号一律脱敏。
 */
@Service
@Slf4j
public class MerchantServiceImpl implements IMerchantService {

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private MerchantModifyRecordMapper merchantModifyRecordMapper;

    @Override
    public ApiResult submit(MerchantModifyIn modifyIn) {
        ApiResult checkResult = checkModifyParam(modifyIn);
        if (!ReturnCode.SUCCESS.getValue().equals(checkResult.getCode())) {                      // [占位]
            return checkResult;
        }
        try {
            // 商家存在性校验(修改/删除类操作前必查)
            MerchantDO merchant = merchantMapper.selectByMerchantId(modifyIn.getMerchantId());
            if (Objects.isNull(merchant)) {
                return ApiResult.fail("商家不存在");
            }

            // 单待审约束:已有待审核申请时拒绝,避免多份快照歧义
            MerchantModifyRecordDO pending = merchantModifyRecordMapper.selectPendingByMerchantId(modifyIn.getMerchantId());
            if (Objects.nonNull(pending)) {
                return ApiResult.fail("资料修改审核中,请等待审核完成后再提交");
            }

            // 只写快照:整体 JSON 序列化入库,正式表不动
            MerchantModifyRecordDO record = new MerchantModifyRecordDO();
            record.setMerchantId(modifyIn.getMerchantId());
            record.setModifyContent(JSON.toJSONString(modifyIn));
            record.setAuditStatus(MerchantAuditStatusEnum.PENDING.getValue());
            merchantModifyRecordMapper.insert(record);
            log.info("MerchantServiceImpl.submit 资料修改已提交待审 recordId={}, merchantId={}",
                    record.getId(), modifyIn.getMerchantId());
            return ApiResult.succ();
        } catch (Exception e) {
            log.error("MerchantServiceImpl.submit 资料修改提交异常 merchantId={}", modifyIn.getMerchantId(), e);
            return ApiResult.fail("资料修改提交失败");
        }
    }

    @Override
    public ApiResult<MerchantInfoOut> info(Integer merchantId) {
        if (Objects.isNull(merchantId) || merchantId <= 0) {
            return ApiResult.fail("商家id必须为正整数");
        }
        try {
            MerchantDO merchant = merchantMapper.selectByMerchantId(merchantId);
            if (Objects.isNull(merchant)) {
                return ApiResult.fail("商家不存在");
            }
            MerchantInfoOut out = new MerchantInfoOut();
            out.setMerchantId(merchant.getId());
            out.setStatus(merchant.getStatus());
            out.setStatusStr(MerchantStatusEnum.getDescByValue(merchant.getStatus()));

            // 数据源切换:待审核中 → 展示最新提交内容(快照),商家看到的是自己刚提交的资料
            MerchantModifyRecordDO pending = merchantModifyRecordMapper.selectPendingByMerchantId(merchantId);
            if (Objects.nonNull(pending)) {
                MerchantModifyIn snapshot = JSON.parseObject(pending.getModifyContent(), MerchantModifyIn.class);
                out.setMerchantName(snapshot.getMerchantName());
                out.setContactName(snapshot.getContactName());
                out.setContactMobileMask(DesensitizeUtils.maskMobile(snapshot.getContactMobile()));  // [占位] → 项目脱敏工具
                // ……(省略:businessLicense / address / auditStatus+Str / submitTimeStr,同构 set)
            } else {
                out.setMerchantName(merchant.getMerchantName());
                out.setContactName(merchant.getContactName());
                out.setContactMobileMask(DesensitizeUtils.maskMobile(merchant.getContactMobile()));  // [占位]
                // ……(省略:businessLicense / address,同构 set)
            }
            return ApiResult.succ(out);
        } catch (Exception e) {
            log.error("MerchantServiceImpl.info 商家资料查询异常 merchantId={}", merchantId, e);
            return ApiResult.fail("商家资料查询失败");
        }
    }

    /**
     * 提交入参校验:必填 + 手机号格式(预编译正则)。
     */
    private ApiResult checkModifyParam(MerchantModifyIn modifyIn) {
        if (Objects.isNull(modifyIn) || Objects.isNull(modifyIn.getMerchantId())) {
            return ApiResult.fail("商家id不能为空");
        }
        if (StringUtils.isBlank(modifyIn.getMerchantName()) || StringUtils.isBlank(modifyIn.getContactName())) {
            return ApiResult.fail("商家名称与联系人不能为空");
        }
        if (StringUtils.isBlank(modifyIn.getContactMobile())
                || !PatternConsts.MOBILE_PATTERN.matcher(modifyIn.getContactMobile()).matches()) {  // [占位] → 项目正则常量
            return ApiResult.fail("联系手机号格式不正确");
        }
        return ApiResult.succ();
    }
}
```

### 审核方 ServiceImpl(状态机前置校验 + 分流)

```java
/**
 * 审核语义:详情看的是该申请最新提交的信息(快照反解析);
 * APPROVE 才写正式表(事务);REJECT 正式表不变;
 * 状态机前置校验:仅待审核可操作,已审记录 CAS 失败提示刷新。
 */
@Service
@Slf4j
public class MerchantAuditServiceImpl implements IMerchantAuditService {

    @Resource
    private MerchantModifyRecordMapper merchantModifyRecordMapper;

    @Resource
    private IMerchantAuditManager merchantAuditManager;

    @Override
    public PageInfo<MerchantAuditListOut> listByPage(Integer auditStatus, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            List<MerchantModifyRecordDO> doList = merchantModifyRecordMapper.pageList(auditStatus);
            PageInfo<MerchantModifyRecordDO> doPage = new PageInfo<>(doList);
            PageInfo<MerchantAuditListOut> resultPage = new PageInfo<>();
            BeanUtils.copyProperties(doPage, resultPage);
            resultPage.setList(convertListOut(doList));
            return resultPage;
        } catch (Exception e) {
            log.error("MerchantAuditServiceImpl.listByPage 审核列表查询异常 auditStatus={}", auditStatus, e);
            return new PageInfo<>(new ArrayList<>());
        }
    }

    @Override
    public ApiResult<MerchantAuditDetailOut> detail(Integer recordId) {
        if (Objects.isNull(recordId) || recordId <= 0) {
            return ApiResult.fail("记录id必须为正整数");
        }
        try {
            MerchantModifyRecordDO record = merchantModifyRecordMapper.selectById(recordId);
            if (Objects.isNull(record)) {
                return ApiResult.fail("审核记录不存在");
            }
            // 审核对象 = 最新提交内容:快照反解析展示(手机号脱敏)
            MerchantModifyIn snapshot = JSON.parseObject(record.getModifyContent(), MerchantModifyIn.class);
            MerchantAuditDetailOut out = new MerchantAuditDetailOut();
            out.setRecordId(record.getId());
            out.setMerchantId(record.getMerchantId());
            // ……(省略:快照资料字段 set(脱敏手机号)/ auditStatus+Str / auditRemark / auditorName /
            //      auditTimeStr / createdTimeStr,与 info 快照分支同构)
            return ApiResult.succ(out);
        } catch (Exception e) {
            log.error("MerchantAuditServiceImpl.detail 审核详情查询异常 recordId={}", recordId, e);
            return ApiResult.fail("审核详情查询失败");
        }
    }

    @Override
    public ApiResult check(LoginUser auditor, MerchantAuditCheckIn checkIn) {
        // 入参校验 + 操作类型解析
        if (Objects.isNull(checkIn) || Objects.isNull(checkIn.getRecordId())) {
            return ApiResult.fail("记录id不能为空");
        }
        MerchantAuditTypeEnum auditType = MerchantAuditTypeEnum.fromCode(checkIn.getAuditType());
        if (Objects.isNull(auditType)) {
            return ApiResult.fail("不支持的操作类型");
        }
        if (MerchantAuditTypeEnum.REJECT == auditType && StringUtils.isBlank(checkIn.getAuditRemark())) {
            return ApiResult.fail("驳回时必须填写驳回原因");
        }
        try {
            MerchantModifyRecordDO record = merchantModifyRecordMapper.selectById(checkIn.getRecordId());
            if (Objects.isNull(record)) {
                return ApiResult.fail("审核记录不存在");
            }
            // 状态机前置校验:仅待审核可流转
            if (!MerchantAuditStatusEnum.PENDING.getValue().equals(record.getAuditStatus())) {
                return ApiResult.fail("该申请已审核,请刷新列表");
            }

            if (MerchantAuditTypeEnum.APPROVE == auditType) {
                // 通过:事务内 CAS 审核记录 + 快照写正式表
                boolean approved = merchantAuditManager.approve(record, auditor);
                if (!approved) {
                    return ApiResult.fail("该申请已被其他运营审核,请刷新列表");
                }
                return ApiResult.succ();
            }

            // 驳回:仅 CAS 终态回写,正式表不变
            int rejected = merchantModifyRecordMapper.casAudit(record.getId(),
                    MerchantAuditStatusEnum.REJECTED.getValue(), checkIn.getAuditRemark(),
                    auditor.getUserCode(), auditor.getUserName());
            if (rejected == 0) {
                return ApiResult.fail("该申请已被其他运营审核,请刷新列表");
            }
            log.info("MerchantAuditServiceImpl.check 已驳回 recordId={}, auditor={}", record.getId(), auditor.getUserCode());
            return ApiResult.succ();
        } catch (Exception e) {
            log.error("MerchantAuditServiceImpl.check 审核异常 recordId={}, auditType={}",
                    checkIn.getRecordId(), checkIn.getAuditType(), e);
            return ApiResult.fail("审核操作失败");
        }
    }

    /**
     * DO → 列表出参:商家名取快照(与审核对象一致)。
     */
    private List<MerchantAuditListOut> convertListOut(List<MerchantModifyRecordDO> doList) {
        // ……(省略:循环 set recordId/merchantId/auditStatus+Str/auditorName/auditTimeStr/createdTimeStr;
        //      merchantName 从 modify_content 反解析快照取,null 安全降级空串)
        return new ArrayList<>();
    }
}
```

### 审核通过事务 Manager(接口 + 实现)

```java
/** 商家审核事务 Manager:审核通过 = 同库双表写(CAS 审核记录 + 快照写正式表),事务内无 RPC/MQ/Redis */
public interface IMerchantAuditManager {
    /**
     * 审核通过(事务):
     * ① CAS 待审核→通过(WHERE audit_status=0,并发审核仅一个成功,返回 false 由调用方提示)
     * ② 快照反解析 → 只含主键+资料字段的最小实体 UPDATE 正式表(version+1)
     *
     * @return true=审核通过且正式表已生效;false=记录已被其他运营先行审核
     */
    boolean approve(MerchantModifyRecordDO record, LoginUser auditor);
}

@Service
@Slf4j
public class MerchantAuditManagerImpl implements IMerchantAuditManager {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantModifyRecordMapper merchantModifyRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(MerchantModifyRecordDO record, LoginUser auditor) {
        // 1. CAS 待审核→通过:WHERE audit_status=0,双运营并发审核仅一个生效
        int updated = merchantModifyRecordMapper.casAudit(record.getId(),
                MerchantAuditStatusEnum.APPROVED.getValue(), null,
                auditor.getUserCode(), auditor.getUserName());
        if (updated == 0) {
            log.info("MerchantAuditManagerImpl.approve 审核已被他人先行处理 recordId={}", record.getId());
            return false;
        }

        // 2. 快照写正式表:反解析 JSON,组装只含主键+资料字段的最小更新实体(version+1 在 SQL 内)
        MerchantModifyIn snapshot = JSON.parseObject(record.getModifyContent(), MerchantModifyIn.class);
        MerchantDO updateDO = new MerchantDO();
        updateDO.setId(record.getMerchantId());
        updateDO.setMerchantName(snapshot.getMerchantName());
        // ……(省略:contactName / contactMobile / businessLicense / address,同构 set)
        merchantMapper.updateMerchantByAudit(updateDO);

        log.info("MerchantAuditManagerImpl.approve 审核通过并生效 recordId={}, merchantId={}, auditor={}",
                record.getId(), record.getMerchantId(), auditor.getUserCode());
        return true;
    }
}
```

### Mapper(接口 + XML)

```java
/** 商家正式资料 Mapper:正式表更新走专用 SQL(传只含主键+待更新字段的最小实体) */
@Mapper
public interface MerchantMapper extends BaseMapper<MerchantDO> {
    MerchantDO selectByMerchantId(@Param("merchantId") Integer merchantId);

    /** 审核通过写正式表:整体覆盖资料字段并 version+1(唯一写入口,串行性由审核记录 CAS 保证) */
    int updateMerchantByAudit(@Param("merchant") MerchantDO merchant);
}

/** 商家资料修改审核 Mapper:插入复用 BaseMapper.insert,查询/审核状态机走 XML */
@Mapper
public interface MerchantModifyRecordMapper extends BaseMapper<MerchantModifyRecordDO> {
    /** 查商家的待审核记录(同一商家同时仅一条,提交防重 + 自查数据源切换) */
    MerchantModifyRecordDO selectPendingByMerchantId(@Param("merchantId") Integer merchantId);

    /** 按状态分页查审核记录(运营审核列表,状态空=全部),配 PageHelper */
    List<MerchantModifyRecordDO> pageList(@Param("auditStatus") Integer auditStatus);

    /** CAS 审核状态机:WHERE audit_status=0,双运营并发审核仅一个成功(返回 1),失败方提示已审 */
    int casAudit(@Param("id") Integer id, @Param("targetStatus") Integer targetStatus,
                 @Param("auditRemark") String auditRemark, @Param("auditorNo") String auditorNo,
                 @Param("auditorName") String auditorName);
}
```

```xml
<!-- 正式表:查询 + 审核通过专用更新 -->
<mapper namespace="com.example.demo.dao.mapper.MerchantMapper">
    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.MerchantDO">
        <id column="id" property="id"/>
        <result column="merchant_name" property="merchantName"/>
        <result column="contact_name" property="contactName"/>
        <result column="contact_mobile" property="contactMobile"/>
        <!-- ……(省略:business_license / address / status / version / 审计字段 / is_del) -->
    </resultMap>

    <select id="selectByMerchantId" resultMap="BaseResultMap">
        select id, merchant_name, contact_name, contact_mobile, business_license, address,
               status, version, created_stime, modified_stime, is_del
        from merchant
        where is_del = 0
          and id = #{merchantId}
    </select>

    <!-- 审核通过写正式表:快照整体覆盖资料字段,version+1;WHERE 带主键与软删条件 -->
    <update id="updateMerchantByAudit">
        update merchant
        set merchant_name = #{merchant.merchantName},
            contact_name = #{merchant.contactName},
            contact_mobile = #{merchant.contactMobile},
            business_license = #{merchant.businessLicense},
            address = #{merchant.address},
            version = version + 1
        where id = #{merchant.id}
          and is_del = 0
    </update>
</mapper>

<!-- 快照表:防重查 + 分页 + CAS 状态机 -->
<mapper namespace="com.example.demo.dao.mapper.MerchantModifyRecordMapper">
    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.MerchantModifyRecordDO">
        <id column="id" property="id"/>
        <result column="merchant_id" property="merchantId"/>
        <result column="modify_content" property="modifyContent"/>
        <result column="audit_status" property="auditStatus"/>
        <!-- ……(省略:audit_remark / auditor_no / auditor_name / audit_time / 审计字段 / is_del) -->
    </resultMap>

    <select id="selectPendingByMerchantId" resultMap="BaseResultMap">
        select id, merchant_id, modify_content, audit_status, audit_remark, auditor_no, auditor_name,
               audit_time, created_stime, modified_stime, is_del
        from merchant_modify_record
        where is_del = 0
          and audit_status = 0
          and merchant_id = #{merchantId}
        order by id desc
        limit 1
    </select>

    <select id="pageList" resultMap="BaseResultMap">
        select id, merchant_id, modify_content, audit_status, audit_remark, auditor_no, auditor_name,
               audit_time, created_stime, modified_stime, is_del
        from merchant_modify_record
        where is_del = 0
        <if test="auditStatus != null">
            and audit_status = #{auditStatus}
        </if>
        order by id desc
    </select>

    <!-- CAS 审核状态机:仅待审核(0)可流转到通过/驳回,并发审核仅一个生效 -->
    <update id="casAudit">
        update merchant_modify_record
        set audit_status = #{targetStatus},
            audit_remark = #{auditRemark},
            auditor_no = #{auditorNo},
            auditor_name = #{auditorName},
            audit_time = now()
        where id = #{id}
          and audit_status = 0
          and is_del = 0
    </update>
</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
/** 商家正式资料表(仅审核通过的数据写入) */
@TableName("merchant")
public class MerchantDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("merchant_name")
    private String merchantName;

    @TableField("contact_name")
    private String contactName;

    /** 联系手机号(出参必须脱敏;生产建议加密存储,参考 leads 密文形态) */
    @TableField("contact_mobile")
    private String contactMobile;

    // ……(省略:businessLicense / address / status(1正常 2停用) / version(乐观锁,审核写正式表时+1)
    //      / created_stime / modified_stime / is_del(@TableLogic))

    // ……(省略:全字段 getter/setter,由 DAO 生成工具产出)
}

/** 商家资料修改审核表(快照,同一商家同时仅一条待审核) */
@TableName("merchant_modify_record")
public class MerchantModifyRecordDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("merchant_id")
    private Integer merchantId;

    /** 本次提交的资料JSON快照(审核对象,通过后整体写入正式表) */
    @TableField("modify_content")
    private String modifyContent;

    /** 审核状态 0待审核 1通过 2驳回 */
    @TableField("audit_status")
    private Integer auditStatus;

    /** 审核备注(驳回时必填原因) */
    @TableField("audit_remark")
    private String auditRemark;

    // ……(省略:auditorNo / auditorName / auditTime / created_stime / modified_stime / is_del(@TableLogic))

    // ……(省略:全字段 getter/setter)
}
```

### DTO(In / Out)

```java
/**
 * 提交入参——本 DTO 同时是审核快照的 JSON 载体:
 * 提交时整体序列化存入 modify_content,审核通过时反解析写回正式表
 * —— 提交、审核详情、生效三处共用同一份结构,保证口径一致。
 */
@Getter
@Setter
public class MerchantModifyIn implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer merchantId;
    private String merchantName;
    private String contactName;
    /** 联系手机号(服务端校验格式,出参展示一律脱敏) */
    private String contactMobile;
    /** 营业执照图片URL */
    private String businessLicense;
    private String address;
}

/** 审核入参(运营端) */
@Getter
@Setter
public class MerchantAuditCheckIn implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 审核记录ID */
    private Integer recordId;
    /** 审核操作 APPROVE通过 / REJECT驳回 */
    private String auditType;
    /** 审核备注(驳回时必填原因) */
    private String auditRemark;
}

/** 商家自查出参:auditStatus null=无审核中申请(资料=正式表);0=待审核(资料=最新提交快照) */
@Getter
@Setter
public class MerchantInfoOut implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer merchantId;
    private String merchantName;
    private String contactName;
    /** 脱敏手机号 137****0969 */
    private String contactMobileMask;
    // ……(省略:businessLicense / address / status+Str / auditStatus+Str / submitTimeStr)
}

/** 审核列表出参(运营端,分页):merchantName 取快照(与审核对象一致) */
@Getter
@Setter
public class MerchantAuditListOut implements Serializable {
    private static final long serialVersionUID = 1L;
    // ……(recordId / merchantId / merchantName / auditStatus+Str / auditorName / auditTimeStr / createdTimeStr)
}

/** 审核详情出参:资料字段为快照反解析内容(手机号脱敏)+ 审核信息 */
@Getter
@Setter
public class MerchantAuditDetailOut implements Serializable {
    private static final long serialVersionUID = 1L;
    // ……(recordId / merchantId / 快照资料字段(contactMobileMask 脱敏)/ auditStatus+Str / auditRemark /
    //      auditorName / auditTimeStr / createdTimeStr)
}
```

### 枚举

```java
/**
 * 审核状态机:待审核 → 通过 / 驳回(终态)。
 * 流转只能由 casAudit(WHERE audit_status=0)驱动,并发审核仅一个生效。
 * 驳回后商家可重新提交(新的待审核记录)。
 */
public enum MerchantAuditStatusEnum {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),
    ;

    private final Integer value;
    private final String desc;

    MerchantAuditStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() { return value; }

    public String getDesc() { return desc; }

    /** 按状态值取中文描述,未匹配返回空串(出参 statusStr 用) */
    public static String getDescByValue(Integer value) {
        for (MerchantAuditStatusEnum e : values()) {
            if (Objects.equals(e.getValue(), value)) {
                return e.getDesc();
            }
        }
        return "";
    }
}

/** 审核操作类型:code+desc+fromCode 解析,未匹配返 null 由调用方兜底(同 campaign 域形态) */
public enum MerchantAuditTypeEnum {

    APPROVE("APPROVE", "通过"),
    REJECT("REJECT", "驳回"),
    ;

    // ……(构造器/getter/fromCode 与 CampaignUpdateTypeEnum 同构)

    /** 按操作类型编码解析,未匹配返回 null,由调用方做失败兜底 */
    public static MerchantAuditTypeEnum fromCode(String code) {
        // ……(blank 返 null;遍历 code 精确匹配;未匹配返 null)
        return null;
    }
}

/** 商家状态(正式表字段,与审核状态是两个独立状态域,勿混用) */
public enum MerchantStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "停用"),
    ;

    // ……(value+desc+getDescByValue,与 MerchantAuditStatusEnum 同构)
}
```
