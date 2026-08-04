package com.ClinicaDeYmid.admissions_service.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Executor para el enriquecimiento de atenciones con datos de otros servicios.
     * <p>
     * Se declara explícitamente en lugar de dejar que {@code CompletableFuture.supplyAsync}
     * caiga en el {@code ForkJoinPool.commonPool()}: ese pool está dimensionado a
     * {@code nCores - 1} y está pensado para trabajo intensivo en CPU, no para I/O
     * bloqueante. Un puñado de llamadas Feign lentas lo agotaría y frenaría cualquier otra
     * tarea que dependa de él.
     * <p>
     * Con un hilo virtual por tarea, bloquearse esperando la respuesta HTTP libera el hilo
     * portador, así que el número de llamadas en vuelo no está limitado por el número de
     * núcleos.
     */
    @Bean
    public ExecutorService enrichmentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
