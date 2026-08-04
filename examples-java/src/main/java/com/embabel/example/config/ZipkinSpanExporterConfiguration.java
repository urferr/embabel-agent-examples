/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.example.config;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Zipkin {@link SpanExporter} consumed by Embabel's
 * OpenTelemetry SDK auto-configuration.
 *
 * <p>Spring Boot 4 no longer auto-configures a {@code ZipkinSpanExporter}:
 * the support moved out of actuator and is deprecated for removal
 * ({@code ZipkinWithOpenTelemetryTracingAutoConfiguration}), so the bean
 * must be defined by the application.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ZipkinSpanExporter.class)
@ConditionalOnProperty("management.zipkin.tracing.endpoint")
public class ZipkinSpanExporterConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpanExporter.class)
    public SpanExporter zipkinSpanExporter(
            @Value("${management.zipkin.tracing.endpoint}") String endpoint) {
        return ZipkinSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }
}
