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

package com.netflix.zuul.context;

import com.netflix.netty.common.metrics.HttpBodySizeRecordingChannelHandler;
import com.netflix.util.UUIDFactory;
import com.netflix.util.concurrent.ConcurrentUUIDFactory;
import com.netflix.zuul.niws.RequestAttempts;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.passport.CurrentPassport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Base Session Context Decorator
 * <p>
 * Author: Arthur Gonigberg
 * Date: November 21, 2017
 */
@Singleton
public class ZuulSessionContextDecorator implements SessionContextDecorator {

	private static final UUIDFactory UUID_FACTORY = new ConcurrentUUIDFactory();

	private final OriginManager originManager;

	@Inject
	public ZuulSessionContextDecorator(OriginManager originManager) {
		this.originManager = originManager;
	}

	@Override
	public SessionContext decorate(SessionContext ctx) {
		// TODO split out commons parts from BaseSessionContextDecorator

		ChannelHandlerContext nettyCtx = (ChannelHandlerContext) ctx.get(CommonContextKeys.NETTY_SERVER_CHANNEL_HANDLER_CONTEXT);
		if (nettyCtx == null) {
			return null;
		}

		Channel channel = nettyCtx.channel();

		// 添加已经注入的OriginManager
		ctx.put(CommonContextKeys.ORIGIN_MANAGER, originManager);

		// TODO
/*        // The throttle result info.
        ThrottleResult throttleResult = channel.attr(HttpRequestThrottleChannelHandler.ATTR_THROTTLE_RESULT).get();
        ctx.set(CommonContextKeys.THROTTLE_RESULT, throttleResult);*/

		// 声明一个用于存储Request尝试信息的容器，默认是一个ArrayList
		ctx.put(CommonContextKeys.REQUEST_ATTEMPTS, new RequestAttempts());

		// 从当前Channel中获取记录RequestBody大小和Response大小的记录
		ctx.set(CommonContextKeys.REQ_BODY_SIZE_PROVIDER, HttpBodySizeRecordingChannelHandler.getCurrentRequestBodySize(channel));
		ctx.set(CommonContextKeys.RESP_BODY_SIZE_PROVIDER, HttpBodySizeRecordingChannelHandler.getCurrentResponseBodySize(channel));

		// 获取请求通行证，它是基于纳秒时间记录的关于请求瞬时状态
		CurrentPassport passport = CurrentPassport.fromChannel(channel);
		ctx.set(CommonContextKeys.PASSPORT, passport);
		// 生成对应的UUID
		ctx.setUUID(UUID_FACTORY.generateRandomUuid().toString());

		return ctx;
	}
}
