package com.alibaba.arthas.channel.server.autoconfigure;

import com.alibaba.arthas.channel.server.conf.ScheduledExecutorConfig;
import com.alibaba.arthas.channel.server.grpc.ArthasServiceGrpcImpl;
import com.alibaba.arthas.channel.server.grpc.ChannelServer;
import com.alibaba.arthas.channel.server.message.MessageExchangeService;
import com.alibaba.arthas.channel.server.message.impl.MessageExchangeServiceImpl;
import com.alibaba.arthas.channel.server.redis.RedisAgentManageServiceImpl;
import com.alibaba.arthas.channel.server.redis.RedisMessageExchangeServiceImpl;
import com.alibaba.arthas.channel.server.service.AgentBizSerivce;
import com.alibaba.arthas.channel.server.service.AgentCleaner;
import com.alibaba.arthas.channel.server.service.AgentManageService;
import com.alibaba.arthas.channel.server.service.ApiActionDelegateService;
import com.alibaba.arthas.channel.server.service.impl.AgentBizServiceImpl;
import com.alibaba.arthas.channel.server.service.impl.AgentManageServiceImpl;
import com.alibaba.arthas.channel.server.service.impl.ApiActionDelegateServiceImpl;
import com.alibaba.arthas.channel.server.ws.WebSocketServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;


@Configuration
@ConditionalOnClass(ChannelServer.class)
@EnableConfigurationProperties(ChannelServerProperties.class)
public class ChannelServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScheduledExecutorConfig scheduledExecutorConfig() {
        // 设置较大的corePoolSize，避免并发运行的task阻塞调度队列 (https://developer.aliyun.com/article/5897 "1.2 线程数量控制")
        int corePoolSize = 10;
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(corePoolSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread t = new Thread(r, "Arthas-channel-server-execute");
                t.setDaemon(true);
                return t;
            }
        });

        ScheduledExecutorConfig scheduledExecutorConfig = new ScheduledExecutorConfig();
        scheduledExecutorConfig.setExecutorService(executorService);
        return scheduledExecutorConfig;
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentBizSerivce agentBizSerivce() {
        return new AgentBizServiceImpl();
    }

    @Bean
    public ApiActionDelegateService apiActionDelegateService() {
        return new ApiActionDelegateServiceImpl();
    }

    @Bean
    public ArthasServiceGrpcImpl arthasServiceGrpc() {
        return new ArthasServiceGrpcImpl();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public AgentCleaner agentCleaner(ScheduledExecutorConfig scheduledExecutorConfig, ChannelServerProperties serverProperties) {
        ChannelServerProperties.Agent agentConfig = serverProperties.getAgent();
        AgentCleaner agentCleaner = new AgentCleaner(scheduledExecutorConfig);
        agentCleaner.setCleanIntervalMills(agentConfig.getCleanIntervalMills());
        agentCleaner.setRemovingTimeout(agentConfig.getRemovingTimeoutMills());
        agentCleaner.setDownTimeout(agentConfig.getDownTimeoutMills());
        agentCleaner.setOutOfServiceTimeout(agentConfig.getOutOfServiceTimeoutMills());
        return agentCleaner;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = ChannelServerProperties.PREFIX+".backend.enabled", havingValue = "true", matchIfMissing = false)
    public ChannelServer channelServer(ChannelServerProperties serverProperties) {
        ChannelServer channelServer = new ChannelServer();
        channelServer.setPort(serverProperties.getBackend().getPort());
        return channelServer;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = ChannelServerProperties.PREFIX+".websocket.enabled", havingValue = "true", matchIfMissing = false)
    public WebSocketServer webSocketServer(ChannelServerProperties serverProperties) {
        WebSocketServer server = new WebSocketServer();
        ChannelServerProperties.Server websocket = serverProperties.getWebsocket();
        server.setHost(websocket.getHost());
        server.setPort(websocket.getPort());
        server.setSsl(websocket.isSsl());
        return server;
    }

    @Profile("memory")
    @Configuration
    static class StandaloneConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AgentManageService agentManageService() {
            return new AgentManageServiceImpl();
        }

        @Bean
        @ConditionalOnMissingBean
        public MessageExchangeService messageExchangeService() {
            return new MessageExchangeServiceImpl();
        }
    }


    @Profile("redis")
    @Configuration
    static class RedisConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public AgentManageService agentManageService() {
            return new RedisAgentManageServiceImpl();
        }

        @Bean
        @ConditionalOnMissingBean
        public MessageExchangeService messageExchangeService() {
            return new RedisMessageExchangeServiceImpl();
        }

        @Bean
        @ConditionalOnMissingBean
        public ReactiveRedisTemplate<String, byte[]> reactiveRedisTemplate(ReactiveRedisConnectionFactory redisConnectionFactory) {
            ReactiveRedisTemplate<String, byte[]> template = new ReactiveRedisTemplate (redisConnectionFactory, RedisSerializationContext
                    .<String, byte[]>newSerializationContext()
                    .key(RedisSerializer.string())
                    .value(RedisSerializer.byteArray())
                    .hashKey(RedisSerializer.string())
                    .hashValue(RedisSerializer.string())
                    .build());
            return template;
        }

        @Bean
        @ConditionalOnMissingBean
        public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory redisConnectionFactory) {
            ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(redisConnectionFactory);
            return template;
        }
    }


}