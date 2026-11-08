package com.clean.dd.owner.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRoomRequest(
		@NotBlank @Size(max = 20) String number,
		@NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
