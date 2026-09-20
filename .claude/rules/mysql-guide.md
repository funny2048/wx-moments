# MYSQL开发设计规范

## 1. 数据库设计规范

- 表名与字段统一使用 `snake_case` ，表名不使用复数名词，表名、字段名必须使用小写字母或数字，禁止出现数字开头，禁止两个下划线中间只出现数字

- 二值字段（是否/有无/对错）数据类型统一 unsigned tinyint(1)（1=是 0=否），命名优先级：
  1. 优先用状态动词/过去分词：shipped、locked、verified、activated、enabled、published
  2. 其次用 can_xx / has_xx / need_xx / allow_xx / support_xx / enable_xx
  3. 无法用自然语义表达时再用 xxx_flag（如 default_flag、manual_flag）
  4. 禁止 is_xx / xxx_ind / xxx_yn 前缀（POJO getter/setter 与序列化字段名歧义）；通用逻辑删除字段 is_del 为既定约定，保留例外
- 非二值字段（状态/类型）必须用带前缀语义名 xx_status / xx_type（如 order_status、pay_type），禁止裸用 status / type；枚举值 ≤10 用 tinyint(2)，>10 用 tinyint(4)；Java 侧必须配套枚举类，code 与库值一一对应，COMMENT 列出全部 值:含义 映射，提供 byCode(Integer) 反查，禁止业务代码出现魔法数字

- 小数类型为 decimal，禁止使用 float 和 double。
- 金额字段统一使用 decimal(15,2) 存储，精度按业务调整须全表统一；若用整数存分须命名 xxx_fen 并在 COMMENT 标注单位为分

- 如果存储的字符串长度几乎相等，使用 char 定长字符串类型。

- varchar 是可变长字符串，不预先分配存储空间，长度不要超过 5000，如果存储长度

  大于此值，定义字段类型为 text，独立出来一张表，用主键来对应，避免影响其它字段索引效

  率。

- 时间字段统一使用 datetime 类型并命名 xxx_time，仅存日期使用 date 类型并命名 xxx_date，禁止用 varchar 存储时间
- 所有表必须包含以下4个通用字段：

> `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
>
> `created_stime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
>
> `modified_stime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
>
> `is_del` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除 0 正常 1 删除',

- 所有字段必须加 COMMENT 描述业务含义，枚举字段注释须列出全部 值:含义 映射；禁止裸字段名上库
- 手机号、密码、用户真实姓名、身份证号、银行卡号、个人住址、员工姓名、公司税务标识、属于敏感信息，必须设计为加密存储，字段后缀 `_encrypt`，手机号额外增加 `_hash` 字段。
- 主键索引名为 `pk_字段名`；唯一索引名为 `uniq_字段名`；普通索引名则为` idx_字段名`。
- 超过三个表禁止 join ，如需join，必须需要确认，多表 join 必须要注意表索引、SQL 性能。
- 禁止使用外键约束和检查约束；
- 新设计的表至少设置1-3个索引字段,
- 如果数据库表是面向C端用户处理以及B端定时任务批量处理的情况，需要额外注意SQL的性能是否会受到表索引的影响。