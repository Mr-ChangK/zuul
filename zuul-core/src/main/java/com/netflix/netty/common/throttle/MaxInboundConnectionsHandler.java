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

package com.netflix.netty.common.throttle;

import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.passport.PassportState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最大接入连接Handler
 * 如果当前总数超过了配置的阈值，关闭任何接入的连接
 * 如果一个连接被限流了，channel将会被关闭，然后一个CONNECTION_THROTTLED_EVENT时间将开启，并且不会通知其他对其感兴趣的handler
 * 是总的连接数
 */
@ChannelHandler.Sharable
public class MaxInboundConnectionsHandler extends ChannelInboundHandlerAdapter {
	public static final String CONNECTION_THROTTLED_EVENT = "connection_throttled";

	private static final Logger LOG = LoggerFactory.getLogger(MaxInboundConnectionsHandler.class);
	private static final AttributeKey<Boolean> ATTR_CH_THROTTLED = AttributeKey.newInstance("_channel_throttled");

	/**
	 * 计数器
	 */
	private final static AtomicInteger connections = new AtomicInteger(0);
	/**
	 * 最大连接数
	 */
	private final int maxConnections;

	public MaxInboundConnectionsHandler(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (maxConnections > 0) {
			int currentCount = connections.getAndIncrement();

			if (currentCount + 1 > maxConnections) {
				LOG.warn("Throttling incoming connection as above configured max connections threshold of " + maxConnections);
				Channel channel = ctx.channel();
				// 设置channel限流属性
				channel.attr(ATTR_CH_THROTTLED).set(Boolean.TRUE);

				CurrentPassport.fromChannel(channel).add(PassportState.SERVER_CH_THROTTLING);
				channel.close();
				// 发送一个用户事件触发的事件
				ctx.pipeline().fireUserEventTriggered(CONNECTION_THROTTLED_EVENT);
			}
		}

		super.channelActive(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (ctx.channel().attr(ATTR_CH_THROTTLED).get() != null) {
			// Discard this msg as channel is in process of being closed.
		} else {
			super.channelRead(ctx, msg);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (maxConnections > 0) {
			connections.decrementAndGet();
		}

		super.channelInactive(ctx);
	}
}
