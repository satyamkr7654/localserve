package com.localserve.config;

import com.mongodb.client.MongoClient;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import java.time.Clock;

@Configuration
public class CoreConfiguration {
    @Bean Clock utcClock() { return Clock.systemUTC(); }
    @Bean MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
    @Bean CircuitBreakerRegistry circuitBreakers() { return CircuitBreakerRegistry.ofDefaults(); }
    @Bean RetryRegistry retries() { return RetryRegistry.ofDefaults(); }
    @Bean RateLimiterRegistry rateLimiters() { return RateLimiterRegistry.ofDefaults(); }
    @Bean TimeLimiterRegistry timeLimiters() { return TimeLimiterRegistry.ofDefaults(); }
    @Bean BulkheadRegistry bulkheads() { return BulkheadRegistry.ofDefaults(); }
}
