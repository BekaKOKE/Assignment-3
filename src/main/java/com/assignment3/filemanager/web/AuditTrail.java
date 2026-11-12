package com.assignment3.filemanager.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class AuditTrail implements Consumer<String> {

    private static final int CAPACITY = 300;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    public record Entry(long seq, String time, String text, boolean granted) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Consumer<String> echo;
    private long nextSeq = 1;

    public AuditTrail(Consumer<String> echo) {
        this.echo = echo;
    }

    @Override
    public synchronized void accept(String message) {
        boolean granted = !message.contains(" : DENIED");
        entries.addLast(new Entry(nextSeq++, LocalTime.now().format(CLOCK), message, granted));
        if (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
        if (echo != null) {
            echo.accept(message);
        }
    }

    public synchronized List<Entry> since(long seq) {
        return entries.stream().filter(entry -> entry.seq() > seq).toList();
    }
}
