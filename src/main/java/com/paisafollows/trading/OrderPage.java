package com.paisafollows.trading;

import java.util.List;

/** One page of order history. */
public record OrderPage(List<OrderHistoryItem> items, int page, int size, long totalItems, int totalPages) {
}