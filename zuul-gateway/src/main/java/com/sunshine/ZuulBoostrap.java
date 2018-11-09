package com.sunshine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Zuul网关启动
 *
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/8 下午3:58
 */
@EnableEurekaClient
@SpringBootApplication
public class ZuulBoostrap {

	public static void main(String[] args) {
		SpringApplication.run(ZuulBoostrap.class);
	}
}
