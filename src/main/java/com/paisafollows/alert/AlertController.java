package com.paisafollows.alert;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.alert.AlertViews.AlertView;
import com.paisafollows.alert.AlertViews.CreateAlertRequest;
import com.paisafollows.alert.AlertViews.UpdateAlertRequest;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertDao dao;
    private final CurrentUser currentUser;

    public AlertController(AlertDao dao, CurrentUser currentUser) {
        this.dao = dao;
        this.currentUser = currentUser;
    }

    /** GET /api/alerts?status=TRIGGERED   (status optional: ACTIVE or TRIGGERED) */
    @GetMapping
    public List<AlertView> list(@RequestParam(required = false) String status) {
        String s = status == null ? null : status.toUpperCase();
        if (s != null && !s.equals("ACTIVE") && !s.equals("TRIGGERED")) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "status must be ACTIVE or TRIGGERED");
        }
        return dao.findAll(currentUser.get().accountId(), s);
    }

    /** POST /api/alerts  {"symbol":"RELIANCE","direction":"ABOVE","targetPrice":1500} */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<AlertView> create(@Valid @RequestBody CreateAlertRequest request) {
        var accountId = currentUser.get().accountId();
        try {
            UUID id = dao.create(accountId, request.symbol(), request.direction(),
                    request.targetPrice().setScale(2, RoundingMode.HALF_UP));
            if (id == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + request.symbol());
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS, "You already have this exact alert");
        }
        return dao.findAll(accountId, null);
    }

    /** PUT /api/alerts/{id}  {"targetPrice": 1550}  (only ACTIVE alerts can be changed) */
    @PutMapping("/{id}")
    public List<AlertView> update(@PathVariable UUID id, @Valid @RequestBody UpdateAlertRequest request) {
        var accountId = currentUser.get().accountId();
        if (dao.updateTarget(id, accountId, request.targetPrice().setScale(2, RoundingMode.HALF_UP)) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No ACTIVE alert with this id");
        }
        return dao.findAll(accountId, null);
    }

    /** DELETE /api/alerts/{id} */
    @DeleteMapping("/{id}")
    public List<AlertView> delete(@PathVariable UUID id) {
        var accountId = currentUser.get().accountId();
        if (dao.delete(id, accountId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Alert not found");
        }
        return dao.findAll(accountId, null);
    }
}