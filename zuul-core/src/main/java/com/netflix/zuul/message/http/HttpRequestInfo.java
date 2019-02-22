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

package com.netflix.zuul.message.http;

import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;

/**
 * Http请求信息
 * User: Mike Smith
 * Date: 7/15/15
 * Time: 1:18 PM
 */
public interface HttpRequestInfo extends ZuulMessage {
	String getProtocol();

	String getMethod();

	String getPath();

	HttpQueryParams getQueryParams();

	String getPathAndQuery();

	Headers getHeaders();

	String getClientIp();

	String getScheme();

	int getPort();

	String getServerName();

	int getMaxBodySize();

	String getInfoForLogging();

	String getOriginalHost();

	String getOriginalScheme();

	String getOriginalProtocol();

	int getOriginalPort();

	String reconstructURI();

	/**
	 * 解析并懒缓存请求的cookies
	 */
	Cookies parseCookies();

	/**
	 * 强制解析或是重解析cookies
	 * 当headers在cookies第一次解析后发生了变化
	 */
	Cookies reParseCookies();
}
