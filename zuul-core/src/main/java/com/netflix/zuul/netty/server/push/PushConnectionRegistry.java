/**
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.netty.server.push;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 维持Websocket或者SSE渠道的客户端验证映射关系
 * 实现方式concurrenthashmap
 * Created by saroskar on 9/26/16.
 */
@Singleton
public class PushConnectionRegistry {

	private final ConcurrentMap<String, PushConnection> clientPushConnectionMap;

	@Inject
	private PushConnectionRegistry() {
		clientPushConnectionMap = new ConcurrentHashMap<>(1024 * 8);
	}

	public PushConnection get(final String clientId) {
		return clientPushConnectionMap.get(clientId);
	}

	public void put(final String clientId, final PushConnection pushConnection) {
		clientPushConnectionMap.put(clientId, pushConnection);
	}

	public PushConnection remove(final String clientId) {
		final PushConnection pc = clientPushConnectionMap.remove(clientId);
		return pc;
	}

	public int size() {
		return clientPushConnectionMap.size();
	}

}
