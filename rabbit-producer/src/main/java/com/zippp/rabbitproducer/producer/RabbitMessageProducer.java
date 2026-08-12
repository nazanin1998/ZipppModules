package com.zippp.rabbitproducer.producer;

import com.zippp.rabbitproducer.exception.RabbitProducerException;
import com.zippp.rabbitproducer.exception.RabbitProducerNullArgumentException;
import com.zippp.rabbitproducer.exception.RabbitProducerTimeoutException;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;
import java.util.UUID;

/**
 * {@link MessageProducer} implementation backed by Spring AMQP's {@link RabbitTemplate}.
 *
 * <p>This is the only class in the codebase that talks to RabbitTemplate directly —
 * callers depend on {@link MessageProducer}, never on RabbitTemplate. That keeps the
 * transport swappable: if the broker changes, only this class changes.
 *
 * <h2>Observability</h2>
 * Every publish is tagged with a generated {@code messageId} (UUID). The id is:
 * <ul>
 *   <li>attached to the outgoing AMQP message as the {@value #HEADER_MESSAGE_ID}
 *       header, so consumers can correlate it with their own logs;</li>
 *   <li>placed on SLF4J's {@link MDC} under {@code messageId} for the duration of
 *       the publish, so the surrounding logs (in this class and any downstream
 *       code on the same thread) carry the same id.</li>
 * </ul>
 *
 * <h2>Exception model</h2>
 * <ul>
 *   <li>Invalid arguments → {@link RabbitProducerNullArgumentException}
 *       (unchecked, extends {@link RabbitProducerException}).</li>
 *   <li>Broker / serialization failure → {@link RabbitProducerException}
 *       wrapping the original {@link AmqpException}.</li>
 *   <li>Reply timeout on {@code sendAndReceive} → {@link RabbitProducerTimeoutException}.</li>
 * </ul>
 */

public class RabbitMessageProducer implements MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessageProducer.class);

    /** MDC key used to correlate producer logs across a single publish call. */
    private static final String MDC_MESSAGE_ID = "messageId";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // ------------------------------------------------------------------
    // send (fire-and-forget)
    // ------------------------------------------------------------------

    @Override
    public void send(String exchange, String routingKey, Object payload) {
        send(exchange, routingKey, payload, (MessagePostProcessor) null);
    }

    @Override
    public void send(String exchange, String routingKey, Object payload, Map<String, Object> headers) {
        send(exchange, routingKey, payload, buildPostProcessor(headers));
    }

    @Override
    public void send(String exchange, String routingKey, Object payload, MessagePostProcessor postProcessor) {
        validateInputToSend(exchange, routingKey, payload);

        String messageId = UUID.randomUUID().toString();
        MessagePostProcessor effectivePostProcessor = composePostProcessor(messageId, postProcessor);

        publish(exchange, routingKey, payload, messageId, effectivePostProcessor);
    }

    // ------------------------------------------------------------------
    // sendAndReceive (request/reply)
    // ------------------------------------------------------------------

    @Override
    public <R> R sendAndReceive(String exchange, String routingKey, Object payload,
                                ParameterizedTypeReference<R> responseType) {
        return sendAndReceive(exchange, routingKey, payload, null, responseType);
    }

    @Override
    public <R> R sendAndReceive(String exchange, String routingKey, Object payload,
                                Map<String, Object> headers,
                                ParameterizedTypeReference<R> responseType) {
        if (responseType == null) {
            throw new RabbitProducerNullArgumentException("responseType is null");
        }
        validateInputToSend(exchange, routingKey, payload);

        String messageId = UUID.randomUUID().toString();
        MessagePostProcessor headerPostProcessor = buildPostProcessor(headers);
        MessagePostProcessor effectivePostProcessor = composePostProcessor(messageId, headerPostProcessor);

        return requestReply(exchange, routingKey, payload, responseType, messageId, effectivePostProcessor);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void publish(String exchange,
                         String routingKey,
                         Object payload,
                         String messageId,
                         MessagePostProcessor postProcessor) {
        String payloadType = payload.getClass().getName();
        MDC.put(MDC_MESSAGE_ID, messageId);
        try {
            log.debug("(RABBIT-PRODUCER) - starting send: exchange={}, routingKey={}, payloadType={}, messageId={}",
                    exchange, routingKey, payloadType, messageId);

            rabbitTemplate.convertAndSend(exchange, routingKey, payload, postProcessor);

            log.info("(RABBIT-PRODUCER) - published: exchange={}, routingKey={}, payloadType={}, messageId={}",
                    exchange, routingKey, payloadType, messageId);
        } catch (RuntimeException e) {
            log.error("(RABBIT-PRODUCER) - exception on send: exchange={}, routingKey={}, payloadType={}, messageId={}",
                    exchange, routingKey, payloadType, messageId, e);
            throw new RabbitProducerException("sending exception", e);
        } finally {
            MDC.remove(MDC_MESSAGE_ID);
        }
    }

    private <R> R requestReply(String exchange,
                               String routingKey,
                               Object payload,
                               ParameterizedTypeReference<R> responseType,
                               String messageId,
                               MessagePostProcessor postProcessor) {
        String payloadType = payload.getClass().getName();
        MDC.put(MDC_MESSAGE_ID, messageId);
        try {
            log.debug("(RABBIT-PRODUCER) - starting sendAndReceive: exchange={}, routingKey={}, payloadType={}, responseType={}, messageId={}",
                    exchange, routingKey, payloadType, responseType.getType(), messageId);

            R reply = rabbitTemplate.convertSendAndReceiveAsType(exchange, routingKey, payload,
                    postProcessor, responseType);

            if (reply == null) {
                log.warn("(RABBIT-PRODUCER) - sendAndReceive timed out: exchange={}, routingKey={}, payloadType={}, messageId={}",
                        exchange, routingKey, payloadType, messageId);
                throw new RabbitProducerTimeoutException();
            }

            log.info("(RABBIT-PRODUCER) - sendAndReceive completed: exchange={}, routingKey={}, payloadType={}, messageId={}, responseType={}",
                    exchange, routingKey, payloadType, messageId, responseType.getType());
            return reply;
        } catch (RabbitProducerTimeoutException e) {
            throw e; // already classified
        } catch (RuntimeException e) {
            log.error("(RABBIT-PRODUCER) - exception on sendAndReceive: exchange={}, routingKey={}, payloadType={}, messageId={}, responseType={}",
                    exchange, routingKey, payloadType, messageId, responseType.getType(), e);
            throw new RabbitProducerException("sending exception", e);
        } finally {
            MDC.remove(MDC_MESSAGE_ID);
        }
    }

    // ------------------------------------------------------------------
    // post-processor composition
    // ------------------------------------------------------------------

    /**
     * Builds a {@link MessagePostProcessor} that injects {@code headers} (if any)
     * into the outgoing message. Returns {@code null} when no headers were given,
     * which keeps the simple {@link RabbitTemplate#convertAndSend(String, String, Object)}
     * overload callable.
     */
    private static MessagePostProcessor buildPostProcessor(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        Map<String, Object> snapshot = Map.copyOf(headers);
        return message -> {
            for (Map.Entry<String, Object> e : snapshot.entrySet()) {
                message.getMessageProperties().setHeader(e.getKey(), e.getValue());
            }
            return message;
        };
    }

    /**
     * Composes the producer-owned post-processor (currently just the
     * {@code x-message-id} header) with a caller-supplied one.
     *
     * <p>Producer headers are applied first; the caller's post-processor runs
     * second, so it can observe and override them if it really needs to.
     */
    private static MessagePostProcessor composePostProcessor(String messageId,
                                                            MessagePostProcessor callerPostProcessor) {
        // Producer-owned step: always stamp x-message-id.
        MessagePostProcessor producerStep = message -> {
            message.getMessageProperties().setHeader(HEADER_MESSAGE_ID, messageId);
            return message;
        };

        return message -> {
            message = producerStep.postProcessMessage(message);
            if (callerPostProcessor != null) {
                message = callerPostProcessor.postProcessMessage(message);
            }
            return message;
        };
    }

    private static void validateInputToSend(String exchange, String routingKey, Object payload) {
        if (StringUtils.isBlank(exchange)) {
            throw new RabbitProducerNullArgumentException("Exchange name is empty");
        }
        if (StringUtils.isBlank(routingKey)) {
            throw new RabbitProducerNullArgumentException("RoutingKey name is empty");
        }
        if (payload == null) {
            throw new RabbitProducerNullArgumentException("Payload is null");
        }
    }
}
