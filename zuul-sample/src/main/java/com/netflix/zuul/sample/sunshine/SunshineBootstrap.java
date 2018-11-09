package com.netflix.zuul.sample.sunshine;

import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.governator.InjectorBuilder;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/8 下午6:06
 */
public class SunshineBootstrap {

	private static final Logger LOGGER = LoggerFactory.getLogger(SunshineBootstrap.class);

	public static void main(String[] args) {
		new SunshineBootstrap().start();
	}

	public void start() {
		long startTime = System.currentTimeMillis();
		Server server = null;
		try {
			ConfigurationManager.loadCascadedPropertiesFromResources("sunshine");
			Injector injector = InjectorBuilder.fromModule(new SunshineModule()).createInjector();

			BaseServerStartup serverStartup = injector.getInstance(BaseServerStartup.class);
			server = serverStartup.server();

			long startupDuration = System.currentTimeMillis() - startTime;
			LOGGER.info("Zuul Sample: finished startup. Duration = " + startupDuration + " ms");

			server.start(true);
		} catch (Exception e) {
		    LOGGER.error("", e);
		}
	}
}
