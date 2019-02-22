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

import com.google.common.collect.Sets;
import com.netflix.netty.common.ssl.SslHandshakeInfo;
import com.netflix.zuul.netty.server.ssl.SslHandshakeInfoHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.ClientAuth;
import io.netty.util.AsciiString;

import java.util.Collection;

/**
 * 如果连接不可信，剥离进入Http请求的任何X-Forward-*的headers
 */
@ChannelHandler.Sharable
public class StripUntrustedProxyHeadersHandler extends ChannelInboundHandlerAdapter {
	public enum AllowWhen {
		ALWAYS,
		MUTUAL_SSL_AUTH,
		NEVER
	}

	// 需要剥离的请求头
	private static final Collection<AsciiString> HEADERS_TO_STRIP = Sets.newHashSet(
			new AsciiString("x-forwarded-for"),
			new AsciiString("x-forwarded-port"),
			new AsciiString("x-forwarded-proto"),
			new AsciiString("x-forwarded-proto-version"),
			new AsciiString("x-real-ip")
	);

	private final AllowWhen allowWhen;

	public StripUntrustedProxyHeadersHandler(AllowWhen allowWhen) {
		this.allowWhen = allowWhen;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof HttpRequest) {
			HttpRequest req = (HttpRequest) msg;

			switch (allowWhen) {
				case NEVER:
					// 永不允许，剥离所有
					stripXFFHeaders(req);
					break;
				case MUTUAL_SSL_AUTH:
					// 共用SSL权限，如果不是强制使用共用SSL，剥离所有
					if (!connectionIsUsingMutualSSLWithAuthEnforced(ctx.channel())) {
						stripXFFHeaders(req);
					}
					break;
				case ALWAYS:
					// 总是允许，什么都不做
					break;
				default:
					// 默认剥离所有
					stripXFFHeaders(req);
			}
		}

		super.channelRead(ctx, msg);
	}

	private boolean connectionIsUsingMutualSSLWithAuthEnforced(Channel ch) {
		boolean is = false;
		SslHandshakeInfo sslHandshakeInfo = ch.attr(SslHandshakeInfoHandler.ATTR_SSL_INFO).get();
		if (sslHandshakeInfo != null) {
			if (sslHandshakeInfo.getClientAuthRequirement() == ClientAuth.REQUIRE) {
				is = true;
			}
		}
		return is;
	}

	private void stripXFFHeaders(HttpRequest req) {
		HttpHeaders headers = req.headers();
		for (AsciiString headerName : HEADERS_TO_STRIP) {
			headers.remove(headerName);
		}
	}
}
