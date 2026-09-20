package com.funny.moments.dao;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.converts.MySqlTypeConvert;
import com.baomidou.mybatisplus.generator.config.querys.MySqlQuery;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

/***
 * 代码生成器 执行 main 方法控制台输入模块表名回车自动生成对应项目目录中
 */
public class CodeGenerator {

    public static void main(String[] args) {
        System.out.println("=====================数据库配置=======================");
        // 凭证一律走环境变量，禁止写入源码：CODEGEN_DB_URL / CODEGEN_DB_USERNAME / CODEGEN_DB_PASSWORD
        String url = System.getenv("CODEGEN_DB_URL");
        String username = System.getenv("CODEGEN_DB_USERNAME");
        String password = System.getenv("CODEGEN_DB_PASSWORD");
        if (url == null || url.isBlank() || username == null || username.isBlank() || password == null || password.isBlank()) {
            System.out.println("请先配置环境变量 CODEGEN_DB_URL / CODEGEN_DB_USERNAME / CODEGEN_DB_PASSWORD");
            return;
        }
        String author = "funny2048";
        String parentName = "com.funny.sample";// 父包名
        String moduleName = "sample";// 父包模块名
        String tableName = "common_event";//表名，多个英文逗号分隔？所有输入 all
        AutoGenerator(url,
                username,
                password,
                author,
                parentName,
                moduleName,
                tableName);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 自动生成代码调用方法
     *
     * @param url        数据库地址
     * @param username   数据库用户名
     * @param password   数据库密码
     * @param author     作者
     * @param parentName 父包名
     * @param moduleName 父包模块名
     * @param tableName  表名，多个英文逗号分隔？所有输入 all
     */
    protected static void AutoGenerator(String url,
                                        String username,
                                        String password,
                                        String author,
                                        String parentName,
                                        String moduleName,
                                        String tableName) {
        String packagePath = parentName.replace('.', '/');
        String daoModuleName = moduleName + "-dao";
        String serviceModuleName = moduleName + "-service";
        String webModuleName = moduleName + "-web";
        FastAutoGenerator.create(
                        //数据源配置，url需要修改
                        new DataSourceConfig.Builder(url, username, password)
                                .dbQuery(new MySqlQuery())
                                .schema("cloud_user")
                                .typeConvert((globalConfig, fieldType) -> {
                                    String t = fieldType.toLowerCase();
                                    if (t.contains("tinyint")) {
                                        return DbColumnType.INTEGER;
                                    }
                                    if (t.contains("datetime")) {
                                        return DbColumnType.DATE;
                                    }
                                    //其它字段采用默认转换（非mysql数据库可以使用其它默认的数据库转换器）
                                    return new MySqlTypeConvert().processTypeConvert(globalConfig, fieldType);
                                })

                )
                // 全局配置
                .globalConfig(builder -> builder.author(author)// 设置作者名
                                .outputDir(System.getProperty("user.dir") + "/"+daoModuleName+"/src/main/java") //设置输出路径：项目的 java 目录下
                                .commentDate("yyyy-MM-dd hh:mm:ss")//注释日期
                                .dateType(DateType.ONLY_DATE)//定义生成的实体类中日期的类型 TIME_PACK=LocalDateTime;ONLY_DATE=Date;
//                        .enableSwagger()//开启 swagger 模式
                                .disableOpenDir() //禁止打开输出目录，默认打开
                )
                // 包配置
                .packageConfig(builder -> {
                            builder.parent(parentName)// 设置父包名
//                                    .moduleName(moduleName)//设置模块包名
                                    .entity("dao.entity") //entity 实体类包名
                                    .mapper("dao.mapper") //Mapper 包名
                                    .service("service")//Service 包名
                                    .serviceImpl("service.impl")// ***ServiceImpl 包名
                                    .xml("mapper") //Mapper XML 包名
                                    .controller("controller") //Controller 包名
                                    //                        .other("utils")//自定义文件包名
                                    //Collections.singletonMap(OutputFile.mapperXml, System.getProperty("user.dir") + "/sample-dao/src/main/resources/mapper")
                                    .pathInfo(new HashMap<OutputFile, String>() {{
                                        put(OutputFile.xml, System.getProperty("user.dir") + "/"+daoModuleName+"/src/main/resources/mapper");
                                        put(OutputFile.service, System.getProperty("user.dir") + "/"+serviceModuleName+"/src/main/java/"+packagePath+"/service/");
                                        put(OutputFile.serviceImpl, System.getProperty("user.dir") + "/"+serviceModuleName+"/src/main/java/"+packagePath+"/service/impl");
                                        put(OutputFile.controller, System.getProperty("user.dir") + "/"+webModuleName+"/src/main/java/"+packagePath+"/controller/");
                                    }});
                        } //配置 mapper.xml 路径信息：项目的
                        // resources 目录下
                )
                // 策略配置
                .strategyConfig(builder -> {
                    builder.addInclude(getTables(tableName))// 设置需要生成的数据表名
                            .addTablePrefix("t_", "c_") // 设置过滤表前缀

                            // service 策略配置
//                            .serviceBuilder()
//                            .formatServiceFileName("%sService")//格式化 service 接口文件名称，%s进行匹配表名，如 UserService
//                            .formatServiceImplFileName("%sServiceImpl") //格式化 service 实现类文件名称，%s进行匹配表名，如 UserServiceImpl

                            // 实体类策略配置
                            .entityBuilder()        //实体类策略配置
                            // 不开启 Lombok：DO 实体按编码基准手写 getter/setter
                            .disableSerialVersionUID() //不实现 Serializable 接口，不生产 SerialVersionUID
                            .logicDeleteColumnName("is_del")  //逻辑删除字段名
                            .naming(NamingStrategy.underline_to_camel) //数据库表映射到实体的命名策略：下划线转驼峰命
                            .columnNaming(NamingStrategy.underline_to_camel)  //数据库表字段映射到实体的命名策略：下划线转驼峰命
                            //添加表字段填充，"create_time"字段自动填充为插入时间，"modify_time"字段自动填充为插入修改时间
//                            .addTableFills(
//                                    new Column("created_stime", FieldFill.INSERT),
//                                    new Column("modified_stime", FieldFill.INSERT_UPDATE))
                            .formatFileName("%sDO")
                            .enableTableFieldAnnotation()       // 开启生成实体时生成字段注解
                            // Controller策略配置
                            .controllerBuilder()
//                            .formatFileName("%sController")//格式化 Controller 类文件名称，%s进行匹配表名，如 UserController
                            .enableRestStyle() //开启生成 @RestController 控制器
                            // Mapper策略配置
                            .mapperBuilder()
                            .superClass(BaseMapper.class) //设置父类
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .formatMapperFileName("%sMapper")  //格式化 mapper 文件名称
                            .enableMapperAnnotation()       //开启 @Mapper 注解
                            .formatXmlFileName("%sMapper"); //格式化 Xml 文件名称
                })
                //5、模板
                .templateEngine(new FreemarkerTemplateEngine())
                /*
                        .templateEngine(new VelocityTemplateEngine())
                        .templateEngine(new FreemarkerTemplateEngine())
                        .templateEngine(new BeetlTemplateEngine())
                        */
                //6、执行
                .execute();
                /*
                模板引擎配置，默认 Velocity 可选模板引擎 Beetl 或 Freemarker
                .templateEngine(new BeetlTemplateEngine())
                .templateEngine(new FreemarkerTemplateEngine())

                .execute();    */
    }

    // 处理 all 情况
    protected static List<String> getTables(String tables) {
        return "all".equals(tables) ? Collections.emptyList() : Arrays.asList(tables.split(","));
    }

}
