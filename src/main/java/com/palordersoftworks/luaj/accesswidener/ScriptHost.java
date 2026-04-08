package com.palordersoftworks.luaj.accesswidener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class ScriptHost {
    private final Consumer<String> infoSink;
    private final Consumer<String> errorSink;
    private final Queue<String> input = new ConcurrentLinkedQueue<>();

    public ScriptHost(Consumer<String> infoSink, Consumer<String> errorSink) {
        this.infoSink = infoSink;
        this.errorSink = errorSink;
    }

    public void print(String text) {
        infoSink.accept(text == null ? "nil" : text);
    }

    public void error(String text) {
        errorSink.accept(text == null ? "nil" : text);
    }

    public void pushInput(String text) {
        if (text != null && !text.isEmpty()) {
            input.add(text);
        }
    }

    public String readInput() {
        return input.poll();
    }
}