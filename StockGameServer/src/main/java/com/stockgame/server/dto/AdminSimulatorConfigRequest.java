package com.stockgame.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminSimulatorConfigRequest {
    @Min(0)
    @Max(50)
    private Integer retailCount;

    @Min(0)
    @Max(20)
    private Integer noiseCount;
}
