package com.paisafollows.trading;

import java.util.List;

public record BasketResult(List<BasketLegResult> legs, long filled, long rolledBack) {
}