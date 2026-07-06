package com.example.price_tracker.notification;

import com.example.price_tracker.entity.NotificationDelivery;

public interface WebhookDeliveryClient {

    WebhookDeliveryResult send(NotificationDelivery delivery);
}
