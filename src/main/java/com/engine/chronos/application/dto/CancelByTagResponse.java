package com.engine.chronos.application.dto;

public record CancelByTagResponse(
        String tag,
        int cancelledCount
) {}
