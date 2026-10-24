package com.paisafollows.watchlist;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.auth.AppUser;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.watchlist.WatchlistViews.Activity;
import com.paisafollows.watchlist.WatchlistViews.Idea;
import com.paisafollows.watchlist.WatchlistViews.MyStock;

@RestController
@RequestMapping("/api/my-stocks")
public class MyStocksController {

    private final MyStocksDao dao;
    private final CurrentUser currentUser;

    public MyStocksController(MyStocksDao dao, CurrentUser currentUser) {
        this.dao = dao;
        this.currentUser = currentUser;
    }

    /** GET /api/my-stocks : everything I watch OR own (FULL OUTER JOIN) */
    @GetMapping
    public List<MyStock> myStocks() {
        AppUser user = currentUser.get();
        return dao.myStocks(user.accountId(), user.portfolioId());
    }

    /** GET /api/my-stocks/watched-and-owned (INTERSECT) */
    @GetMapping("/watched-and-owned")
    public List<String> watchedAndOwned() {
        AppUser user = currentUser.get();
        return dao.watchedAndOwned(user.accountId(), user.portfolioId());
    }

    /** GET /api/my-stocks/watch-only : watched but never bought (EXCEPT) */
    @GetMapping("/watch-only")
    public List<String> watchOnly() {
        AppUser user = currentUser.get();
        return dao.watchOnly(user.accountId(), user.portfolioId());
    }

    /** GET /api/my-stocks/activity?limit=20 : buys, sells, watch-adds in one timeline (UNION ALL) */
    @GetMapping("/activity")
    public List<Activity> activity(@RequestParam(defaultValue = "20") int limit) {
        return dao.activity(currentUser.get().accountId(), Math.min(Math.max(limit, 1), 100));
    }

    /** GET /api/my-stocks/ideas : cheaper than a stock I own, not yet owned (ANY + NOT EXISTS) */
    @GetMapping("/ideas")
    public List<Idea> ideas() {
        return dao.ideas(currentUser.get().portfolioId());
    }
}