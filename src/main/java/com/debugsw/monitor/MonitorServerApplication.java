package com.debugsw.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 统一监控平台服务端入口。 */
@SpringBootApplication
public class MonitorServerApplication {
    /** 启动监控平台。 */
    public static void main(String[] args) {
        SpringApplication.run(MonitorServerApplication.class, args);
    }
}
