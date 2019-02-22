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

import com.netflix.zuul.exception.ZuulFilterConcurrencyExceededException;
import com.netflix.zuul.message.ZuulMessage;
import io.netty.handler.codec.http.HttpContent;
import rx.Observable;

/**
 * Zuul基础过滤器
 *
 * @author Mikey Cohen
 * Date: 10/27/11
 * Time: 3:03 PM
 */
public interface ZuulFilter<I extends ZuulMessage, O extends ZuulMessage> extends ShouldFilter<I> {
	/**
	 * 是否生效
	 * @return
	 */
	boolean isDisabled();

	/**
	 * filter的名称
	 * @return
	 */
	String filterName();

	/**
	 * filter实现必须声明filterOrder()方法，如果你对filter的优先级并不关心，那么就可能会拥有相同优先级的Filters
	 * filterOrders不必按照顺序
	 *
	 * @return Filter的优先级，类型为int
	 */
	int filterOrder();

	/**
	 * 用于区分一个Filter的类型
	 * 前置路由过滤的标准类型是in
	 * Origin的类型是end
	 * 后置路由过滤的类型是out
	 * @return 过滤类型
	 */
	FilterType filterType();

	/**
	 * 确认filter的shouldFilter()方法是否需要检查，并且调用apply()方法，即使SessionContext.stopFilterProcessing属性已经设置
	 * @return boolean
	 */
	boolean overrideStopFilterProcessing();

	/**
	 * Zuul Filter调用线程会在发送请求通过这个filter时调用这个方法
	 * 如果达到了并发请求限制，并且准备拒绝执行这个请求，filter会抛出ZuulFilterConcurrencyExceedException
	 * 通常对异步的filters生效
	 */
	void incrementConcurrency() throws ZuulFilterConcurrencyExceededException;

	/**
	 * 如果shouldFilter()返回结果为true，这个方法将会被调用
	 * 是ZuulFilter的核心方法
	 */
	Observable<O> applyAsync(I input);

	/**
	 * 在请求被filter处理完成后会调用这个方法
	 */
	void decrementConcurrency();

	/**
	 * 获取同步类型
	 * @return
	 */
	FilterSyncType getSyncType();

	/**
	 * 如果applyAsync()方法抛出错误，那么这个方法将提供默认的响应文案
	 *
	 * @return ZuulMessage
	 */
	O getDefaultOutput(I input);

	/**
	 * filer指明它需要读取和缓存整个body，在它可以在reuturning true操作之前
	 * 描述在运行时完成，观察request type
	 * 如果一个正到达的消息是一个MSL消息，MSL解码，filter可以在解码之前return true，并备份整个MSL消息，
	 *
	 * @return true if this filter needs to read whole body before it can run, false otherwise
	 */
	boolean needsBodyBuffered(I input);

	/**
	 * 非强制性的传输接收到的HTTP内容块
	 *
	 * @param chunk
	 * @return
	 */
	HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk);
}
