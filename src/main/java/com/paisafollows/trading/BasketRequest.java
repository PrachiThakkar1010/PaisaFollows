package com.paisafollows.trading;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BasketRequest(
        @NotEmpty @Size(max = 10) List<@Valid BasketLeg> legs
) {
}