package com.workeasy.erp.config

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration

/**
 * MyBatis 装配：只扫描新增的查询 Mapper。
 * 规范：Kotlin 接口 + `resources/mapper` 下 XML，namespace 必须等于接口全限定名；
 * 一律用 `#{}` 参数绑定，禁止 `${}` 拼接。
 */
@Configuration
@MapperScan("com.workeasy.erp.report.mapper", "com.workeasy.erp.mall.mapper")
class MyBatisConfig
