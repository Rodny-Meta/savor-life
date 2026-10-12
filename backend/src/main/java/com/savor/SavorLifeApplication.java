package com.savor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.savor.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true) // 暴露代理对象：AopContext.currentProxy() 取代理调用，保证事务生效
public class SavorLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SavorLifeApplication.class, args);
    }

}
