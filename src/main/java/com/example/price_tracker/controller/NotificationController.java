package com.example.price_tracker.controller;

import com.example.price_tracker.common.PageResult;
import com.example.price_tracker.common.Result;
import com.example.price_tracker.dto.NotificationQueryDto;
import com.example.price_tracker.service.NotificationService;
import com.example.price_tracker.vo.NotificationVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Management", description = "Query, read, and manage user price alert notifications (Requires JWT)")
@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Get My Notifications", description = "Fetch unread and read price alert notifications page by page for the authenticated user (Requires JWT)")
    @GetMapping("/my")
    public Result<PageResult<NotificationVo>> my(@Valid NotificationQueryDto queryDto) {
        return Result.success(notificationService.pageMyNotifications(queryDto));
    }

    @Operation(summary = "Mark Notification as Read", description = "Mark a specific user notification as read by ID (Requires JWT)")
    @PutMapping("/{id}/read")
    public Result<Void> read(
            @Parameter(description = "Notification ID") @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        notificationService.markRead(id);
        return Result.success();
    }
}
