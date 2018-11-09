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

package com.netflix.zuul.sample;

import com.google.inject.AbstractModule;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.BasicRequestCompleteHandler;
import com.netflix.zuul.FilterFileManager;
import com.netflix.zuul.RequestCompleteHandler;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.context.ZuulSessionContextDecorator;
import com.netflix.zuul.init.ZuulFiltersModule;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.ClientRequestReceiver;
import com.netflix.zuul.origins.BasicNettyOriginManager;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.stats.BasicRequestMetricsPublisher;
import com.netflix.zuul.stats.RequestMetricsPublisher;

/**
 * Zuul Sample Module
 * <p>
 * Author: Arthur Gonigberg
 * Date: November 20, 2017
 */
public class ZuulSampleModule extends AbstractModule {
	@Override
	protected void configure() {
		// 启动器
		bind(BaseServerStartup.class).to(SampleServerStartup.class);
		// 基础NettyOriginManager
		bind(OriginManager.class).to(BasicNettyOriginManager.class);
		// 添加ZuulFilterModule绑定类型
		install(new ZuulFiltersModule());
		// 恶汉单例模式的FilterFileManager
		bind(FilterFileManager.class).asEagerSingleton();
		// 服务器健康状态、发现状态管理
		bind(ServerStatusManager.class);
		// 请求接入时的Session装饰器
		bind(SessionContextDecorator.class).to(ZuulSessionContextDecorator.class);
		// 计数器注册
		bind(Registry.class).to(DefaultRegistry.class);
		// Request请求完成的处理器，也是用于计数
		bind(RequestCompleteHandler.class).to(BasicRequestCompleteHandler.class);
		// 服务发现客户端，实现的是Eureka
		bind(AbstractDiscoveryClientOptionalArgs.class).to(DiscoveryClient.DiscoveryClientOptionalArgs.class);
		// Request请求时间点计算与发布
		bind(RequestMetricsPublisher.class).to(BasicRequestMetricsPublisher.class);
		// Request日志生成，以及打一些Request基础日志
		bind(AccessLogPublisher.class).toInstance(new AccessLogPublisher("ACCESS",
				(channel, httpRequest) -> ClientRequestReceiver.getRequestFromChannel(channel).getContext().getUUID()));
	}
}
