package com.zippp.rabbitconsumer.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for the consumer module.
 *
 * <p>Exposes two beans the target project's {@code @Configuration} class uses
 * to build {@link com.zippp.rabbitconsumer.consumer.MessageConsumer} instances:
 * <ul>
 *   <li>{@link MessageConverter} — Jackson-based JSON converter.</li>
 *   <li>{@link SimpleRabbitListenerContainerFactory} — preconfigured factory
 *       that produces the {@code SimpleMessageListenerContainer}s each
 *       {@code MessageConsumer} registers.</li>
 * </ul>
 *
 * <p>Spring Boot autoconfigures the {@link ConnectionFactory} from
 * {@code spring.rabbitmq.*} properties — no manual factory bean needed.
 *
 * <h2>Why no {@code @Component MessageConsumer}</h2>
 * <p>This module deliberately does <strong>not</strong> register a
 * {@code MessageConsumer} bean itself. Each consumer is a user-defined bean
 * constructed with {@link ConsumerConfig} and a handler — that keeps
 * subscription count, queue names, and lifecycle fully under the target
 * project's control. See {@code MessageConsumer} javadoc for usage.
 */
@Configuration
public class RabbitConsumerConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
