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

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.Header;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.ZuulMessageImpl;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.ServerCookieEncoder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * HttpResponseMessage的实现
 * User: michaels
 * Date: 2/24/15
 * Time: 10:54 AM
 */
public class HttpResponseMessageImpl implements HttpResponseMessage {
	private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
			"zuul.HttpResponseMessage.body.max.size", 25 * 1000 * 1024);
	private static final Logger LOG = LoggerFactory.getLogger(HttpResponseMessageImpl.class);

	private ZuulMessage message;
	private HttpRequestMessage outboundRequest;
	private int status;
	private HttpResponseInfo inboundResponse = null;

	public HttpResponseMessageImpl(SessionContext context, HttpRequestMessage request, int status) {
		this(context, new Headers(), request, status);
	}

	public HttpResponseMessageImpl(SessionContext context, Headers headers, HttpRequestMessage request, int status) {
		this.message = new ZuulMessageImpl(context, headers);
		this.outboundRequest = request;
		if (this.outboundRequest.getInboundRequest() == null) {
			LOG.warn("HttpResponseMessage created with a request that does not have a stored inboundRequest! " +
							"Probably a bug in the filter that is creating this response.",
					new RuntimeException("Invalid HttpRequestMessage"));
		}
		this.status = status;
	}

	public static HttpResponseMessage defaultErrorResponse(HttpRequestMessage request) {
		final HttpResponseMessage resp = new HttpResponseMessageImpl(request.getContext(), request, 500);
		resp.finishBufferedBodyIfIncomplete();
		return resp;
	}

	@Override
	public Headers getHeaders() {
		return message.getHeaders();
	}

	@Override
	public SessionContext getContext() {
		return message.getContext();
	}

	@Override
	public void setHeaders(Headers newHeaders) {
		message.setHeaders(newHeaders);
	}

	@Override
	public void setHasBody(boolean hasBody) {
		message.setHasBody(hasBody);
	}

	@Override
	public boolean hasBody() {
		return message.hasBody();
	}

	@Override
	public void bufferBodyContents(HttpContent chunk) {
		message.bufferBodyContents(chunk);
	}

	@Override
	public void setBodyAsText(String bodyText) {
		message.setBodyAsText(bodyText);
	}

	@Override
	public void setBody(byte[] body) {
		message.setBody(body);
	}

	@Override
	public String getBodyAsText() {
		return message.getBodyAsText();
	}

	@Override
	public byte[] getBody() {
		return message.getBody();
	}

	@Override
	public int getBodyLength() {
		return message.getBodyLength();
	}

	@Override
	public boolean hasCompleteBody() {
		return message.hasCompleteBody();
	}

	@Override
	public boolean finishBufferedBodyIfIncomplete() {
		return message.finishBufferedBodyIfIncomplete();
	}

	@Override
	public Iterable<HttpContent> getBodyContents() {
		return message.getBodyContents();
	}

	@Override
	public void runBufferedBodyContentThroughFilter(ZuulFilter filter) {
		message.runBufferedBodyContentThroughFilter(filter);
	}

	@Override
	public void disposeBufferedBody() {
		message.disposeBufferedBody();
	}

	@Override
	public HttpRequestInfo getInboundRequest() {
		return outboundRequest.getInboundRequest();
	}

	@Override
	public HttpRequestMessage getOutboundRequest() {
		return outboundRequest;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public void setStatus(int status) {
		this.status = status;
	}

	@Override
	public int getMaxBodySize() {
		return MAX_BODY_SIZE_PROP.get();
	}

	@Override
	public Cookies parseSetCookieHeader(String setCookieValue) {
		Cookies cookies = new Cookies();
		for (Cookie cookie : CookieDecoder.decode(setCookieValue)) {
			cookies.add(cookie);
		}
		return cookies;
	}

	@Override
	public boolean hasSetCookieWithName(String cookieName) {
		boolean has = false;
		for (String setCookieValue : getHeaders().get(HttpHeaderNames.SET_COOKIE)) {
			for (Cookie cookie : CookieDecoder.decode(setCookieValue)) {
				if (cookie.getName().equalsIgnoreCase(cookieName)) {
					has = true;
					break;
				}
			}
		}
		return has;
	}

	@Override
	public boolean removeExistingSetCookie(String cookieName) {
		// 遍历headers，如果发现headers中有以"${cookieName}="开头的值，那么不将其加入到已过滤的的headers中
		String cookieNamePrefix = cookieName + "=";
		boolean dirty = false;
		Headers filtered = new Headers();
		for (Header hdr : getHeaders().entries()) {
			if (HttpHeaderNames.SET_COOKIE.equals(hdr.getName())) {
				String value = hdr.getValue();
				// 除需要被移除的cookie之外，拷贝其他所有的headers
				if (value.startsWith(cookieNamePrefix)) {
					dirty = true;
				} else {
					filtered.add(hdr.getName(), hdr.getValue());
				}
			} else {
				// 如果没发现需要移除的，拷贝所有
				filtered.add(hdr.getName(), hdr.getValue());
			}
		}

		if (dirty) {
			setHeaders(filtered);
		}
		return dirty;
	}

	@Override
	public void addSetCookie(Cookie cookie) {
		// 添加cookie
		getHeaders().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie));
	}

	@Override
	public void setSetCookie(Cookie cookie) {
		// 复制cookie
		getHeaders().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.encode(cookie));
	}

	@Override
	public ZuulMessage clone() {
		// TODO - 并不确定在这里是否需要克隆一份outbound request
		HttpResponseMessageImpl clone = new HttpResponseMessageImpl(getContext().clone(),
				getHeaders().clone(),
				getOutboundRequest(), getStatus());
		if (getInboundResponse() != null) {
			clone.inboundResponse = (HttpResponseInfo) getInboundResponse().clone();
		}
		return clone;
	}

	protected HttpResponseInfo copyResponseInfo() {
		// Unlike clone(), we create immutable copies of the Headers here.
		// 不同于clone()，我们会创建一个不可变的Headers拷贝
		HttpResponseMessageImpl response = new HttpResponseMessageImpl(getContext(),
				getHeaders().immutableCopy(),
				getOutboundRequest(), getStatus());
		response.setHasBody(hasBody());
		return response;
	}

	@Override
	public String toString() {
		return "HttpResponseMessageImpl{" +
				"message=" + message +
				", outboundRequest=" + outboundRequest +
				", status=" + status +
				", inboundResponse=" + inboundResponse +
				'}';
	}

	@Override
	public void storeInboundResponse() {
		inboundResponse = copyResponseInfo();
	}

	@Override
	public HttpResponseInfo getInboundResponse() {
		return inboundResponse;
	}

	@Override
	public String getInfoForLogging() {
		HttpRequestInfo req = getInboundRequest() == null ? getOutboundRequest() : getInboundRequest();
		StringBuilder sb = new StringBuilder()
				.append(req.getInfoForLogging())
				.append(",proxy-status=").append(getStatus());
		return sb.toString();
	}


	@RunWith(MockitoJUnitRunner.class)
	public static class UnitTest {
		@Mock
		private HttpRequestMessage request;

		private HttpResponseMessageImpl response;

		@Before
		public void setup() {
			response = new HttpResponseMessageImpl(new SessionContext(), new Headers(), request, 200);
		}

		@Test
		public void testHasSetCookieWithName() {
			response.getHeaders().add("Set-Cookie", "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
			response.getHeaders().add("Set-Cookie", "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

			assertTrue(response.hasSetCookieWithName("c1"));
			assertTrue(response.hasSetCookieWithName("c2"));
			assertFalse(response.hasSetCookieWithName("XX"));
		}

		@Test
		public void testRemoveExistingSetCookie() {
			response.getHeaders().add("Set-Cookie", "c1=1234; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");
			response.getHeaders().add("Set-Cookie", "c2=4567; Max-Age=-1; Expires=Tue, 01 Sep 2015 22:49:57 GMT; Path=/; Domain=.netflix.com");

			response.removeExistingSetCookie("c1");

			assertEquals(1, response.getHeaders().size());
			assertFalse(response.hasSetCookieWithName("c1"));
			assertTrue(response.hasSetCookieWithName("c2"));
		}
	}
}
