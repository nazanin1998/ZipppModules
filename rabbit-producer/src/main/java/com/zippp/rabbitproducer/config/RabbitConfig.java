package com.zippp.rabbitproducer.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for the producer module.
 *
 * <p>Exposes the single bean every caller depends on: a {@link RabbitTemplate}
 * autoconfigured with JSON serialization and a sane reply timeout.
 *
 * <p>Spring Boot will autoconfigure the {@link ConnectionFactory} from
 * {@code spring.rabbitmq.*} properties — no manual factory bean needed.
 */
@Configuration
public class RabbitConfig {

    /** Default reply timeout (ms) for {@code sendAndReceive}. */
    private static final long DEFAULT_REPLY_TIMEOUT_MS = 5_000L;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setReplyTimeout(DEFAULT_REPLY_TIMEOUT_MS);
        return template;
    }
//
//    /**
//     * Reply container required by {@code convertSendAndReceive}. Without an
//     * explicit listener container on the reply queue, Spring AMQP cannot
//     * route incoming replies back to the waiting caller.
//     */
//    @Bean
//    public SimpleMessageListenerContainer replyListenerContainer(ConnectionFactory connectionFactory) {
//        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
//        container.setQueueNames("amqp.rabbitmq.reply-to");
//        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
//        return container;
//    }
}