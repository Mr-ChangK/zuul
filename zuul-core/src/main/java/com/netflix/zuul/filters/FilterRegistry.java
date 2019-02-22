/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.filters;


import javax.inject.Singleton;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter注册器
 * @author mhawthorne
 */
@Singleton
public class FilterRegistry {
	/**
	 * 用ConcurrentHashMap来存储filter
	 */
	private final ConcurrentHashMap<String, ZuulFilter> filters = new ConcurrentHashMap<String, ZuulFilter>();

	/**
	 * 删除filter
	 * @param key
	 * @return
	 */
	public ZuulFilter remove(String key) {
		return this.filters.remove(key);
	}

	/**
	 * 获取filter
	 * @param key
	 * @return
	 */
	public ZuulFilter get(String key) {
		return this.filters.get(key);
	}

	/**
	 * 写入filter
	 * @param key
	 * @param filter
	 */
	public void put(String key, ZuulFilter filter) {
		this.filters.putIfAbsent(key, filter);
	}

	/**
	 * 统计filter的个数
	 * @return
	 */
	public int size() {
		return this.filters.size();
	}

	/**
	 * 获取所有的filter
	 * @return
	 */
	public Collection<ZuulFilter> getAllFilters() {
		return this.filters.values();
	}
}
