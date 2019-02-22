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

import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.spectator.api.Counter;
import com.netflix.zuul.exception.ZuulFilterConcurrencyExceededException;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.netty.SpectatorUtils;
import io.netty.handler.codec.http.HttpContent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 旧Zuul的基抽象类，实现了ZuulFilter接口，同时也声明了一些需要实现的抽象方法
 * 之前的Filter类型是pre为前置路由过滤，route为Origin路由过滤，post为后置路由过滤，error为异常处理
 * 静态响应可以使用StaticResponseFilter
 *
 * 静态路由过滤器一般不会有状态值，通常表现为isStaticFilter()方法的值为false
 * @author Mikey Cohen
 * Date: 10/26/11
 * Time: 4:29 PM
 */
public abstract class BaseFilter<I extends ZuulMessage, O extends ZuulMessage> implements ZuulFilter<I, O> {
	private final String baseName;
	private final AtomicInteger concurrentCount;
	private final Counter concurrencyRejections;

	private final CachedDynamicBooleanProperty filterDisabled;
	private final CachedDynamicIntProperty filterConcurrencyLimit;

	/**
	 * 是否开启并发保护，属性zuul.filter.concurrency.protect.enabled，默认值为true
	 */
	private static final CachedDynamicBooleanProperty concurrencyProtectEnabled = new CachedDynamicBooleanProperty("zuul.filter.concurrency.protect.enabled", true);


	protected BaseFilter() {
		baseName = this.getClass().getSimpleName() + "." + filterType().toString();
		concurrentCount = SpectatorUtils.newGauge("zuul.filter.concurrency.current", baseName, new AtomicInteger(0));
		concurrencyRejections = SpectatorUtils.newCounter("zuul.filter.concurrency.rejected", baseName);
		filterDisabled = new CachedDynamicBooleanProperty(disablePropertyName(), false);
		filterConcurrencyLimit = new CachedDynamicIntProperty(maxConcurrencyPropertyName(), 4000);
	}

	@Override
	public String filterName() {
		// 显示当前类的名称
		return this.getClass().getName();
	}

	@Override
	public boolean overrideStopFilterProcessing() {
		// 是否重写停止filter处理
		return false;
	}

	/**
	 * 生成使失效filter的名称
	 * 默认是zuul.${className}.${filterType}.disable
	 *
	 * @return
	 */
	public String disablePropertyName() {
		return "zuul." + baseName + ".disable";
	}

	/**
	 * filter支持的最大并发数
	 * 默认是zuul.${className}.${filter}.concurrency.limit
	 *
	 * @return
	 */
	public String maxConcurrencyPropertyName() {
		return "zuul." + baseName + ".concurrency.limit";
	}

	/**
	 * 判断filter的状态是否为已失效
	 *
	 * @return
	 */
	@Override
	public boolean isDisabled() {
		return filterDisabled.get();
	}

	@Override
	public O getDefaultOutput(I input) {
		return (O) input;
	}

	@Override
	public FilterSyncType getSyncType() {
		// filter的同步方式
		return FilterSyncType.ASYNC;
	}

	@Override
	public String toString() {
		return String.valueOf(filterType()) + ":" + String.valueOf(filterName());
	}

	@Override
	public boolean needsBodyBuffered(I input) {
		// 是否需要进行缓存body
		return false;
	}

	@Override
	public HttpContent processContentChunk(ZuulMessage zuulMessage, HttpContent chunk) {
		// 处理HttpContent chunk
		return chunk;
	}

	@Override
	public void incrementConcurrency() throws ZuulFilterConcurrencyExceededException {
		final int limit = filterConcurrencyLimit.get();
		if ((concurrencyProtectEnabled.get()) && (concurrentCount.get() >= limit)) {
			concurrencyRejections.increment();
			throw new ZuulFilterConcurrencyExceededException(this, limit);
		}
		concurrentCount.incrementAndGet();
	}

	@Override
	public void decrementConcurrency() {
		concurrentCount.decrementAndGet();
	}

	public static class TestUnit {
		@Mock
		private BaseFilter f1;
		@Mock
		private BaseFilter f2;
		@Mock
		private ZuulMessage req;

		@Before
		public void before() {
			MockitoAnnotations.initMocks(this);
		}


		@Test
		public void testShouldFilter() {
			class TestZuulFilter extends BaseSyncFilter {
				@Override
				public int filterOrder() {
					return 0;
				}

				@Override
				public FilterType filterType() {
					return FilterType.INBOUND;
				}

				@Override
				public boolean shouldFilter(ZuulMessage req) {
					return false;
				}

				@Override
				public ZuulMessage apply(ZuulMessage req) {
					return null;
				}
			}

			TestZuulFilter tf1 = spy(new TestZuulFilter());
			TestZuulFilter tf2 = spy(new TestZuulFilter());

			when(tf1.shouldFilter(req)).thenReturn(true);
			when(tf2.shouldFilter(req)).thenReturn(false);
		}
	}
}
