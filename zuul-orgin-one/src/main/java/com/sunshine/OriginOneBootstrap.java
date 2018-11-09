package com.sunshine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/8 下午4:25
 */
@EnableEurekaClient
@SpringBootApplication
public class OriginOneBootstrap {

	public static void main(String[] args) {
		SpringApplication.run(OriginOneBootstrap.class);
	}
}
