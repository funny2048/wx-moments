# 业务样例:后台导出(export)

> 本文件是 executor 编码前的风格对齐参照,不是可编译源码;基础设施依赖为占位形态(见「占位依赖对照表」),裁剪处以 `……(省略)` 标注。

## 匹配特征

- 主标签:`异步导出` / `提交即返回+记录落库` / `job 轮询扫描` / `文件生成` / `状态回写`
- 适用任务示例:后台管理列表导出 Excel/CSV、报表异步生成+轮询下载、批量文件生成并上传、任何"提交任务→后台慢做→回写产物"形态的长耗时操作

## 全链组成

```
Controller(/submit 提交、/list 轮询) → Service(接口 + Impl) → Mapper(接口 + XML) → DO / In / Out / 枚举
同步:submit 落库后同线程直调 executeOne;异步:XXL-Job Handler 扫描 INIT 逐条调 executeOne(同一执行管道)
执行管道:CAS 抢占(INIT→EXPORTING)→ 分页拉数生成 Excel → COS 上传 → CAS 终态回写(OK/FAIL)
```

涉及文件(包 `com.example.demo` 下):

| 层 | 文件 |
|---|---|
| 入口 | controller/ExportController.java |
| 调度 | task/ExportScanJobHandler.java |
| 服务 | service/export/IExportService.java、service/export/impl/ExportServiceImpl.java |
| 数据 | dao/entity/ExportRecordDO.java、dao/mapper/ExportRecordMapper.java、resources/mapper/common/ExportRecordMapper.xml |
| DTO | dto/in/ExportSubmitIn.java、dto/out/ExportSubmitOut.java、dto/out/ExportRecordOut.java |
| 枚举 | enums/ExportStatusEnum.java、enums/ExportTypeEnum.java |

数据源依赖 order 域只读件:OrderInfoMapper.selectOrderList、OrderQueryIn、OrderStatusEnum(导出内容来源,非本域文件,样例不展开)。

## 占位依赖对照表

样例代码中的基础设施类均为**占位**(类名保留、包路径已省略),编码时必须替换为本项目实际等价物,禁止照抄;开源类可直接引入:

| 占位类 | 代表角色 | 编码时 |
|---|---|---|
| `ApiResult` | 统一返回包装 | 本项目统一返回包装,保持 `succ / fail` 用法 |
| `LoginUser` / `UserHolder` | 登录态 | 项目内登录态获取方式(自研 UserContext / Security principal 等) |
| `ReturnCode` / `DateUtils` | 错误码 / 日期工具 | 项目内等价物(DateUtils 需 format/parseDate/isValid/formatDefault) |
| `CosFileClient` / `FileUploadResult` | 文件上传客户端 | OSS/COS/MinIO SDK 或自研上传封装,保持 `upload(key, bytes, contentType)` 返回含 url |
| `JobMDCAspect` | Job MDC 链路切面 | 仅做链路追踪,可删;调度本身用开源 @XxlJob |
| `mybatis-plus` / `pagehelper` | 开源·ORM 增强与分页 | MyBatis-Plus(插入/主键查复用 BaseMapper)+ PageHelper 物理分页,可直接引入 |
| `poi` / `xxl-job` | 开源·Excel 与调度 | Apache POI 生成 Excel(超大量换 SXSSF 流式);XXL-Job 调度,无 admin 换 @Scheduled/Quartz |
| `lombok` / `fastjson` / `commons-lang3` / `jakarta.annotation` | 开源·基础工具 | 直接引入 |

## 风格要点(看什么)

**任务建模(提交即返回+记录落库)**
1. 提交先落库再执行:submit 一律先 insert export_record(INIT),recordId 即受理凭证;绝不"先执行后补记录",任务异常也有据可查
2. 筛选条件整体 `JSON.toJSONString(submitIn)` 快照进 export_condition 列,执行时反解析回查,保证执行口径与提交时刻一致,不拆散存列
3. 同一执行管道双入口:syncFlag=1 落库后同线程直调 executeOne(响应即终态含 fileUrl);0/空 返回 INIT 由 Job 扫描、前端轮询 /list;不允许为同步模式另写一份导出逻辑

**Job 扫描与并发收敛**
4. Job 类只做调度壳:`@XxlJob("exportScanJob")` 方法体一行调 service.executeScan() 后 handleSuccess;扫描 SQL 按 created_stime asc FIFO、limit 50 单轮上限;executeScan 逐条 try-catch,单条失败仅记 error 继续下一跳,结束输出 scanned/done 统计
5. 并发抢占收敛到一条 CAS SQL:`update ... where id=? and export_status=前态`,返回 0 即被其他执行方(同步线程/Job/其他节点)抢走,直接让出 return false——同步异步并存、多节点部署都安全

**状态机回写与事务边界**
6. 状态机 INIT→EXPORTING→EXPORT_OK/EXPORT_FAIL 只经 casUpdateStatus 驱动;每步是独立 UPDATE,全程无 @Transactional,Excel 生成(纯内存)与上传(外部 IO)均不进事务
7. 终态回写带产物:成功回写 file_url+total_count;失败回写 fail_reason 且 `StringUtils.abbreviate(e.getMessage(), 500)` 截断防列宽溢出、totalCount 归 0;文件 key=`export/order_export_{recordId}_{yyyyMMddHHmmss}.xls` 可追溯且不覆盖

**文件生成与 OOM 防护**
8. 分页拉数:`PageHelper.startPage(pageNum, 500, false)` 第三参 false 关闭 count SQL;while(true) 翻页,停止条件双保险——空页 break + 页内条数<500 break
9. 单任务总量上限 EXPORT_MAX_ROWS=50000,超限抛 IllegalStateException 走 FAIL 分支,提示用户缩小筛选范围,不硬撑内存
10. 金额列一律字符串写入(`Objects.toString(price, "0")`),禁止 BigDecimal 转 double;文本列 `StringUtils.defaultString` 空安全

**接口契约与越权防护**
11. Controller 只做接参→入口日志→UserHolder 取登录人→调 service;分页参数 @RequestParam defaultValue 兜底;异常兜底在 service 内完成
12. 防水平越权:任务列表 SQL 强制 `export_operator_id=当前登录人`,只能看/下自己提交的导出文件,越权数据根本查不出
13. 校验独立 `checkSubmitParam(in)`:日期 String 入参用 isValid 逐条校验 yyyy-MM-dd、起止顺序校验,不合法 return ApiResult.fail,不抛异常

**通用纪律**
14. 日志:入口 info 参数 JSON 化,成功 info 带 url,失败 error 必带异常对象 e;轮询接口异常返空 PageInfo 不向前端抛错;容量常量(EXPORT_PAGE_SIZE 等)集中类顶部 private static final 并注释用途

## 代码

> 基础设施类(ApiResult/UserHolder 等)的 import 已省略,按文首对照表替换为本项目等价类;业务包 `com.example.demo` 对应你项目自己的业务包。

### Controller

```java
package com.example.demo.controller;

// ……(省略:基础设施类 import 见文首对照表;fastjson、本域 DTO/Service、spring-web、jakarta、lombok、PageInfo 标准导入)
/**
 * B 端订单导出 demo(异步任务参考形态):任务表落库 + 同步/异步双模式 + CAS 状态机 + COS 文件上传。
 * 防水平越权:任务列表强制 export_operator_id = 当前登录人,只能看/下自己的导出文件。
 */
@RestController
@RequestMapping("/export")
@Slf4j
public class ExportController {

    @Resource
    private IExportService exportService;

    /** 提交订单导出任务:syncFlag=1 同步执行(响应即终态,含下载地址);0/空 异步执行(返回 recordId 供轮询) */
    @PostMapping("/submit")
    public ApiResult<ExportSubmitOut> submit(String _appId, HttpServletRequest request, @RequestBody ExportSubmitIn submitIn) {
        log.info("ExportController.submit appId={}, param={}", _appId, JSONObject.toJSONString(submitIn));
        LoginUser loginUser = UserHolder.getUser();
        ApiResult<ExportSubmitOut> protocol = exportService.submit(loginUser, submitIn);
        log.info("ExportController.submit 导出提交 result={}", JSONObject.toJSONString(protocol));
        return protocol;
    }

    /** 导出任务列表(轮询进度/获取下载地址),仅可见本人提交的任务 */
    @GetMapping("/list")
    public ApiResult<PageInfo<ExportRecordOut>> list(String _appId, HttpServletRequest request, @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum, @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        log.info("ExportController.list appId={}, pageNum={}, pageSize={}", _appId, pageNum, pageSize);
        LoginUser loginUser = UserHolder.getUser();
        return ApiResult.succ(exportService.listByPage(loginUser, pageNum, pageSize));
    }
}
```

### Service 接口

```java
package com.example.demo.service.export;

// ……(import 区:基础设施类按文首对照表替换,本域 DTO 与 PageInfo 标准导入省略)
/** 订单导出 Service:任务表落库 + 同步/异步双模式执行 */
public interface IExportService {

    /** 提交导出任务:一律先落 export_record(INIT);syncFlag=1 同线程执行并返回终态,否则返回 INIT 由 Job 执行 */
    ApiResult<ExportSubmitOut> submit(LoginUser operator, ExportSubmitIn submitIn);

    /** 执行单个导出任务(同步/异步共用管道):CAS INIT→EXPORTING → 分页拉数生成 Excel → COS 上传 → CAS 终态;true=本执行方完成,false=被抢占 */
    boolean executeOne(Integer recordId);

    /** XXL-Job 入口:扫描 INIT 任务逐条执行,单条失败不影响其他 */
    void executeScan();

    /** 分页查当前操作人的导出任务(强制 operator 过滤防水平越权,轮询用) */
    PageInfo<ExportRecordOut> listByPage(LoginUser operator, Integer pageNum, Integer pageSize);
}
```

### ServiceImpl(裁剪版)

```java
package com.example.demo.service.export.impl;

// ……(import 区:基础设施类见文首对照表;poi(HSSF*)、pagehelper、fastjson、commons-lang3 开源直接引入;本域及订单域源文件自身)
/**
 * 订单导出 Service:任务表落库 + 同步/异步双模式 + CAS 状态机。全程无事务——CAS 每步独立 UPDATE、Excel 生成纯内存、COS 上传是外部 IO 均不进事务;
 * OOM 防护:每页 500 条,单任务上限 50000 行,超限置 FAIL 让运营缩小筛选范围。
 */
@Service
@Slf4j
public class ExportServiceImpl implements IExportService {

    /** 分页拉取每页条数(批量 ≤500)/ 单任务总量上限(OOM 防护)/ fail_reason 列宽上限(截断防 SQL 报错) */
    private static final int EXPORT_PAGE_SIZE = 500;
    private static final int EXPORT_MAX_ROWS = 50000;
    private static final int FAIL_REASON_MAX = 500;
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.ms-excel";
    /** Excel 表头(10 列) */
    private static final String[] EXCEL_HEADERS = {
            "订单号", "用户ID", "商品名称", "数量", "单价(元)", "促销优惠(元)", "券优惠(元)", "实付(元)", "订单状态", "下单时间"};

    @Resource
    private ExportRecordMapper exportRecordMapper;
    @Resource // 订单域 Mapper:导出数据源(只读)
    private OrderInfoMapper orderInfoMapper;
    @Resource
    private CosFileClient cosFileClient;

    @Override
    public ApiResult<ExportSubmitOut> submit(LoginUser operator, ExportSubmitIn submitIn) {
        ApiResult checkResult = checkSubmitParam(submitIn);
        if (!ReturnCode.SUCCESS.getValue().equals(checkResult.getCode())) {
            return checkResult;
        }
        try {
            // 1. 落任务:筛选条件 JSON 快照,保证执行口径与提交时刻一致
            ExportRecordDO record = new ExportRecordDO();
            record.setExportOperatorId(operator.getUserCode());
            // ……(省略:operatorName / exportType=ORDER_EXPORT / totalCount=0,同构 set)
            record.setExportCondition(JSON.toJSONString(submitIn));
            record.setExportStatus(ExportStatusEnum.INIT.getValue());
            exportRecordMapper.insert(record);
            log.info("ExportServiceImpl.submit 导出任务已创建 recordId={}, operator={}, syncFlag={}", record.getId(), operator.getUserCode(), submitIn.getSyncFlag());

            // 2. syncFlag=1 同线程直接执行(响应即终态);否则留给 XXL-Job 扫描
            if (Objects.nonNull(submitIn.getSyncFlag()) && submitIn.getSyncFlag() == 1) {
                executeOne(record.getId());
            }
            return ApiResult.succ(convertSubmitOut(exportRecordMapper.selectById(record.getId())));
        } catch (Exception e) {
            log.error("ExportServiceImpl.submit 导出任务提交异常 operator={}", operator.getUserCode(), e);
            return ApiResult.fail("导出任务提交失败");
        }
    }

    @Override
    public boolean executeOne(Integer recordId) {
        ExportRecordDO record = exportRecordMapper.selectById(recordId);
        if (Objects.isNull(record)) {
            log.warn("ExportServiceImpl.executeOne 任务不存在 recordId={}", recordId);
            return false;
        }
        // CAS 抢占:返回 0 = 已被其他执行方(同步线程/Job/其他节点)处理,直接让出
        int grabbed = exportRecordMapper.casUpdateStatus(record.getId(), ExportStatusEnum.INIT.getValue(), ExportStatusEnum.EXPORTING.getValue(), null, 0, null);
        if (grabbed == 0) {
            log.info("ExportServiceImpl.executeOne 任务被其他执行方抢占 recordId={}", recordId);
            return false;
        }
        try {
            byte[] excelBytes = doExport(record);
            // COS 上传(外部 IO,任何事务之外);key 带任务 id+时间戳,可追溯且不覆盖
            String key = "export/order_export_" + record.getId() + "_" + DateUtils.format(new Date(), "yyyyMMddHHmmss") + ".xls";
            FileUploadResult uploadResult = cosFileClient.upload(key, excelBytes, EXCEL_CONTENT_TYPE);
            exportRecordMapper.casUpdateStatus(record.getId(), ExportStatusEnum.EXPORTING.getValue(), ExportStatusEnum.EXPORT_OK.getValue(), uploadResult.getUrl(), record.getTotalCount(), null);
            log.info("ExportServiceImpl.executeOne 导出成功 recordId={}, url={}", recordId, uploadResult.getUrl());
            return true;
        } catch (Exception e) {
            log.error("ExportServiceImpl.executeOne 导出失败 recordId={}", recordId, e);
            String reason = StringUtils.abbreviate(e.getMessage(), FAIL_REASON_MAX);
            exportRecordMapper.casUpdateStatus(record.getId(), ExportStatusEnum.EXPORTING.getValue(), ExportStatusEnum.EXPORT_FAIL.getValue(), null, 0, reason);
            return false;
        }
    }

    @Override
    public void executeScan() {
        List<ExportRecordDO> tasks = exportRecordMapper.scanInit(ExportTypeEnum.ORDER_EXPORT.getValue());
        if (tasks.isEmpty()) {
            return;
        }
        int done = 0;
        for (ExportRecordDO task : tasks) {
            try {
                if (executeOne(task.getId())) {
                    done++;
                }
            } catch (Exception e) {
                // 单条失败不影响本轮其他任务
                log.error("ExportServiceImpl.executeScan 单任务执行异常 recordId={}", task.getId(), e);
            }
        }
        log.info("ExportServiceImpl.executeScan 完成 scanned={}, done={}", tasks.size(), done);
    }

    @Override
    public PageInfo<ExportRecordOut> listByPage(LoginUser operator, Integer pageNum, Integer pageSize) {
        // ……(省略:PageHelper.startPage → pageListByOperator(强制 operator 过滤)→ PageInfo 拷贝换分页 + convertRecordOut 转 Out,
        //      与 campaign.listByPage 同构;差异点:catch 兜底 return new PageInfo<>(new ArrayList<>()) 不抛错)
    }

    /** 生成 Excel 字节流:分页拉取(500/页)→ 逐页写 Sheet → 统计总行数回写 record.totalCount。金额列以字符串写入。 */
    private byte[] doExport(ExportRecordDO record) throws Exception {
        OrderQueryIn queryParam = buildQueryParam(record.getExportCondition());
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet sheet = workbook.createSheet("订单导出");
        // 表头:for 循环逐列 createCell 写 EXCEL_HEADERS(省略 4 行循环)
        int rowIndex = 1, totalCount = 0, pageNum = 1;
        while (true) {
            PageHelper.startPage(pageNum, EXPORT_PAGE_SIZE, false);
            List<OrderInfoDO> pageList = orderInfoMapper.selectOrderList(queryParam);
            if (pageList.isEmpty()) {
                break;
            }
            for (OrderInfoDO order : pageList) {
                writeRow(sheet.createRow(rowIndex++), order);
            }
            totalCount += pageList.size();
            if (totalCount > EXPORT_MAX_ROWS) {
                throw new IllegalStateException("导出数据超上限 " + EXPORT_MAX_ROWS + " 行，请缩小筛选范围");
            }
            if (pageList.size() < EXPORT_PAGE_SIZE) {
                break;
            }
            pageNum++;
        }
        record.setTotalCount(totalCount);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    /** 条件快照 → 查询参数:日期 String 转 Date(校验已在前置完成),结束日期拼 23:59:59 闭区间 */
    private OrderQueryIn buildQueryParam(String conditionJson) {
        ExportSubmitIn snapshot = JSON.parseObject(conditionJson, ExportSubmitIn.class);
        OrderQueryIn queryParam = new OrderQueryIn();
        queryParam.setOrderStatus(snapshot.getOrderStatus());
        if (StringUtils.isNotBlank(snapshot.getStartDate())) {
            queryParam.setStartTime(DateUtils.parseDate(snapshot.getStartDate(), DATE_PATTERN));
        }
        // ……(省略:endDate 同构 if;差异点:拼 " 23:59:59" 闭区间、用 DATE_TIME_PATTERN)
        return queryParam;
    }

    private void writeRow(HSSFRow row, OrderInfoDO order) {
        row.createCell(0).setCellValue(StringUtils.defaultString(order.getOrderNo()));
        // ……(省略:cell 1-3 用户ID/商品名称/数量,defaultString / null 判 0 同构)
        // 金额一律字符串写入,避免 double 精度丢失
        row.createCell(4).setCellValue(Objects.toString(order.getUnitPrice(), "0"));
        // ……(省略:cell 5-6 促销/券优惠与单价同构;cell 8-9 订单状态枚举 desc / 下单时间 formatDefault 直写)
        row.createCell(7).setCellValue(Objects.toString(order.getPayAmount(), "0"));
    }

    /** 入参校验:日期入参用 String 接收,格式逐条校验,不合法 return fail 不抛异常 */
    private ApiResult checkSubmitParam(ExportSubmitIn submitIn) {
        if (Objects.isNull(submitIn)) {
            return ApiResult.fail("导出参数不能为空");
        }
        // ……(省略:startDate/endDate 各一条 isValid(DATE_PATTERN) 格式校验,同构 return fail)
        if (StringUtils.isNotBlank(submitIn.getStartDate()) && StringUtils.isNotBlank(submitIn.getEndDate()) && submitIn.getStartDate().compareTo(submitIn.getEndDate()) > 0) {
            return ApiResult.fail("开始日期不能晚于结束日期");
        }
        return ApiResult.succ();
    }

    private ExportSubmitOut convertSubmitOut(ExportRecordDO record) {
        ExportSubmitOut out = new ExportSubmitOut();
        out.setRecordId(record.getId());
        out.setExportStatus(record.getExportStatus());
        out.setStatusStr(ExportStatusEnum.getDescByValue(record.getExportStatus()));
        // ……(省略:fileUrl/totalCount/failReason 同构直拷;convertRecordOut 亦同构,差异点:id 替代 recordId、多 createdTimeStr)
        return out;
    }
}
```

### Job(XXL-Job 调度入口)

```java
import com.xxl.job.core.handler.annotation.XxlJob;
// ……(省略:XxlJobHelper、本域 IExportService、Component/Resource/lombok 标准导入)
/**
 * 订单导出异步扫描 Job:扫描 export_record 中 INIT 状态任务,逐条执行导出管道(CAS 抢占,多节点安全)。
 * 需在 xxl-job admin 配置 JobHandler=exportScanJob;MDC 链路追踪由 {@link JobMDCAspect} 切面自动注入。
 */
@Slf4j
@Component
public class ExportScanJobHandler {

    @Resource
    private IExportService exportService;

    @XxlJob("exportScanJob")
    public void execute() {
        exportService.executeScan();
        XxlJobHelper.handleSuccess();
    }
}
```

### Mapper(接口 + XML)

```java
@Mapper
public interface ExportRecordMapper extends BaseMapper<ExportRecordDO> {

    /** 按导出人+类型分页查导出记录(配合 PageHelper):强制 operator 过滤防水平越权 */
    List<ExportRecordDO> pageListByOperator(@Param("operatorId") String operatorId, @Param("exportType") Integer exportType);

    /** 扫描 INIT 状态任务(XXL-Job 入口),FIFO 取前 50 条 */
    List<ExportRecordDO> scanInit(@Param("exportType") Integer exportType);

    /** CAS 状态机更新:WHERE export_status=前态,多节点并发抢占仅一个成功;@return 1=抢占成功,0=状态已被其他执行方改变 */
    int casUpdateStatus(@Param("id") Integer id, @Param("preStatus") Integer preStatus,
                        @Param("curStatus") Integer curStatus, @Param("fileUrl") String fileUrl,
                        @Param("totalCount") Integer totalCount, @Param("failReason") String failReason);
}
```

```xml
<mapper namespace="com.example.demo.dao.mapper.ExportRecordMapper">

    <resultMap id="BaseResultMap" type="com.example.demo.dao.entity.ExportRecordDO">
        <id column="id" property="id"/>
        <result column="export_operator_id" property="exportOperatorId"/>
        <result column="export_type" property="exportType"/>
        <result column="export_condition" property="exportCondition"/>
        <result column="export_status" property="exportStatus"/>
        <result column="fail_reason" property="failReason"/>
        <!-- ……(省略:operator_name / file_url / total_count / created_stime / modified_stime / is_del 列映射,同构) -->
    </resultMap>

    <sql id="Base_Column_List">
        id, export_operator_id, export_type, export_condition, file_url, <!-- ……(省略其余列) --> is_del
    </sql>

    <!-- 防水平越权:强制 export_operator_id 过滤,只允许导出人查自己的任务 -->
    <select id="pageListByOperator" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from export_record
        where is_del = 0
          and export_operator_id = #{operatorId}
          and export_type = #{exportType}
        order by created_stime desc
    </select>

    <!-- XXL-Job 扫描:INIT 任务 FIFO,单轮最多 50 条 -->
    <select id="scanInit" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List"/>
        from export_record
        where is_del = 0
          and export_status = 0
          and export_type = #{exportType}
        order by created_stime asc
        limit 50
    </select>

    <!-- CAS 状态机:WHERE export_status=前态,并发抢占仅一个成功;file_url/total_count 成功时回写、fail_reason 失败时回写 -->
    <update id="casUpdateStatus">
        update export_record
        set export_status = #{curStatus},
            file_url = #{fileUrl},
            total_count = #{totalCount},
            fail_reason = #{failReason}
        where id = #{id}
          and export_status = #{preStatus}
    </update>
</mapper>
```

### DO(字段区;getter/setter 为工具生成物,省略)

```java
@TableName("export_record")
public class ExportRecordDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    /** 导出人工号(B端登录态),列表查询强制过滤防水平越权 */
    @TableField("export_operator_id")
    private String exportOperatorId;
    // ……(省略:export_operator_name 普通列,同构 @TableField)
    /** 导出类型 1订单导出 */
    @TableField("export_type")
    private Integer exportType;
    /** 导出筛选条件JSON快照(执行时反解析回查) */
    @TableField("export_condition")
    private String exportCondition;
    /** 导出文件URL(执行成功后回写) */
    @TableField("file_url")
    private String fileUrl;
    /** 导出状态 0INIT 1EXPORTING 2EXPORT_OK 3EXPORT_FAIL */
    @TableField("export_status")
    private Integer exportStatus;
    /** 导出数据总行数(执行后回写) */
    @TableField("total_count")
    private Integer totalCount;
    @TableField("fail_reason")
    private String failReason;
    // ……(省略:created_stime / modified_stime 审计列,同构 @TableField)
    @TableField("is_del")
    @TableLogic
    private Integer isDel;

    // ……(省略:全字段 getter/setter,由 DAO 生成工具产出;String setter 带 null 安全 trim)
}
```

### DTO(In / Out)

```java
/** 提交入参:筛选条件整体 JSON 快照落库,syncFlag 控同步/异步 */
@Getter @Setter
public class ExportSubmitIn implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 是否同步执行:1=入库后同线程直接导出(响应即终态);0/空=异步,由 Job 扫描执行,前端轮询任务列表 */
    private Integer syncFlag;
    /** 订单状态 1已创建 2已关闭,空=全部 */
    private Integer orderStatus;
    /** 下单开始日期(含),格式 yyyy-MM-dd,服务端校验并转换 */
    private String startDate;
    /** 下单结束日期(含当天 23:59:59),格式 yyyy-MM-dd */
    private String endDate;
}

/** 提交结果:同步模式返回终态(含 fileUrl),异步模式返回 INIT 供轮询 */
@Getter @Setter
public class ExportSubmitOut implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 导出任务ID(异步轮询主键) */
    private Integer recordId;
    private Integer exportStatus;
    private String statusStr;
    /** 导出文件URL(成功后可下载) */
    private String fileUrl;
    // ……(省略:totalCount / failReason,同构)
}

/** 任务列表出参(轮询用) */
@Getter @Setter
public class ExportRecordOut implements Serializable {
    // ……(省略:id/exportStatus/statusStr/fileUrl/totalCount/failReason/createdTimeStr 七字段,
    //      与 ExportSubmitOut 同构;差异点:id 替代 recordId、多 createdTimeStr(yyyy-MM-dd HH:mm:ss 格式化))
}
```

### 枚举

```java
/** 状态机枚举:INIT → EXPORTING → EXPORT_OK / EXPORT_FAIL,流转只经 casUpdateStatus(WHERE export_status=前态)驱动 */
public enum ExportStatusEnum {

    INIT(0, "待处理"),
    EXPORTING(1, "导出中"),
    EXPORT_OK(2, "导出成功"),
    EXPORT_FAIL(3, "导出失败"),
    ;

    private final Integer value;
    private final String desc;

    ExportStatusEnum(Integer value, String desc) { this.value = value; this.desc = desc; }
    public Integer getValue() { return value; }
    public String getDesc() { return desc; }
    /** 按状态值取中文描述,未匹配返回空串(出参 statusStr 用) */
    public static String getDescByValue(Integer value) {
        for (ExportStatusEnum e : values()) {
            if (Objects.equals(e.getValue(), value)) {
                return e.getDesc();
            }
        }
        return "";
    }
}

/** 导出类型枚举(export_record.export_type,预留扩展多数据源导出) */
public enum ExportTypeEnum {

    ORDER_EXPORT(1, "订单导出"),
    ;
    // ……(省略:value/desc 字段、构造器与 getter 同 ExportStatusEnum;无静态解析方法)
}
```
