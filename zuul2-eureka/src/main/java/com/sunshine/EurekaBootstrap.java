package com.sunshine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka服务启动
 *
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/5 下午3:54
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaBootstrap {

	public static void main(String[] args) {
		SpringApplication.run(EurekaBootstrap.class);
	}
}
