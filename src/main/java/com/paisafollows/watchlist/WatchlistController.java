package com.paisafollows.watchlist;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.auth.AppUser;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.watchlist.WatchlistViews.AddItemRequest;
import com.paisafollows.watchlist.WatchlistViews.CreateWatchlistRequest;
import com.paisafollows.watchlist.WatchlistViews.DeleteResult;
import com.paisafollows.watchlist.WatchlistViews.Suggestion;
import com.paisafollows.watchlist.WatchlistViews.WatchlistItem;
import com.paisafollows.watchlist.WatchlistViews.WatchlistSummary;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistDao dao;
    private final CurrentUser currentUser;

    public WatchlistController(WatchlistDao dao, CurrentUser currentUser) {
        this.dao = dao;
        this.currentUser = currentUser;
    }

    /** GET /api/watchlists : my watchlists with item counts */
    @GetMapping
    public List<WatchlistSummary> list() {
        return dao.findAll(currentUser.get().accountId());
    }

    /** POST /api/watchlists  {"name": "Banks"} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistSummary create(@Valid @RequestBody CreateWatchlistRequest request) {
        UUID id = dao.create(currentUser.get().accountId(), request.name());
        return new WatchlistSummary(id, request.name().trim(), 0, null);
    }

    /** GET /api/watchlists/suggestions : popular stocks I am not watching */
    @GetMapping("/suggestions")
    public List<Suggestion> suggestions() {
        return dao.suggestions(currentUser.get().accountId());
    }

    /** GET /api/watchlists/{id}/items */
    @GetMapping("/{id}/items")
    public List<WatchlistItem> items(@PathVariable UUID id) {
        AppUser user = requireOwner(id);
        return dao.items(id, user.portfolioId());
    }

    /** POST /api/watchlists/{id}/items  {"symbol": "TCS"} */
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public List<WatchlistItem> addItem(@PathVariable UUID id, @Valid @RequestBody AddItemRequest request) {
        AppUser user = requireOwner(id);
        try {
            if (dao.addItem(id, request.symbol()) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + request.symbol());
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS, request.symbol().toUpperCase() + " is already in this watchlist");
        }
        return dao.items(id, user.portfolioId());
    }

    /** DELETE /api/watchlists/{id}/items/TCS */
    @DeleteMapping("/{id}/items/{symbol}")
    public List<WatchlistItem> removeItem(@PathVariable UUID id, @PathVariable String symbol) {
        AppUser user = requireOwner(id);
        if (dao.removeItem(id, symbol) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, symbol.toUpperCase() + " is not in this watchlist");
        }
        return dao.items(id, user.portfolioId());
    }

    /** DELETE /api/watchlists/{id} : its items are deleted by ON DELETE CASCADE */
    @DeleteMapping("/{id}")
    public DeleteResult delete(@PathVariable UUID id) {
        AppUser user = requireOwner(id);
        long items = dao.countItems(id);
        dao.delete(id, user.accountId());
        return new DeleteResult("Watchlist deleted; its items were removed by ON DELETE CASCADE", items);
    }

    /** Someone else's watchlist looks exactly like one that does not exist */
    private AppUser requireOwner(UUID watchlistId) {
        AppUser user = currentUser.get();
        if (!dao.owns(watchlistId, user.accountId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Watchlist not found");
        }
        return user;
    }
}