package com.taoshao.companionspace;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 */
@SpringBootApplication
@MapperScan("com.taoshao.companionspace.mapper")
@EnableScheduling//开启定时任务
public class CompanionSpaceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanionSpaceBackendApplication.class, args);
    }

}

