package com.zippp.rabbitconsumer.handler;

import com.zippp.rabbitconsumer.exception.RabbitConsumerFailedToParseException;
import com.zippp.rabbitconsumer.model.ConsumerParsedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Listens on {@code otp.signup.request} and returns the dispatch result
 * via the RPC reply queue.
 *
 * The {@code correlationId} is read from the AMQP {@code x-message-id}
 * header (NOT from the request body) so it survives even if the payload
 * is malformed or partially deserializable.
 */
public class MessageParser {

    private static final Logger log = LoggerFactory.getLogger(MessageParser.class);

    /** AMQP header carrying the per-request correlation id. */
    public static final String CORRELATION_HEADER = "x-message-id";

    public <T> ConsumerParsedMessage<T> parsedMessage(Message amqpMessage, Class<T> resClass, JsonMapper jsonMapper) {
        String correlationId = extractCorrelationId(amqpMessage);

        T payload;
        try {
            String body = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
            payload = jsonMapper.readValue(body, resClass);
        } catch (Exception ex) {
            log.error("(RABBIT-CONSUMER) - Failed to deserialize consumed message with corrId={}", correlationId, ex);
            throw new RabbitConsumerFailedToParseException(ex);
        }

        log.debug("(RABBIT-CONSUMER) - Received message with corrId={}, payload={}", correlationId, payload);
        return new ConsumerParsedMessage<>(correlationId, payload);
    }

    private String extractCorrelationId(Message amqpMessage) {
        Object headerValue = amqpMessage.getMessageProperties().getHeader(CORRELATION_HEADER);
        if (headerValue == null) {
            log.warn("Missing {} header on signup request — generating fallback id",
                    CORRELATION_HEADER);
            return java.util.UUID.randomUUID().toString();
        }
        return headerValue.toString();
    }
}
