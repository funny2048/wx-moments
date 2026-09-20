# SqlServer开发设计规范

## 1. 表结构

- 表命名规则：业务名称_表用途；表名、字段名禁止使用关键保留字命名：如，add、proc、cross等，具体请参考SQL Server保留关键字；
- 【强制】新建表中字段必须设置为 NOT NULL.；
  正例： [Vin] [int] default(1) NOT NULL
- 【强制】禁止指定排序规则；禁止使用text类型，超长的文本类型可使用varchar(max)或narchar(max).
- 所有表必须包含以下4个通用字段：

```
[id] bigint PRIMARY KEY IDENTITY(1, 1),
[created_stime] DATETIME NOT NULL DEFAULT GETDATE(),
[modified_stime] DATETIME NOT NULL DEFAULT GETDATE(),
[is_del] INT NOT NULL DEFAULT 0
```

- 敏感字段(手机号、密码、用户真实姓名、身份证号、银行卡号、个人住址、员工姓名、公司税务标识、属于敏感信息，必须设计为加密存储)必须使用公司统一加密规则，并且加密字段使用统一命名规范；

可逆密文：字段名关键字_AHencrypt
hash密文：字段名关键字_AHhash
正例：bank_card_number_AHencrypt VARCHAR(512) NOT NULL DEFAULT '',
mobile_number_AHHash varchar(64)  NOT NULL DEFAULT ''

- 【强制】新建表中字段必须设置为 NOT NULL.；
  正例： [Vin] [int] default(1) NOT NULL
- 【强制】禁止指定排序规则；禁止使用text类型，超长的文本类型可使用varchar(max)或narchar(max).
- 【强制】数据库已有表新增列禁止使用not null；
  说明：not null default()形式会锁表，影响业务访问，如果需要not null default()建议联系DBA处理.
  正例：Alter table test add status bit default(0)
  反例：Alter table test add status bit not null default(0)

## 2. 索引

- 【强制】主键命名规则PK_tablename，索引命名规则IX_tablename_columnname，索引过长column可简写.
  正例：create index IX_test_name_createtime on test(name,createtime)
  with(online=on,fillfactor=90)
- 【强制】创建索引必须加 with(online=on,fillfactor=90)；
  说明：使用联机索引创建online=on，可以避免在索引创建时阻塞该表的DML操作；填充因子设置为fillfactor=90，为每个索引页预留了一定的空间，避免频繁页拆分产生过多碎片，影响修改和查询的效率.
  正例：create index IX_test_name_createtime on test(name,createtime)
  with(online=on,fillfactor=90)
- 【强制】禁止使用外键约束和检查约束； 