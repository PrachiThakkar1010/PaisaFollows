package com.paisafollows.admin;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.admin.AdminViews.ArchiveInfo;
import com.paisafollows.admin.AdminViews.ArchiveResult;
import com.paisafollows.admin.AdminViews.DelistResult;
import com.paisafollows.admin.AdminViews.RenameRequest;
import com.paisafollows.admin.AdminViews.RenameResult;
import com.paisafollows.admin.AdminViews.ResetResult;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.market.provider.SimulatedProvider;

import jakarta.validation.Valid;

/** Admin-only (see SecurityConfig: /api/admin/** needs ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDao dao;
    private final SimulatedProvider simulator;

    public AdminController(AdminDao dao, SimulatedProvider simulator) {
        this.dao = dao;
        this.simulator = simulator;
    }

    /** POST /api/admin/instruments/TATAMOTORS/rename  {"newSymbol":"TMPV"}   UPDATE with CASCADE */
    @PostMapping("/instruments/{symbol}/rename")
    public RenameResult rename(@PathVariable String symbol, @Valid @RequestBody RenameRequest request) {
        String oldSymbol = symbol.toUpperCase();
        String newSymbol = request.newSymbol().toUpperCase();
        try {
            if (dao.renameSymbol(oldSymbol, newSymbol) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + oldSymbol);
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS, newSymbol + " is already used by another stock");
        }
        simulator.reloadStocks();
        long alerts = dao.alertsFor(newSymbol);
        return new RenameResult(oldSymbol, newSymbol, alerts,
                "Renamed; " + alerts + " price alert(s) followed automatically through ON UPDATE CASCADE");
    }

    /** DELETE /api/admin/instruments/WIPRO   DELETE without cascade -> refused -> marked INACTIVE */
    @DeleteMapping("/instruments/{symbol}")
    public DelistResult delist(@PathVariable String symbol) {
        String s = symbol.toUpperCase();
        try {
            if (dao.deleteInstrument(s) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + s);
            }
            simulator.reloadStocks();
            return new DelistResult(s, true, "DELETED", "No other rows referred to it");
        } catch (DataIntegrityViolationException e) {
            dao.setStatus(s, "INACTIVE");
            simulator.reloadStocks();
            return new DelistResult(s, false, "INACTIVE",
                    "The database refused the DELETE (foreign keys without cascade: prices, holdings, orders "
                  + "still refer to it), so the stock was marked INACTIVE instead");
        }
    }

    /** POST /api/admin/instruments/WIPRO/activate */
    @PostMapping("/instruments/{symbol}/activate")
    public DelistResult activate(@PathVariable String symbol) {
        String s = symbol.toUpperCase();
        if (dao.setStatus(s, "ACTIVE") == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + s);
        }
        simulator.reloadStocks();
        return new DelistResult(s, false, "ACTIVE", "Tradable again");
    }

    /** POST /api/admin/price-history/archive   CREATE ... LIKE, ALTER, RENAME */
    @PostMapping("/price-history/archive")
    public ArchiveResult archive() {
        String table = dao.archiveHistory();
        long rows = dao.countRows(table);
        return new ArchiveResult(table, rows,
                "Old history kept as " + table + "; a new empty MARKET_PRICE_HISTORY is now in use");
    }

    /** GET /api/admin/price-history/archives */
    @GetMapping("/price-history/archives")
    public List<ArchiveInfo> archives() {
        return dao.listArchives();
    }

    /** DELETE /api/admin/price-history/archives/MARKET_PRICE_HISTORY_20261025_023000   DROP TABLE */
    @DeleteMapping("/price-history/archives/{table}")
    public List<ArchiveInfo> dropArchive(@PathVariable String table) {
        boolean exists = dao.listArchives().stream().anyMatch(a -> a.table().equals(table));
        if (!exists) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No archive called " + table);
        }
        dao.dropArchive(table);
        return dao.listArchives();
    }

    /** POST /api/admin/price-history/reset   TRUNCATE TABLE */
    @PostMapping("/price-history/reset")
    public ResetResult reset() {
        long rows = dao.countRows("MARKET_PRICE_HISTORY");
        dao.truncateHistory();
        return new ResetResult(rows, "MARKET_PRICE_HISTORY emptied with TRUNCATE");
    }
}