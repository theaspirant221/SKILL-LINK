package com.skilllink.api.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AnalysisAsyncConfiguration {
    @Bean(name = "analysisExecutor")
    ThreadPoolTaskExecutor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(20); executor.setThreadNamePrefix("skilllink-analysis-"); executor.initialize(); return executor;
    }
}
