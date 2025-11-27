package com.ClinicaDeYmid.clients_service.infra.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    @Primary
    @Bean(name = "objectMapper")
    public ObjectMapper httpObjectMapper() {
        return configureObjectMapper(new ObjectMapper());
    }

    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        return configureObjectMapper(new ObjectMapper());
    }

    private ObjectMapper configureObjectMapper(ObjectMapper mapper) {

        mapper.registerModule(new ParameterNamesModule());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        return mapper;
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {

        log.info("Configurando Redis Cache Manager (versión 2.0 - sin activateDefaultTyping)");

        // Configuración por defecto (1 hora)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper)))
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

        log.info("Redis Cache Manager configurado con {} cachés personalizados",
                cacheConfigurations.size());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}