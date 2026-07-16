package com.stockgame.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAnnouncementRequest {
    @NotBlank
    @Size(max = 200)
    private String message;
}
