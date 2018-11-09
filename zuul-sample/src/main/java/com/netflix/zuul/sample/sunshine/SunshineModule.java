package com.netflix.zuul.sample.sunshine;

import com.google.inject.Scopes;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.zuul.sample.ZuulSampleModule;

/**
 * @author <sunshine> yangsonglin@maoyan.com
 * @date 2018/11/8 下午6:11
 */
public class SunshineModule extends ZuulSampleModule {

	@Override
	protected void configure() {
		bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
		super.configure();
	}
}
