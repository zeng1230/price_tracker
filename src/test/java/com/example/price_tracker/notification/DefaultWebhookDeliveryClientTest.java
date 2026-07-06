package com.example.price_tracker.notification;

import com.example.price_tracker.entity.NotificationDelivery;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWebhookDeliveryClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendPostsPayloadWithHmacSignature() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        server = startServer(202, receivedBody, receivedSignature);
        DefaultWebhookDeliveryClient client = client("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "secret-123");

        WebhookDeliveryResult result = client.send(delivery("{\"eventKey\":\"abc\"}"));

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(202);
        assertThat(receivedBody.get()).isEqualTo("{\"eventKey\":\"abc\"}");
        assertThat(receivedSignature.get()).isNotBlank().startsWith("sha256=");
    }

    @Test
    void sendClassifiesServerErrorAsRetryable() throws Exception {
        server = startServer(500, new AtomicReference<>(), new AtomicReference<>());
        DefaultWebhookDeliveryClient client = client("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "secret-123");

        WebhookDeliveryResult result = client.send(delivery("{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.error()).contains("status=500");
    }

    @Test
    void sendClassifiesClientErrorAsDead() throws Exception {
        server = startServer(400, new AtomicReference<>(), new AtomicReference<>());
        DefaultWebhookDeliveryClient client = client("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "secret-123");

        WebhookDeliveryResult result = client.send(delivery("{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.error()).contains("status=400");
    }

    private HttpServer startServer(int status,
                                   AtomicReference<String> receivedBody,
                                   AtomicReference<String> receivedSignature) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/hook", exchange -> {
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Price-Tracker-Signature"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private DefaultWebhookDeliveryClient client(String url, String secret) {
        DefaultWebhookDeliveryClient client = new DefaultWebhookDeliveryClient();
        ReflectionTestUtils.setField(client, "webhookUrl", url);
        ReflectionTestUtils.setField(client, "webhookSecret", secret);
        ReflectionTestUtils.setField(client, "timeoutMillis", 1000L);
        return client;
    }

    private NotificationDelivery delivery(String payload) {
        return NotificationDelivery.builder()
                .id(1L)
                .eventKey("abc")
                .channel("WEBHOOK")
                .payload(payload)
                .build();
    }
}
