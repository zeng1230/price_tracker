package com.example.price_tracker.controller;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.WatchlistAddDto;
import com.example.price_tracker.dto.WatchlistQueryDto;
import com.example.price_tracker.dto.WatchlistUpdateDto;
import com.example.price_tracker.redis.RateLimit;
import com.example.price_tracker.service.WatchlistService;
import com.example.price_tracker.vo.WatchlistVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Watchlist Management", description = "Add, update, view, or remove items from the user's price watchlist (Requires JWT)")
@Validated
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @Operation(summary = "Add Product to Watchlist", description = "Monitor a product with a target price (Requires JWT, Rate Limited)")
    @RateLimit
    @PostMapping
    public Result<Long> add(@Valid @RequestBody WatchlistAddDto watchlistAddDto) {
        return Result.success(watchlistService.addWatchlist(watchlistAddDto));
    }

    @Operation(summary = "Get My Watchlist", description = "Fetch currently monitored products page by page for the authenticated user (Requires JWT)")
    @GetMapping("/my")
    public Result<PageResult<WatchlistVo>> my(@Valid WatchlistQueryDto queryDto) {
        return Result.success(watchlistService.pageMyWatchlist(queryDto));
    }

    @Operation(summary = "Update Watchlist Target Price", description = "Update the target price or notification preferences for an entry in the watchlist (Requires JWT, Rate Limited)")
    @RateLimit
    @PutMapping("/{id}")
    public Result<Void> update(
            @Parameter(description = "Watchlist Entry ID") @PathVariable Long id,
            @Valid @RequestBody WatchlistUpdateDto watchlistUpdateDto) {
        watchlistService.updateWatchlist(id, watchlistUpdateDto);
        return Result.success();
    }

    @Operation(summary = "Remove Product from Watchlist", description = "Stop monitoring a product by removing its watchlist entry (Requires JWT, Rate Limited)")
    @RateLimit
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "Watchlist Entry ID") @PathVariable Long id) {
        watchlistService.deleteWatchlist(id);
        return Result.success();
    }
}
