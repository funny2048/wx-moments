# Java 编码基准规范

> 通用 Java/Spring/MyBatis 后端编码基准，标签含义：【必须】强制执行 / 【禁止】不可触犯 / 【建议】优先采用
---

## 1. 分层与调用方向

- 【必须】遵循 Controller / Service / Dao(Mapper) 三层：Controller 负责参数校验与流程编排，Service 负责业务逻辑，Dao 负责数据库与中间件操作
- 【禁止】Controller 包含业务逻辑；Dao(Mapper) 包含业务判断或流程编排
- 【必须】调用方向为 Controller → Service → Dao；简单只读查询可 Controller → Dao 直连，禁止逆向调用（Dao → Service）

## 2. 分层代码编写规则

### 2.1 Controller层
-  类注解 `@RestController` + `@RequestMapping`，新方法优先 `@GetMapping`/`@PostMapping`，不使用 `@Validated`。
-  普通接口入参设计
  - 每个 public 方法第一个参数必须是 `String _appId`（无注解，Spring 自动绑定）。 
  - 【建议】接口入参超过 3 个时封装为 DTO
  - 【必须】日期、时间类入参用 `String` 接收，并校验格式（`YYYY-MM-DD` 或 `YYYY-MM-DD HH:mm:ss`）
  - 【必须】接口出入参数值类型使用包装类（如 `Integer`、`Long`），禁止使用原生类型（如 `int`、`long`）
-  普通接口出参设计
  - 【禁止】直接返回 Mapper 实体。
  - 【建议】返回的对象有 type、status、需要额外增加 xxTypeStr、xxStatusStr 对应的中文标识
  - 【建议】返回的对象有 Date、DateTime 的类型需要额外增加 xxDateStr（`YYYY-MM-DD`）、xxTimeStr（`YYYY-MM-DD HH:mm:ss`）
  - 【禁止】接口返回 `Map`、`Object`、裸字符串等非标准结构，必须用统一响应体封装
- 敏感接口(面向C端用户与B端客户的写入类接口（创建 / 修改 / 删除 / 下单 / 支付 / 退款 )的设计
  - 需要满足普通接口出入参设计
  -【必须】增加签名认证机制，参数必须有 `apiKey` + `timestamp` + `sign` 三参数认证，`timestamp` 纳入签名计算，服务端校验时间戳有效窗口（一般 ±5 分钟，按项目配置）拒超时请求，
  -【必须】签名实现优先复用项目已有签名工具类（先查 `knowledge/tech/utils-inventory.md`）；若项目无现成签名类，必须与用户确认实现方案（算法、密钥分发、参与签名字段），禁止自行新造
- 接口出入参数命名规则
  - 命名前先按包结构判断项目分层形式 ：存在 `domain`(聚合根 / 领域实体 / 仓储 Repository) / `application`(应用服务) / `infrastructure` / `interfaces` 等四层 → 领域驱动(DDD)项目；否则为传统 Controller / Service / Dao 三层项目
  - 【必须】领域驱动项目：Controller 入参统一 `XxxReq`、出参统一 `XxxRes`
  - 【必须】非领域驱动(三层)项目：Controller 入参统一 `XxxIn`、出参统一 `XxxOut`
  - 【必须】`Xxx` 按以下优先级逐级降级（高优先级信息缺失再用下一级）：
    1. 角色 + 动作：`RegionAuditIn`、`AdminSaveIn`
    2. 领域对象 + 动作：`ProjectEditIn`
    3. 仅动作：`AuditIn`
  - 【禁止】同一项目内混用 `In` / `Out` 与 `Req` / `Res` 两套后缀

### 2.2 Series 层
- 接口 `IXxxService`，实现 `XxxServiceImpl`。使用 `@Service` + `@Slf4j`。
- 【禁止】Service 使用 MyBatis-Plus 的 `QueryWrapper`/`LambdaQueryWrapper`，复杂查询必须写在 Mapper XML。


### 2.3 DAO 层- mapper
- Mapper 接口命名 `XxxMapper`，按数据源分包（`mapper/source1/`、`mapper/source2/`）。
- 全部 SQL 写在 XML 中，禁止注解 SQL（`@Select`/`@Insert` 等）。 
- 每个查询必须加  软删除条件 `is_del = 0`。 
- 【必须】SQL 参数使用 `#{}` 占位，禁止 `${}`（防 SQL 注入）
- 自定义查询方法每个参数加 `@Param("name")`。 
- 返回值禁止 `Map`/`List<Map>`，必须使用 DTO 或实体。 
- 批量 INSERT：`<foreach separator=",">`（多行插入）；批量 UPDATE：`<foreach separator=";">`（多条语句）。 
- 【禁止】跨数据库联表查询
- 【必须】更新操作新建一个只含主键 `id` + 待更新字段的实体对象再调用更新方法，禁止把查出的完整实体直接传入更新
- 【禁止】Mapper XML 裸写 `<` / `<=` ，`<` / `<=` 应该写成 `&lt;` / `&lt;=`，不等比较用 `!=`（等价免转义）,禁止使用`<>`
- 【必须】新增 / 修改 mapper XML 后收尾必跑 `find src/main/resources/mapper -name '*.xml' -exec xmllint --noout {} \;`，全目录通过才算完成
- 【禁止】生成 `DELETE` / `TRUNCATE` / `DROP` 表语句，以及无 `WHERE` 条件的 `UPDATE`
- 【建议】若项目使用 MyBatis-Plus，Mapper 优先继承 `BaseMapper<>`、Service 优先实现 `IService<>` 复用泛型 CRUD，避免重复代码

### 2.4 DAO 层- DO
- DO 实体手写 getter/setter，不使用 Lombok。
- 主键 `id` 统一 `Integer`，日期统一 `java.util.Date`。
- String setter 中做 trim：`this.field = field == null ? null : field.trim()`。

## 3. JDK API 偏好
- 逗号分隔 ID 字符串解析统一模式：`Stream.of(ids.split(",")).distinct().map(Integer::parseInt).collect(Collectors.toList())`。 
- List 转 Map 必须处理重复 key：`Collectors.toMap(kMapper, vMapper, (k1, k2) -> k2)`。 
- 分组用 `Collectors.groupingBy`，拼接用 `Collectors.joining(",")`，布尔匹配用 `anyMatch/noneMatch/allMatch`。 
- 排序统一 `Comparator.comparing(Item::getField).reversed()`，多字段 `.thenComparing()`。
- `Optional.ofNullable()` 链式 null 安全：`.map().filter().ifPresent().orElseGet()`，禁止裸 `get()`。 
- 广泛使用方法引用：`Item::getField`、`Integer::parseInt`、`this::toDto`。 
- 集合初始化统一菱形操作符 `<>`，`Map.getOrDefault(key, default)` 用于默认值。 
- 随机数用 `ThreadLocalRandom`，禁止 `java.util.Random`。 
- 文件操作用 `java.nio.file.Files` + `Path`，字符集 `StandardCharsets.UTF_8`。
- 【必须】新增工具方法前先查阅 `knowledge/tech/utils-inventory.md`，优先复用项目已有工具类（XxxUtil/XxxUtils/XxxHelper），禁止功能重复的新增
- 【必须】集合判空用 `CollectionUtils.isEmpty()`（`org.apache.commons.collections4`）
- 【必须】字符串判空用 `StringUtils`（`org.apache.commons.lang3`）
- 【必须】对象判空用 `Objects.nonNull()` / `Objects.isNull()`
- 【必须】方法处理无结果返回 `new ArrayList<>()`，禁止返回 `null`

## 4. 多数据源

- 【必须】Service 存在事务注解时，校验所调用 Mapper 的数据源标注是否正确，防止数据源切换失效
- 【必须】事务内切换数据源前，校验事务内各操作的数据源是否一致、是否有额外声明

## 5. 事务与一致性

- 【必须】涉及多表写操作时开启事务- 【必须】`@Transactional(rollbackFor = Exception.class)` 显式指定 `rollbackFor`，注解加在方法上
- 【禁止】`@Transactional` 方法内发起 RPC（HTTP / 网关）、发送 MQ、操作 Redis
- 【必须】将 RPC / MQ / Redis 操作移到事务方法之外，通过调用顺序保证最终一致性，避免长事务与资源阻塞

## 6. 业务正确性

### 6.1 幂等与防重

- 【必须】创建、下单、支付、退款、结算、优惠券、积分、活动等关键写操作保证幂等- 
- 【必须】请求携带唯一 `requestId`，基于 `requestId` / 业务 ID 做分布式锁(优先)或者其他幂等处理设计
- 【必须】`INSERT` 前先 `SELECT` 查重，或使用 UPSERT（`IF EXISTS...ELSE...`）
- 【必须】生产消息的地方不需要做幂等性处理，MQ消费者必须实现幂等处理。
- 【禁止】批量操作不先去重直接处理

### 6.2 状态流转

- 【必须】状态变更前校验当前状态是否允许流转到目标状态（状态机前置校验）
- 【禁止】状态值硬编码魔法数字，必须使用枚举或常量
- 【必须】状态变更后触发必要的通知 / 推送 / 同步逻辑

### 6.3 金额与精度

- 【禁止】金额、价格、补贴、优惠等字段使用 `double` / `float`
- 【禁止】`BigDecimal` 运算中转 `doubleValue()`
- 【必须】金额全程使用 `BigDecimal`；除法必须指定 `scale` 与 `RoundingMode.HALF_UP`
- 【禁止】用 `equals()` 比较 `BigDecimal`，必须用 `compareTo()`（精度敏感）
- 
### 6.4 数据关联一致性

- 【必须】修改核心实体字段时，检查关联字段 / 关联实体是否需要同步更新
- 【必须】DB 更新后同步更新对应的 ES 索引、Redis 缓存
- 【禁止】批量操作只更新主表而忽略关联表 / 缓存 / ES


## 7. 防御性编码

### 7.1 空指针防护

- 【禁止】对可能为 `null` 的对象链式调用（如 `obj.getA().getB()`）而无前置判空
- 【禁止】`Optional` 裸 `.get()`，必须配 `.orElse()` / `.orElseGet()`
- 【必须】Mapper 查询结果使用前判空；集合操作前用 `CollectionUtils.isNotEmpty()` 校验

### 7.2 边界与数值

- 【必须】数值范围同时校验上界与下界
- 【必须】除法运算前校验除数非零
- 【必须】字符串截取、数组下标访问前做长度 / 越界检查
- 【建议】批量操作设上限（≤500），禁止无限制全量处理

### 7.3 日期时间边界

- 【禁止】日期范围比较只用 `<` / `>` 而漏掉闭区间（`<=` / `>=`）
- 【必须】SQL 日期 `BETWEEN` 显式确认是否包含闭区间端点
- 【必须】日期范围结束时间设为当天 `23:59:59`
- 【禁止】`SimpleDateFormat` 作为 `static` 字段共享（非线程安全），必须用 `ThreadLocal` 包装或改用 `DateTimeFormatter`

### 7.4 参数校验

- 【必须】Controller public 方法对关键参数（ID、金额、状态码）做非空 / 正数 / 范围校验
- 【必须】修改 / 删除操作前校验目标实体存在性
- 【必须】外部输入（API 响应、用户输入、文件内容）一律按不可信处理，使用前校验，Controller 入参校验类型与推荐方案（Jakarta / javax validation 注解，需在类或方法参数上标注 `@Validated` / `@Valid` 触发生效）：

| 校验类型 | 推荐方案 |
| --- | --- |
| 非空（字符串） | `@NotBlank` |
| 非空（对象 / 集合） | `@NotNull` / `@NotEmpty` |
| 长度 | `@Size(min, max)` |
| 数值范围 | `@Min` + `@Max` |

## 8. 异常与错误码

- 【禁止】将异常堆栈信息通过接口直接返回前端
- 【禁止】静默吞掉异常（空 catch 块），必须显式处理或上抛并记录
- 【必须】捕获异常时打印 error 日志，含上下文参数与异常对象
- 【必须】参数校验失败且无需继续流程时，优先用统一返回体包装错误信息，非必要不抛异常
- 【禁止】手写 magic number 错误码或运行时动态拼接错误码，必须使用统一定义的错误码常量 / 枚举

## 9. 安全与权限

- 【必须】隶属用户的页面 / 功能 / 数据必须做权限校验，防止水平越权（无校验即可访问、修改、删除他人数据）
- 【必须】数据查询 / 修改 / 删除时校验操作人与数据归属关系
- 【必须】敏感操作前校验用户角色
- 【禁止】仅依赖前端权限控制，后端无任何校验
- 【必须】用户敏感数据展示前脱敏（如中国大陆手机号显示为 `137****0969`，隐藏中间 4 位）

## 10. 性能
- 【禁止】使用 Apache `BeanUtils` 复制属性，使用 Spring `BeanUtils`
- 【禁止】在方法体内 `Pattern.compile()`，正则必须预编译为 `static final` 常量

## 11. 日志

- 【必须】调用外部接口记录：请求 URL、请求参数、请求 Header（如有）、响应结果
- 【必须】Controller public 方法首行打印入参

## 12. 代码组织与工具类
- 【必须】保持不可变性：修改操作返回新对象，禁止就地修改已有对象（避免隐藏副作用、支持安全并发）
- 【建议】方法体 ≤50 行、单文件 ≤800 行（典型 200–400）、条件嵌套 ≤4 层，超出先重构
- 【建议】按功能 / 领域分包，高内聚低耦合，不按类型堆叠
- 【必须】同一逻辑被 ≥2 处调用且含 ≥3 个条件分支时，抽象为独立方法，禁止重复扩散
- 【必须】序列化框架在项目内保持统一，优先使用 FastJSON ，不推荐 Jackson / Gson

## 13. 硬性禁止
- 【禁止】中间件配置、超时参数等可以配置信息进行硬编码
- 【禁止】在 `CLAUDE.md`、源码、配置中记录密码、Token、凭证
- 【禁止】Git 提交包含任何凭证- 【禁止】读取 `application-*.yml`、`*.env`、`*.properties`、`*.config` 中的中间件连接信息与密码