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

package com.netflix.zuul.origins;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.netty.connectionpool.PooledConnection;
import com.netflix.zuul.niws.RequestAttempt;
import com.netflix.zuul.passport.CurrentPassport;
import com.netflix.zuul.stats.Timing;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty Origin interface for integrating cleanly with the ProxyEndpoint state management class.
 * <p>
 * Author: Arthur Gonigberg
 * Date: November 29, 2017
 */
public interface NettyOrigin extends InstrumentedOrigin {

	/**
	 * 连接Origin
	 *
	 * @param zuulReq
	 * @param eventLoop
	 * @param attemptNumber
	 * @param passport
	 * @param chosenServer
	 * @param chosenHostAddr
	 * @return
	 */
	Promise<PooledConnection> connectToOrigin(final HttpRequestMessage zuulReq, EventLoop eventLoop,
											  int attemptNumber, CurrentPassport passport,
											  AtomicReference<Server> chosenServer,
											  AtomicReference<String> chosenHostAddr);

	/**
	 * 获取代理时间节点
	 *
	 * @param zuulReq
	 * @return
	 */
	Timing getProxyTiming(HttpRequestMessage zuulReq);

	/**
	 * 获取Request的最大请求次数
	 *
	 * @param context
	 * @return
	 */
	int getMaxRetriesForRequest(SessionContext context);

	/**
	 * 请求开始执行
	 *
	 * @param zuulReq
	 */
	void onRequestExecutionStart(final HttpRequestMessage zuulReq);

	/**
	 * 带有请求源的请求开始执行
	 *
	 * @param zuulReq
	 * @param originServer
	 * @param attemptNum
	 */
	void onRequestStartWithServer(final HttpRequestMessage zuulReq, final Server originServer, int attemptNum);

	/**
	 * 带有请求源的请求执行异常
	 *
	 * @param zuulReq
	 * @param originServer
	 * @param attemptNum
	 * @param t
	 */
	void onRequestExceptionWithServer(final HttpRequestMessage zuulReq, final Server originServer,
									  final int attemptNum, Throwable t);

	/**
	 * 请求执行成功
	 *
	 * @param zuulReq
	 * @param zuulResp
	 * @param originServer
	 * @param attemptNum
	 */
	void onRequestExecutionSuccess(final HttpRequestMessage zuulReq, final HttpResponseMessage zuulResp,
								   final Server originServer, final int attemptNum);

	/**
	 * 请求执行失败
	 *
	 * @param zuulReq
	 * @param originServer
	 * @param attemptNum
	 * @param t
	 */
	void onRequestExecutionFailed(final HttpRequestMessage zuulReq, final Server originServer,
								  final int attemptNum, Throwable t);

	/**
	 * 记录最后一次错误
	 *
	 * @param requestMsg
	 * @param throwable
	 */
	void recordFinalError(final HttpRequestMessage requestMsg, final Throwable throwable);

	/**
	 * 记录最后一次响应
	 *
	 * @param resp
	 */
	void recordFinalResponse(final HttpResponseMessage resp);

	/**
	 * 新请求尝试
	 *
	 * @param server
	 * @param zuulCtx
	 * @param attemptNum
	 * @return
	 */
	RequestAttempt newRequestAttempt(final Server server, final SessionContext zuulCtx, int attemptNum);

	/**
	 * 从服务端获取IP地址
	 *
	 * @param server
	 * @return
	 */
	String getIpAddrFromServer(Server server);

	/**
	 * 获取客户端的配置
	 *
	 * @return
	 */
	IClientConfig getClientConfig();

	/**
	 * 获取观察者注册
	 *
	 * @return
	 */
	Registry getSpectatorRegistry();

	/**
	 * 获取执行的上下文环境
	 *
	 * @param zuulRequest
	 * @return
	 */
	ExecutionContext<?> getExecutionContext(HttpRequestMessage zuulRequest);
}
