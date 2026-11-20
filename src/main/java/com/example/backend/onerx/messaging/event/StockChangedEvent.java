package com.example.backend.onerx.messaging.event;

/**
 * Domain event published inside a DB transaction whenever a SKU's available stock
 * or catalog status changes. Handled after transaction commit so OneRx only
 * receives committed stock balances.
 */
public record StockChangedEvent(Long skuId) {
}
