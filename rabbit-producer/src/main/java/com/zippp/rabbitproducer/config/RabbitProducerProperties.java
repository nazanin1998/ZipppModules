package com.zippp.rabbitproducer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for the rabbit-producer starter, bound to the
 * {@code zippp.rabbit.*} namespace in the target project's {@code application.yml}.
 *
 * <p>Activated by the starter's
 * {@link RabbitProducerConfig @AutoConfiguration} class via
 * {@link org.springframework.boot.context.properties.EnableConfigurationProperties}.
 * The class is registered with the container only when the starter is on the
 * classpath, so target projects that don't include the starter are unaffected.
 *
 * <p>Validation is enforced by {@link Validated @Validated}: every property is
 * checked at startup, and an invalid value causes a
 * {@code ConfigurationPropertiesBindException} on context refresh — i.e. the
 * app fails fast in tests as well as in production, never with a silent null
 * or a default that masks a misconfiguration.
 *
 * <h2>Example</h2>
 * <pre>
 * zippp:
 *   rabbit:
 *     reply-timeout: 10s
 * </pre>
 *
 * @param replyTimeout timeout for {@code sendAndReceive} replies. Must be
 *                     non-null; {@link Min @Min(0)} forbids negative values.
 *                     Defaults to 5 seconds when the target project omits the
 *                     property.
 */
@ConfigurationProperties(prefix = "zippp.producer.rabbit")
@Validated
public record RabbitProducerProperties(

        @NotNull
        Duration replyTimeout

) {
}
