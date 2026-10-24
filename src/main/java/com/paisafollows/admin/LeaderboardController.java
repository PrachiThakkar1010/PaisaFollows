package com.paisafollows.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.admin.AdminViews.LeaderboardEntry;

@RestController
public class LeaderboardController {

    private final AdminDao dao;

    public LeaderboardController(AdminDao dao) {
        this.dao = dao;
    }

    /** GET /api/leaderboard?limit=10 */
    @GetMapping("/api/leaderboard")
    public List<LeaderboardEntry> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return dao.leaderboard(Math.min(Math.max(limit, 1), 100));
    }
}