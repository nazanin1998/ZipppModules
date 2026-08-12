package com.zippp.rabbitconsumer.exception;

/**
 * Thrown when a {@code receive} / {@code receiveAndReply} operation cannot
 * complete within the configured time. Used both for:
 * <ul>
 *   <li>waiting on a reply from an upstream broker (request/reply pattern),</li>
 *   <li>waiting for a first message on a freshly-subscribed queue.</li>
 * </ul>
 */
public class RabbitConsumerFailedToParseException extends RabbitConsumerException {
    public RabbitConsumerFailedToParseException(Throwable cause) {
        super("rabbit_consumer_parse_failed", cause);
    }
}
