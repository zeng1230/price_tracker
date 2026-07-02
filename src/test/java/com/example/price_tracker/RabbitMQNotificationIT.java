package com.example.price_tracker;

import com.example.price_tracker.config.RabbitMQConfig;
import com.example.price_tracker.entity.Notification;
import com.example.price_tracker.entity.Watchlist;
import com.example.price_tracker.mapper.NotificationMapper;
import com.example.price_tracker.mapper.WatchlistMapper;
import com.example.price_tracker.mq.message.PriceAlertMessage;
import com.example.price_tracker.mq.producer.PriceAlertProducer;
import com.example.price_tracker.redis.RedisCacheService;
import com.example.price_tracker.redis.RedisKeyManager;
import com.example.price_tracker.service.NotificationService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1",
        "spring.rabbitmq.listener.simple.retry.max-interval=100ms"
})
@Testcontainers
@ActiveProfiles("it")
public class RabbitMQNotificationIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("price_tracker")
            .withUsername("price_tracker")
            .withPassword("price_tracker");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PriceAlertProducer priceAlertProducer;

    @Autowired
    private WatchlistMapper watchlistMapper;

    @SpyBean
    private NotificationMapper notificationMapper;

    @Autowired
    private RedisCacheService cacheService;

    @SpyBean
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long watchlistId;
    private Long userId = 1001L;
    private Long productId = 2002L;

    @BeforeEach
    void setUp() {
        // Clean up database tables physically (bypass logic delete)
        jdbcTemplate.execute("DELETE FROM tb_notification");
        jdbcTemplate.execute("DELETE FROM tb_watchlist");

        // Insert a valid, eligible watchlist record
        Watchlist watchlist = Watchlist.builder()
                .userId(userId)
                .productId(productId)
                .targetPrice(new BigDecimal("100.00"))
                .lastNotifiedPrice(null)
                .notifyEnabled(1)
                .status(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        watchlistMapper.insert(watchlist);
        watchlistId = watchlist.getId();
    }

    @Test
    void verifyRabbitMQBindingsExist() {
        // Verify queues are declared
        Properties queueProps = rabbitAdmin.getQueueProperties(RabbitMQConfig.PRICE_ALERT_QUEUE);
        assertThat(queueProps).isNotNull();
        assertThat(queueProps.get(RabbitAdmin.QUEUE_NAME)).isEqualTo(RabbitMQConfig.PRICE_ALERT_QUEUE);

        Properties dlqProps = rabbitAdmin.getQueueProperties(RabbitMQConfig.PRICE_ALERT_DLQ);
        assertThat(dlqProps).isNotNull();
        assertThat(dlqProps.get(RabbitAdmin.QUEUE_NAME)).isEqualTo(RabbitMQConfig.PRICE_ALERT_DLQ);
    }

    @Test
    void verifyPriceAlertFlow_SuccessAndDeduplication() {
        String messageId = UUID.randomUUID().toString();
        String eventKey = "TARGET_PRICE_REACHED:" + userId + ":" + productId + ":100.00:95.00:" + System.currentTimeMillis();

        PriceAlertMessage message = PriceAlertMessage.builder()
                .messageId(messageId)
                .eventKey(eventKey)
                .userId(userId)
                .productId(productId)
                .watchlistId(watchlistId)
                .productName("Test Product")
                .currentPrice(new BigDecimal("95.00"))
                .targetPrice(new BigDecimal("100.00"))
                .triggeredAt(LocalDateTime.now())
                .build();

        // 1. Send normal PriceAlertEvent and verify it is consumed and stored
        priceAlertProducer.send(message);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Notification notification = notificationMapper.selectByEventKey(eventKey);
                    assertThat(notification).isNotNull();
                    assertThat(notification.getUserId()).isEqualTo(userId);
                    assertThat(notification.getProductId()).isEqualTo(productId);
                    assertThat(notification.getWatchlistId()).isEqualTo(watchlistId);
                    assertThat(notification.getContent()).contains("Test Product current price 95.00 reached target 100.00");
                });

        // Verify Redis idempotent key is created and value is "1"
        String idempotentKey = RedisKeyManager.notificationIdempotentKey("mq:" + messageId);
        String value = cacheService.get(idempotentKey, String.class);
        assertThat(value).isEqualTo("1");

        // 2. Verify duplicate message is skipped due to Redis idempotency
        // Clear spy invocations to trace the consumer
        clearInvocations(notificationService);

        // Publish exact same message again
        priceAlertProducer.send(message);

        // Wait a bit to ensure it would have been processed
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that notificationService was NOT invoked for the duplicate message (deduplicated by Redis key)
        verify(notificationService, never()).consumePriceAlert(any());

        // 3. Verify DB eventKey unique constraint fallback when Redis idempotent key is missing
        cacheService.delete(idempotentKey);

        // Publish again. This time Redis idempotency check passes (as we deleted the key).
        // But the DB selectByEventKey check will find the existing record and skip insertion.
        priceAlertProducer.send(message);

        // Verify NotificationService.consumePriceAlert was invoked, but did not cause a new insert or error
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    verify(notificationService, atLeastOnce()).consumePriceAlert(any());
                    // Check database count of notifications - should still be exactly 1
                    Long count = notificationMapper.selectCount(null);
                    assertThat(count).isEqualTo(1L);
                });
    }

    @Test
    void verifyDBUniqueKeyConflictFallback() {
        // Here we test the DuplicateKeyException catch block by forcing selectByEventKey to return null
        // even though a DB record already exists with that eventKey.
        String messageId = UUID.randomUUID().toString();
        String eventKey = "TARGET_PRICE_REACHED:CONFLICT:" + messageId;

        // Bypassing normal producer flow: we manually insert a notification directly with the eventKey
        Notification notification = Notification.builder()
                .userId(userId)
                .productId(productId)
                .watchlistId(watchlistId)
                .eventKey(eventKey)
                .notifyType("TARGET_PRICE_REACHED")
                .content("Initial notification")
                .isRead(0)
                .sendStatus(1)
                .createdAt(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .build();
        notificationMapper.insert(notification);

        // Now we spy/stub notificationMapper.selectByEventKey(eventKey) to return null!
        // This forces consumePriceAlert to attempt insertion and throw DuplicateKeyException.
        doReturn(null).when(notificationMapper).selectByEventKey(eventKey);

        PriceAlertMessage message = PriceAlertMessage.builder()
                .messageId(messageId)
                .eventKey(eventKey)
                .userId(userId)
                .productId(productId)
                .watchlistId(watchlistId)
                .productName("Conflict Product")
                .currentPrice(new BigDecimal("95.00"))
                .targetPrice(new BigDecimal("100.00"))
                .triggeredAt(LocalDateTime.now())
                .build();

        // Send the message. Redis key is empty since it's a new messageId.
        priceAlertProducer.send(message);

        // Awaitility: ensure consumePriceAlert runs. It should execute successfully without throwing an exception,
        // because it catches the DuplicateKeyException and logs it.
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    verify(notificationService, atLeastOnce()).consumePriceAlert(any());
                    // Check database count of notifications - should be exactly 1 (the one we manually inserted)
                    Long count = notificationMapper.selectCount(null);
                    assertThat(count).isEqualTo(1L);
                });
    }

    @Test
    void verifyConsumptionFailureEntersDLQ() {
        String messageId = "fail-msg-" + UUID.randomUUID();
        String eventKey = "TARGET_PRICE_REACHED:FAIL:" + messageId;

        PriceAlertMessage message = PriceAlertMessage.builder()
                .messageId(messageId)
                .eventKey(eventKey)
                .userId(userId)
                .productId(productId)
                .watchlistId(watchlistId)
                .productName("Fail Product")
                .currentPrice(new BigDecimal("95.00"))
                .targetPrice(new BigDecimal("100.00"))
                .triggeredAt(LocalDateTime.now())
                .build();

        // Stub notificationService.consumePriceAlert to throw an exception when this message is processed
        doThrow(new RuntimeException("Simulated consumption failure"))
                .when(notificationService)
                .consumePriceAlert(argThat(msg -> msg != null && messageId.equals(msg.getMessageId())));

        // Send the message
        priceAlertProducer.send(message);

        // Use Awaitility to wait until the message reaches the DLQ
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    // Check if DLQ has received a message.
                    Object dlqMessage = rabbitTemplate.receiveAndConvert(RabbitMQConfig.PRICE_ALERT_DLQ);
                    assertThat(dlqMessage).isNotNull();
                    assertThat(dlqMessage).isInstanceOf(PriceAlertMessage.class);
                    PriceAlertMessage received = (PriceAlertMessage) dlqMessage;
                    assertThat(received.getMessageId()).isEqualTo(messageId);
                });
    }
}
