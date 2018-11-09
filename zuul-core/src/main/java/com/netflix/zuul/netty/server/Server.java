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

package com.netflix.zuul.netty.server;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.netty.common.CategorizedThreadFactory;
import com.netflix.netty.common.LeastConnsEventLoopChooserFactory;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.status.ServerStatusManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * NOTE: Shout-out to <a href="https://github.com/adamfisk/LittleProxy">LittleProxy</a> which was great as a reference.
 * <p>
 * User: michaels
 * Date: 11/8/14
 * Time: 8:39 PM
 */
public class Server {
	public static final DynamicBooleanProperty USE_EPOLL = new DynamicBooleanProperty("zuul.server.netty.socket.epoll", false);

	private static final Logger LOG = LoggerFactory.getLogger(Server.class);

	private static final DynamicBooleanProperty USE_LEASTCONNS_FOR_EVENTLOOPS = new DynamicBooleanProperty("zuul.server.eventloops.use_leastconns", false);

	/**
	 * EventLoopGroupMetrics
	 */
	private final EventLoopGroupMetrics eventLoopGroupMetrics;
	/**
	 * jvm关闭的钩子线程
	 */
	private final Thread jvmShutdownHook;
	/**
	 * 服务组
	 */
	private ServerGroup serverGroup;
	/**
	 * 客户端关闭
	 */
	private final ClientConnectionsShutdown clientConnectionsShutdown;
	/**
	 * Server的状态管理
	 */
	private final ServerStatusManager serverStatusManager;
	/**
	 * 端口-ChannelInitializer容器
	 */
	private final Map<Integer, ChannelInitializer> portsToChannelInitializers;
	/**
	 * EventLoop配置
	 */
	private final EventLoopConfig eventLoopConfig;

	public Server(Map<Integer, ChannelInitializer> portsToChannelInitializers, ServerStatusManager serverStatusManager, ClientConnectionsShutdown clientConnectionsShutdown, EventLoopGroupMetrics eventLoopGroupMetrics) {
		this(portsToChannelInitializers, serverStatusManager, clientConnectionsShutdown, eventLoopGroupMetrics, new DefaultEventLoopConfig());
	}

	public Server(Map<Integer, ChannelInitializer> portsToChannelInitializers, ServerStatusManager serverStatusManager, ClientConnectionsShutdown clientConnectionsShutdown, EventLoopGroupMetrics eventLoopGroupMetrics, EventLoopConfig eventLoopConfig) {
		this.portsToChannelInitializers = portsToChannelInitializers;
		this.serverStatusManager = serverStatusManager;
		this.clientConnectionsShutdown = clientConnectionsShutdown;
		this.eventLoopConfig = eventLoopConfig;
		this.eventLoopGroupMetrics = eventLoopGroupMetrics;
		this.jvmShutdownHook = new Thread(() -> stop(), "Zuul-JVM-shutdown-hook");
	}

	public void stop() {
		LOG.warn("Shutting down Zuul.");
		// 停止服务，加锁
		serverGroup.stop();

		try {
			// 去除钩子函数
			Runtime.getRuntime().removeShutdownHook(jvmShutdownHook);
		} catch (IllegalStateException e) {
			// ignore -- IllegalStateException means the VM is already shutting down
		}

		LOG.warn("Completed zuul shutdown.");
	}

	public void start(boolean sync) {
		// 设置服务组的接收线程数量和工作线程数量，以及计数器
		serverGroup = new ServerGroup("Salamander", eventLoopConfig.acceptorCount(), eventLoopConfig.eventLoopCount(), eventLoopGroupMetrics);
		// 创建模型线程池
		serverGroup.initializeTransport();
		try {
			List<ChannelFuture> allBindFutures = new ArrayList<>();

			for (Map.Entry<Integer, ChannelInitializer> entry : portsToChannelInitializers.entrySet()) {
				// 根据端口和ChannelInitializer，添加对应的ChannelFuture任务
				allBindFutures.add(setupServerBootstrap(entry.getKey(), entry.getValue()));
			}

			for (ChannelFuture f : allBindFutures) {
				// 等待服务关闭
				ChannelFuture cf = f.channel().closeFuture();
				if (sync) {
					cf.sync();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 单元测试中使用的
	 */
	public void waitForEachEventLoop() throws InterruptedException, ExecutionException {
		for (EventExecutor exec : serverGroup.clientToProxyWorkerPool) {
			exec.submit(() -> {
				// Do nothing.
			}).get();
		}
	}

	private ChannelFuture setupServerBootstrap(int port, ChannelInitializer channelInitializer)
			throws InterruptedException {
		// 使用Netty的ServerBootstrap构建主线程池和工作线程池
		ServerBootstrap serverBootstrap = new ServerBootstrap().group(
				serverGroup.clientToProxyBossPool,
				serverGroup.clientToProxyWorkerPool);

		// 处理socket参数
		Map<ChannelOption, Object> channelOptions = new HashMap<>();
		channelOptions.put(ChannelOption.SO_BACKLOG, 128);
		//channelOptions.put(ChannelOption.SO_TIMEOUT, SERVER_SOCKET_TIMEOUT.get());
		channelOptions.put(ChannelOption.SO_LINGER, -1);
		channelOptions.put(ChannelOption.TCP_NODELAY, true);
		channelOptions.put(ChannelOption.SO_KEEPALIVE, true);

		// 判断是使用epoll还是NIO
		if (USE_EPOLL.get()) {
			LOG.warn("Proxy listening with TCP transport using EPOLL");
			serverBootstrap = serverBootstrap.channel(EpollServerSocketChannel.class);
			channelOptions.put(EpollChannelOption.TCP_DEFER_ACCEPT, Integer.valueOf(-1));
		} else {
			LOG.warn("Proxy listening with TCP transport using NIO");
			serverBootstrap = serverBootstrap.channel(NioServerSocketChannel.class);
		}

		// 向serverBootstrap中注入socket参数
		for (Map.Entry<ChannelOption, Object> optionEntry : channelOptions.entrySet()) {
			serverBootstrap = serverBootstrap.option(optionEntry.getKey(), optionEntry.getValue());
		}

		//
		serverBootstrap.childHandler(channelInitializer);
		// 校验Netty的ChannelHandler和工作线程是否存在
		serverBootstrap.validate();

		LOG.info("Binding to port: " + port);

		// 绑定端口前先更改Server状态
		serverStatusManager.localStatus(InstanceInfo.InstanceStatus.UP);

		// 阻塞直到绑定端口成功
		return serverBootstrap.bind(port).sync();
	}

	/**
	 * Override for metrics or informational purposes
	 *
	 * @param clientToProxyBossPool   - acceptor pool
	 * @param clientToProxyWorkerPool - worker pool
	 */
	public void postEventLoopCreationHook(EventLoopGroup clientToProxyBossPool, EventLoopGroup clientToProxyWorkerPool) {

	}


	private class ServerGroup {
		/**
		 * 用户标记线程名称的服务组名称
		 */
		private final String name;
		/**
		 * 接收线程数量
		 */
		private final int acceptorThreads;
		/**
		 * 工作线程数量
		 */
		private final int workerThreads;
		/**
		 * EventLoopGroup的计数器
		 */
		private final EventLoopGroupMetrics eventLoopGroupMetrics;
		/**
		 * 客户端和管理线程池
		 */
		private EventLoopGroup clientToProxyBossPool;
		/**
		 * 客户端和代理工作线程池
		 */
		private EventLoopGroup clientToProxyWorkerPool;
		/**
		 * 是否停止
		 */
		private volatile boolean stopped = false;

		private ServerGroup(String name, int acceptorThreads, int workerThreads, EventLoopGroupMetrics eventLoopGroupMetrics) {
			this.name = name;
			this.acceptorThreads = acceptorThreads;
			this.workerThreads = workerThreads;
			this.eventLoopGroupMetrics = eventLoopGroupMetrics;

			Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(final Thread t, final Throwable e) {
					LOG.error("Uncaught throwable", e);
				}
			});

			Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(), "Zuul-ServerGroup-JVM-shutdown-hook"));
		}

		private void initializeTransport() {
			// TODO - try our own impl of ChooserFactory that load-balances across the eventloops using leastconns algo?
			// EventExecutor的选择器，用于轮询下一个EventExecutor
			EventExecutorChooserFactory chooserFactory;
			if (USE_LEASTCONNS_FOR_EVENTLOOPS.get()) {
				chooserFactory = new LeastConnsEventLoopChooserFactory(eventLoopGroupMetrics);
			} else {
				chooserFactory = DefaultEventExecutorChooserFactory.INSTANCE;
			}

			ThreadFactory workerThreadFactory = new CategorizedThreadFactory(name + "-ClientToZuulWorker");
			Executor workerExecutor = new ThreadPerTaskExecutor(workerThreadFactory);

			// 如果使用epoll模式
			if (USE_EPOLL.get()) {
				clientToProxyBossPool = new EpollEventLoopGroup(
						acceptorThreads,
						new CategorizedThreadFactory(name + "-ClientToZuulAcceptor"));
				// 客户端代理工作线程池使用的是epoll模型线程池
				clientToProxyWorkerPool = new EpollEventLoopGroup(
						workerThreads,
						workerExecutor,
						chooserFactory,
						DefaultSelectStrategyFactory.INSTANCE
				);
			} else {
				clientToProxyBossPool = new NioEventLoopGroup(
						acceptorThreads,
						new CategorizedThreadFactory(name + "-ClientToZuulAcceptor"));
				// 否则，会使用NIO模式的线程池
				clientToProxyWorkerPool = new NioEventLoopGroup(
						workerThreads,
						workerExecutor,
						chooserFactory,
						SelectorProvider.provider(),
						DefaultSelectStrategyFactory.INSTANCE
				);
				// 设置执行IO操作的比值，默认是50
				((NioEventLoopGroup) clientToProxyWorkerPool).setIoRatio(90);
			}

			// 创建完客户端的管理线程池和工作线程池的的后置处理
			postEventLoopCreationHook(clientToProxyBossPool, clientToProxyWorkerPool);
		}

		synchronized private void stop() {
			LOG.warn("Shutting down");
			if (stopped) {
				LOG.warn("Already stopped");
				return;
			}

			// TODO - is this _only_ changing the local status? And therefore should we also implement a HealthCheckHandler
			// 设置当前Server状态为DOWN
			serverStatusManager.localStatus(InstanceInfo.InstanceStatus.DOWN);

			// 优雅的停止客户端连接
			// 核心思想方法是关闭所有的Channel，将Channel的close()事件放入到ChannelFuture中，然后等待所有ChannelFuture完成
			clientConnectionsShutdown.gracefullyShutdownClientChannels();

			LOG.warn("Shutting down event loops");
			List<EventLoopGroup> allEventLoopGroups = new ArrayList<>();
			allEventLoopGroups.add(clientToProxyBossPool);
			allEventLoopGroups.add(clientToProxyWorkerPool);
			for (EventLoopGroup group : allEventLoopGroups) {
				// 优雅的停止EventLoopGroup
				group.shutdownGracefully();
			}

			for (EventLoopGroup group : allEventLoopGroups) {
				try {
					// 等待20秒确认EventLoopGroup停止
					group.awaitTermination(20, TimeUnit.SECONDS);
				} catch (InterruptedException ie) {
					LOG.warn("Interrupted while shutting down event loop");
				}
			}

			stopped = true;
			LOG.warn("Done shutting down");
		}
	}
}