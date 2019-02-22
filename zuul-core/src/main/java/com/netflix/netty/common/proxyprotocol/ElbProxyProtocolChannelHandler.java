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

import com.netflix.netty.common.SourceAddressChannelHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyTLV;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 将任何HAProxyMessage拷贝到Channel属性中，然后不会在pipeline中继续处理它
 * 如果ELB上的代理协议可用，那么可以结合使用OptionalHAProxyMessageDecoder
 * User: michaels@netflix.com
 * Date: 3/24/16
 * Time: 11:59 AM
 */
public class ElbProxyProtocolChannelHandler extends ChannelInboundHandlerAdapter {
	public static final String NAME = "ElbProxyProtocolChannelHandler";
	public static final AttributeKey<HAProxyMessage> ATTR_HAPROXY_MESSAGE = AttributeKey.newInstance("_haproxy_message");
	public static final AttributeKey<HAProxyProtocolVersion> ATTR_HAPROXY_VERSION = AttributeKey.newInstance("_haproxy_version");

	private static final Logger logger = LoggerFactory.getLogger("ElbProxyProtocolChannelHandler");

	private final boolean withProxyProtocol;

	public ElbProxyProtocolChannelHandler(boolean withProxyProtocol) {
		this.withProxyProtocol = withProxyProtocol;
	}

	/**
	 * Setup the required handlers on pipeline using this method.
	 * 在pipeline上安装需要使用的handlers
	 * @param pipeline
	 */
	public void addProxyProtocol(ChannelPipeline pipeline) {
		pipeline.addLast(NAME, this);

		if (withProxyProtocol) {
			pipeline.addBefore(NAME, OptionalHAProxyMessageDecoder.NAME, new OptionalHAProxyMessageDecoder());
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		// 如果可以使用代理协议，那么逐个设置channel的值
		if (withProxyProtocol) {
			if (msg instanceof HAProxyMessage && msg != null) {
				HAProxyMessage hapm = (HAProxyMessage) msg;
				Channel channel = ctx.channel();
				channel.attr(ATTR_HAPROXY_MESSAGE).set(hapm);
				channel.attr(ATTR_HAPROXY_VERSION).set(hapm.protocolVersion());
				// Get the real host and port that the client connected to ELB with.
				String destinationAddress = hapm.destinationAddress();
				if (destinationAddress != null) {
					channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_ADDRESS).set(destinationAddress);
					channel.attr(SourceAddressChannelHandler.ATTR_LOCAL_PORT).set(hapm.destinationPort());
				}

				// 获取真实的客户端请求IP，并覆盖原有的SourceAddress
				String sourceAddress = hapm.sourceAddress();
				if (sourceAddress != null) {
					channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_ADDRESS).set(sourceAddress);
					channel.attr(SourceAddressChannelHandler.ATTR_SOURCE_PORT).set(hapm.sourcePort());
				}

				// PPV2 passes dynamic fields in TLV format which
				// HAProxyMessage decodes into bytebufs retaining
				// refCounts..call release to avoid bytebuf leaks
				//
				List<HAProxyTLV> haProxyTLVList = hapm.tlvs();
				if (haProxyTLVList != null) {
					logger.debug("Decoded PPV2 TLV list count: {}, List: {}", haProxyTLVList.size(), haProxyTLVList);
					for (int i = 0; i < haProxyTLVList.size(); i++) {
						HAProxyTLV haProxyTLV = haProxyTLVList.get(i);
						haProxyTLV.release();
					}
				}

				// TODO - fire an additional event to notify interested parties that we now know the IP?

				// Remove ourselves (this handler) from the channel now, as no more work to do.
				ctx.pipeline().remove(this);
			}
		}

		super.channelRead(ctx, msg);
	}
}
