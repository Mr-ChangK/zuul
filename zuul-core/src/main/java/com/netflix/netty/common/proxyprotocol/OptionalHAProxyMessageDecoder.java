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

package com.netflix.netty.common.proxyprotocol;

import com.netflix.config.CachedDynamicBooleanProperty;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可选择的HAProxyMessageDecoder
 * 选择一个来自ELB的新连接是否是以ProxyProtocol为前缀的，如果是，可以在此handler之后，向pipeline中加入一个HAProxyMessageDecoder
 * User: michaels@netflix.com
 * Date: 3/24/16
 * Time: 3:13 PM
 */
public class OptionalHAProxyMessageDecoder extends ChannelInboundHandlerAdapter {
	public static final String NAME = "OptionalHAProxyMessageDecoder";
	private static final Logger logger = LoggerFactory.getLogger("OptionalHAProxyMessageDecoder");
	private static final CachedDynamicBooleanProperty dumpHAProxyByteBuf = new CachedDynamicBooleanProperty("zuul.haproxy.dump.bytebuf", false);

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			try {
				// 从msg中检测是否携带代理协议
				ProtocolDetectionResult<HAProxyProtocolVersion> result = HAProxyMessageDecoder.detectProtocol((ByteBuf) msg);

				// TODO - is it possible that this message could be split over multiple ByteBufS, and therefore this would fail?
				// 如果检测到，在OptionalHAProxyMessageDecoder后加上一个HAProxyMessageDecoder类型的Handler
				// 然后删除OptionalHAProxyMessageDecoder类型的Handler
				if (result.state() == ProtocolDetectionState.DETECTED) {
					ctx.pipeline().addAfter(NAME, null, new HAProxyMessageDecoder());

					ctx.pipeline().remove(this);
				}
			} catch (Exception e) {
				if (msg != null) {
					logger.error("Exception in OptionalHAProxyMessageDecoder {}" + e.getClass().getCanonicalName());
					if (dumpHAProxyByteBuf.get()) {
						logger.error("Exception Stack: {}" + e.getStackTrace());
						logger.error("Bytebuf is:  {} " + ((ByteBuf) msg).toString(CharsetUtil.US_ASCII));
					}
					((ByteBuf) msg).release();
				}
			}
		}
		super.channelRead(ctx, msg);
	}
}
