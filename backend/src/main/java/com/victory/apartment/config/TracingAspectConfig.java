package com.victory.apartment.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpanParser;
import io.micrometer.tracing.annotation.SpanAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * Enables @NewSpan annotation processing from Micrometer Tracing,
 * matching the slide's SpringBootObservability setup (Slide 2 & 6).
 */
@Configuration
public class TracingAspectConfig {

    @Bean
    public NewSpanParser newSpanParser() {
        return new DefaultNewSpanParser();
    }

    @Bean
    public MethodInvocationProcessor methodInvocationProcessor(NewSpanParser newSpanParser, Tracer tracer) {
        return new ImperativeMethodInvocationProcessor(newSpanParser, tracer, null);
    }

    @Bean
    public SpanAspect spanAspect(MethodInvocationProcessor methodInvocationProcessor) {
        return new SpanAspect(methodInvocationProcessor);
    }
}
