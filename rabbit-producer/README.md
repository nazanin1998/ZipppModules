# rabbit-producer

A Spring Boot starter that lets any Spring Boot service publish messages to RabbitMQ by depending on a single artifact. No `@Configuration` classes to write, no `RabbitTemplate` to wire, no `MessageConverter` to register.

```xml
<dependency>
    <groupId>com.zippp</groupId>
    <artifactId>rabbit-producer</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Inject `MessageProducer` and call `send(...)`.

---

## What you get automatically

| Bean | Type | Notes |
|---|---|---|
| `MessageConverter` | `JacksonJsonMessageConverter` | Outgoing payloads are serialized as JSON. |
| `RabbitTemplate` | Spring AMQP | Pre-configured with the JSON converter and a configurable reply timeout. |
| `MessageProducer` | `RabbitMessageProducer` | The only API callers should depend on. Wraps `RabbitTemplate` and stamps a correlation id on every message. |

Every bean is `@ConditionalOnMissingBean`, so a target project can override any of them by declaring its own bean of the same type.

---

## Activation rules

The autoconfiguration activates when **all** of the following hold:

1. `RabbitTemplate` is on the classpath (target project must include `spring-boot-starter-amqp`).
2. `zippp.rabbit.producer.enabled` is not set to `false`.

Disabling:

```yaml
zippp:
  rabbit:
    producer:
      enabled: false
```

---

## Configuration

Properties live under `zippp.rabbit.*`:

```yaml
zippp:
  rabbit:
    reply-timeout: 10s          # ISO-8601 Duration; used by sendAndReceive
```

| Property | Type | Default | Validation |
|---|---|---|---|
| `zippp.rabbit.reply-timeout` | `Duration` | `5s` | `@NotNull`, `@Min(0)` — startup fails if absent, negative, or zero (set explicitly to disable the timeout) |
| `zippp.rabbit.producer.enabled` | `boolean` | `true` | — |

The broker connection itself is configured by the standard Spring AMQP namespace, not by this starter:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

---

## Usage

### 1. Fire-and-forget publish

```java
@Service
public class OrderService {

    private final MessageProducer producer;

    public OrderService(MessageProducer producer) {
        this.producer = producer;
    }

    public void placeOrder(OrderDto order) {
        producer.send("orders.exchange", "order.created", order);
    }
}
```

The `payload` is serialized by the autoconfigured `JacksonJsonMessageConverter`. The producer stamps an `x-message-id` header on every outgoing message.

### 2. Publish with extra headers

```java
producer.send(
    "orders.exchange",
    "order.created",
    order,
    Map.of("tenant", "acme", "trace-id", traceId)
);
```

Producer-owned headers (currently `x-message-id`) take precedence — caller-supplied keys with the same name are overwritten.

### 3. Publish with a custom post-processor

Use this when you need full control over the outgoing AMQP message — setting `content_type`, attaching a `timestamp`, or rewriting the body:

```java
producer.send(
    "orders.exchange",
    "order.created",
    order,
    message -> {
        message.getMessageProperties().setContentType("application/json");
        message.getMessageProperties().setTimestamp(new Date());
        return message;
    }
);
```

The producer still injects its correlation id unless the post-processor overrides it.

### 4. Request/reply (`sendAndReceive`)

```java
public <R> R sendAndReceive(String exchange, String routingKey, Object payload,
                            ParameterizedTypeReference<R> responseType);
public <R> R sendAndReceive(String exchange, String routingKey, Object payload,
                            Map<String, Object> headers,
                            ParameterizedTypeReference<R> responseType);
```

Blocks the calling thread until a reply is received or the configured `zippp.rabbit.reply-timeout` elapses. Uses `ParameterizedTypeReference` to preserve generic type info (collections, nested generics).

```java
OrderConfirmation confirmation = producer.sendAndReceive(
    "orders.exchange",
    "order.create",
    order,
    new ParameterizedTypeReference<OrderConfirmation>() {}
);
```

> **Requires** a reply queue / listener container on the consumer side. Without it, every call times out.

---

## Exception model

| Exception | Thrown when |
|---|---|
| `RabbitProducerNullArgumentException` | `exchange`, `routingKey`, or `payload` is null/blank, or `responseType` is null in `sendAndReceive` |
| `RabbitProducerTimeoutException` | `sendAndReceive` did not get a reply within `zippp.rabbit.reply-timeout` |
| `RabbitProducerException` | Wraps any other broker / serialization failure (root cause preserved) |

All three extend `RuntimeException` (unchecked) and `RabbitProducerException`.

---

## Observability

Every publish is tagged with a UUID `messageId`:

- attached to the outgoing AMQP message as the `x-message-id` header — consumers can correlate their logs with the producer's;
- placed on SLF4J's `MDC` under the key `messageId` for the duration of the publish — surrounding logs on the same thread carry the same id.

Log line pattern (example):

```
2026-08-09 12:34:56 INFO  RabbitMessageProducer - (RABBIT-PRODUCER) - published: exchange=orders.exchange, routingKey=order.created, payloadType=com.acme.OrderDto, messageId=4f8b...
```

Structured log shipping should pick up `messageId` from MDC and propagate it.

---

## Overriding beans in the target project

Every bean is `@ConditionalOnMissingBean`. Declare your own and the starter backs off:

```java
@Bean
public MessageConverter jsonMessageConverter(ObjectMapper mapper) {
    // custom converter using the target project's ObjectMapper
    return new JacksonJsonMessageConverter(mapper);
}
```

The same applies to `RabbitTemplate` and `MessageProducer`.

---

## Project layout

```
rabbit-producer/
├── pom.xml
└── src/main/
    ├── java/com/zippp/rabbitproducer/
    │   ├── config/
    │   │   ├── RabbitConfig.java              # @AutoConfiguration
    │   │   └── RabbitProducerProperties.java  # @ConfigurationProperties("zippp.rabbit")
    │   ├── exception/
    │   │   ├── RabbitProducerException.java
    │   │   ├── RabbitProducerNullArgumentException.java
    │   │   └── RabbitProducerTimeoutException.java
    │   └── producer/
    │       ├── MessageProducer.java           # public interface — depend on this
    │       └── RabbitMessageProducer.java     # internal implementation
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Quick checklist for a new target project

1. Add the two dependencies (`com.zippp:rabbit-producer` and `spring-boot-starter-amqp`).
2. Configure `spring.rabbitmq.host` / `port` / `username` / `password` in `application.yml`.
3. (Optional) Override `zippp.rabbit.reply-timeout` if 5s is wrong for your workload.
4. Inject `MessageProducer` where needed — never `RabbitTemplate`.
5. Configure a reply queue + listener container on the consumer side if you plan to use `sendAndReceive`.

---

## Versioning

`0.0.1-SNAPSHOT` — pre-release. API and configuration namespace (`zippp.rabbit.*`) may change before `1.0.0`.