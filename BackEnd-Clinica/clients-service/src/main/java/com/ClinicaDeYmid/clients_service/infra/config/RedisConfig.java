package com.ClinicaDeYmid.clients_service.infra.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    /**
     * Configuración del ObjectMapper para serialización JSON en Redis
     */
    @Bean
    public ObjectMapper redisCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Registrar módulo para manejar Java 8 date/time
        mapper.registerModule(new JavaTimeModule());

        // Activar información de tipo para polimorfismo
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    /**
     * Configuración del CacheManager con TTL personalizado por caché
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper redisCacheObjectMapper) {

        log.info("Configurando Redis Cache Manager");

        // Configuración por defecto (1 hora)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(redisCacheObjectMapper)))
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