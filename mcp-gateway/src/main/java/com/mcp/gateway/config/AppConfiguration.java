package com.mcp.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration
public class AppConfiguration implements WebMvcConfigurer {

    private final GatewayProperties properties;
    private final ThreadPoolTaskExecutor mvcAsyncExecutor;

    public AppConfiguration(GatewayProperties properties) {
        this.properties = properties;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mcp-mvc-async-");
        executor.setCorePoolSize(32);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        this.mvcAsyncExecutor = executor;
    }

    @Bean(name = "mvcAsyncExecutor")
    AsyncTaskExecutor mvcAsyncExecutor() {
        return mvcAsyncExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor);
        configurer.setDefaultTimeout(properties.getHttpTimeoutMs() + 5_000L);
    }

    @Bean
    RestClient.Builder restClientBuilder(GatewayProperties gatewayProperties) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(gatewayProperties.getHttpTimeoutMs()))
                .executor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "jdk-http-out");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(gatewayProperties.getHttpTimeoutMs()));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean(destroyMethod = "dispose")
    ConnectionProvider toolForwardConnectionProvider() {
        return ConnectionProvider.builder("mcp-tool-forward")
                .maxConnections(500)
                .pendingAcquireMaxCount(1000)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(45))
                .maxLifeTime(Duration.ofMinutes(10))
                .evictInBackground(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    WebClient toolForwardWebClient(
            ConnectionProvider toolForwardConnectionProvider,
            GatewayProperties gatewayProperties) {
        Duration timeout = Duration.ofMillis(gatewayProperties.getHttpTimeoutMs());
        HttpClient httpClient = HttpClient.create(toolForwardConnectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE))
                .responseTimeout(timeout)
                .protocol(HttpProtocol.HTTP11);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
