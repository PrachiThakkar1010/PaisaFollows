package com.paisafollows.common;

/** Implemented by trading. Called by market data on every new price. */
public interface TickListener {

    void onTick(Tick tick);
}