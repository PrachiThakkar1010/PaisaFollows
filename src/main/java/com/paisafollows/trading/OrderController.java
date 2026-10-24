package com.paisafollows.trading;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final BasketOrderService basketService;
    private final OrderHistoryDao historyDao;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, BasketOrderService basketService,
                           OrderHistoryDao historyDao, CurrentUser currentUser) {
        this.orderService = orderService;
        this.basketService = basketService;
        this.historyDao = historyDao;
        this.currentUser = currentUser;
    }

    /** POST /api/orders  {symbol, side, type, quantity, price?, triggerPrice?} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }

    /** DELETE /api/orders/{orderId}  cancels an OPEN order */
    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(@PathVariable UUID orderId) {
        return orderService.cancel(orderId);
    }

    /** POST /api/orders/basket  {legs: [...]}  several market orders, one transaction, one SAVEPOINT each */
    @PostMapping("/basket")
    @ResponseStatus(HttpStatus.CREATED)
    public BasketResult basket(@Valid @RequestBody BasketRequest request) {
        return basketService.place(request);
    }

    /** GET /api/orders?status=OPEN&page=0&size=20 */
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