package com.paisafollows.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the materialized view MV_SECTOR_SUMMARY fresh by calling its refresh procedure. */
@Component
public class SectorSummaryJob {

    private static final Logger log = LoggerFactory.getLogger(SectorSummaryJob.class);

    private final MarketQueryDao dao;

    public SectorSummaryJob(MarketQueryDao dao) {
        this.dao = dao;
    }

    /** First run 20 s after start (prices are saved by then), then every 5 minutes */
    @Scheduled(fixedRate = 300000, initialDelay = 20000)
    public void refresh() {
        try {
            dao.refreshSectorSummary();
            log.info("CALL sp_refresh_mv_sector_summary() done");
        } catch (RuntimeException e) {
            log.error("Sector summary refresh failed", e);
        }
    }
}