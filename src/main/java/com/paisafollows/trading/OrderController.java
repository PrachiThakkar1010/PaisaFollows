package com.paisafollows.trading;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.auth.CurrentUser;
import com.paisafollows.trading.dao.OrderHistoryDao;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderHistoryDao historyDao;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, OrderHistoryDao historyDao, CurrentUser currentUser) {
        this.orderService = orderService;
        this.historyDao = historyDao;
        this.currentUser = currentUser;
    }

    /** POST /api/orders  {symbol, side, type, quantity} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }

    /** GET /api/orders?status=FILLED&page=0&size=20 */
    @GetMapping
    public OrderPage history(@RequestParam(required = false) OrderStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var accountId = currentUser.get().accountId();

        long total = historyDao.count(accountId, status);
        int totalPages = (int) Math.ceil(total / (double) safeSize);
        return new OrderPage(historyDao.findPage(accountId, status, safePage, safeSize),
                safePage, safeSize, total, totalPages);
    }
}