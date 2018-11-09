package com.sunshine.zuul;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/8 下午10:40
 */
@RestController
@RequestMapping("/origin/one")
public class OriginTwoController {

	@GetMapping("/hello")
	public String sayHello() {
		return String.format("%s-%s", "originTwo", String.valueOf(System.currentTimeMillis()));
	}
}
