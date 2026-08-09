package com.zippp.rabbitproducer.config;

import com.zippp.rabbitproducer.producer.MessageProducer;
import com.zippp.rabbitproducer.producer.RabbitMessageProducer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Spring Boot autoconfigure for the rabbit-producer starter.
 *
 * <p>Target projects drop in a dependency on {@code com.zippp:rabbit-producer} and
 * automatically receive:
 * <ul>
 *   <li>a {@link MessageConverter} (Jackson JSON) — overridable,</li>
 *   <li>a {@link RabbitTemplate} configured with that converter and a reply timeout
 *       sourced from {@link RabbitProducerProperties} — overridable,</li>
 *   <li>a {@link MessageProducer} backed by the template — overridable.</li>
 * </ul>
 *
 * <p>Activation rules:
 * <ul>
 *   <li>{@link RabbitTemplate} must be on the classpath (the
 *       {@code spring-boot-starter-amqp} dep is declared {@code <optional>} so the
 *       target must add it).</li>
 *   <li>Activation can be disabled with {@code zippp.rabbit.producer.enabled=false}
 *       in {@code application.yml}.</li>
 *   <li>Every bean is {@link ConditionalOnMissingBean @ConditionalOnMissingBean} so the
 *       target project can override any of them by declaring its own bean of the same type.</li>
 * </ul>
 *
 * <p>Configuration lives under {@code zippp.rabbit.*}; see
 * {@link RabbitProducerProperties} for the full property contract and validation
 * rules.
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "zippp.producer.rabbit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RabbitProducerProperties.class)
public class RabbitConfig {

    /** Default reply timeout for {@code sendAndReceive} when the target project omits
     *  the {@code zippp.rabbit.reply-timeout} property. */
    private static final Duration DEFAULT_REPLY_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter,
                                         RabbitProducerProperties properties) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setReplyTimeout(replyTimeoutMs(properties));
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageProducer messageProducer(RabbitTemplate rabbitTemplate) {
        return new RabbitMessageProducer(rabbitTemplate);
    }

    /**
     * Reply timeout resolution order:
     * <ol>
     *   <li>{@code zippp.rabbit.reply-timeout} from the target project's
     *       {@code application.yml} — validated {@code @NotNull} + {@code @Min(0)}.</li>
     *   <li>{@link #DEFAULT_REPLY_TIMEOUT} (5s) when the property is absent.</li>
     * </ol>
     * Validation guarantees we never see a {@code null} or negative value here;
     * falling back to the default is only for the "no key in yaml" case.
     */
    private static long replyTimeoutMs(RabbitProducerProperties properties) {
        Duration timeout = properties.replyTimeout() != null
                ? properties.replyTimeout()
                : DEFAULT_REPLY_TIMEOUT;
        return timeout.toMillis();
    }
}
