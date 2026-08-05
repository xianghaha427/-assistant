package com.finoffice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 金融企业智能办公提效平台 - 后端启动类
 */
@SpringBootApplication
@MapperScan("com.finoffice.mapper")
public class SmartOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartOfficeApplication.class, args);
    }
}
