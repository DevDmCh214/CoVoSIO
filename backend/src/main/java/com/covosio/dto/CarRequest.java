package com.covosio.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarRequest {

    @NotBlank(message = "Brand is required")
    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @NotBlank(message = "Color is required")
    @Size(max = 50, message = "Color must not exceed 50 characters")
    private String color;

    @NotBlank(message = "Plate is required")
    @Size(max = 20, message = "Plate must not exceed 20 characters")
    private String plate;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "A car must have at least 1 seat")
    @Max(value = 9, message = "A car must not exceed 9 seats")
    private Integer totalSeats;
}
