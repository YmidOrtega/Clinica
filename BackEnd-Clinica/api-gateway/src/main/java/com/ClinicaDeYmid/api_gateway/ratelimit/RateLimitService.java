package com.ClinicaDeYmid.api_gateway.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    // Límites configurados
    private static final long USER_RATE_LIMIT = 100; // requests por minuto
    private static final long IP_RATE_LIMIT = 1000;  // requests por minuto
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Verifica si un usuario puede hacer una petición
     * @param userId ID del usuario
     * @return true si puede hacer la petición, false si excede el límite
     */
    public boolean allowUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return true; // Si no hay usuario, solo aplica el límite por IP
        }

        String key = "rate_limit:user:" + userId;
        return checkRateLimit(key, USER_RATE_LIMIT, userBuckets);
    }

    /**
     * Verifica si una IP puede hacer una petición
     * @param ipAddress Dirección IP
     * @return true si puede hacer la petición, false si excede el límite
     */
    public boolean allowIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }

        String key = "rate_limit:ip:" + ipAddress;
        return checkRateLimit(key, IP_RATE_LIMIT, ipBuckets);
    }

    /**
     * Verifica el límite de rate usando buckets
     */
    private boolean checkRateLimit(String key, long limit, Map<String, Bucket> bucketMap) {
        Bucket bucket = bucketMap.computeIfAbsent(key, k -> createBucket(limit));
        return bucket.tryConsume(1);
    }

    /**
     * Crea un nuevo bucket con la configuración especificada
     */
    private Bucket createBucket(long capacity) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, REFILL_DURATION)
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Obtiene el tiempo de espera restante para el usuario
     */
    public long getRemainingSecondsForUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0;
        }
        String key = "rate_limit:user:" + userId;
        Bucket bucket = userBuckets.get(key);
        if (bucket != null) {
            return bucket.getAvailableTokens();
        }
        return USER_RATE_LIMIT;
    }

    /**
     * Obtiene el tiempo de espera restante para la IP
     */
    public long getRemainingSecondsForIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 0;
        }
        String key = "rate_limit:ip:" + ipAddress;
        Bucket bucket = ipBuckets.get(key);
        if (bucket != null) {
            return bucket.getAvailableTokens();
        }
        return IP_RATE_LIMIT;
    }

    /**
     * Limpia los buckets antiguos
     */
    public void cleanupOldBuckets() {
        // Se puede implementar una limpieza periódica si es necesario
        if (userBuckets.size() > 10000) {
            userBuckets.clear();
        }
        if (ipBuckets.size() > 10000) {
            ipBuckets.clear();
        }
    }
}
