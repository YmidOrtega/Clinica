package com.ClinicaDeYmid.clients_service.infra.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper objectMapper) {

        log.info("🔧 Configurando Redis Cache Manager para clients-service");

        ObjectMapper redisMapper = objectMapper.copy();
        redisMapper.registerModule(new ParameterNamesModule());
        redisMapper.registerModule(new Jdk8Module());
        redisMapper.registerModule(new JavaTimeModule());
        redisMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        redisMapper.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        redisMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        log.info("✅ Módulos Jackson registrados: ParameterNamesModule, Jdk8Module, JavaTimeModule");

        // Configuración por defecto (1 hora)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(redisMapper)))
                .disableCachingNullValues();

        // Configuraciones específicas por caché
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // HealthProvider caches
        cacheConfigurations.put("health_provider_cache",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("health_providers_list_cache",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Contract caches
        cacheConfigurations.put("contract_cache",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("contract_dto_cache",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("contracts_by_provider_cache",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        cacheConfigurations.put("active_contracts_by_provider_cache",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        cacheConfigurations.put("contracts_list_cache",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        cacheConfigurations.put("contract_by_number_cache",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("contracts_search_cache",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        log.info("✅ Redis Cache Manager configurado con {} cachés personalizados",
                cacheConfigurations.size());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}