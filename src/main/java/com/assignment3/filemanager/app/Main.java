package com.assignment3.filemanager.app;

import com.assignment3.filemanager.exception.FileManagerException;
import com.assignment3.filemanager.storage.StorageType;
import com.assignment3.filemanager.web.AuditTrail;
import com.assignment3.filemanager.web.WebServer;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class Main {

    private static final String USAGE = "Usage: java -jar file-manager.jar [--demo | --web] [--port=N] "
            + "[--storage=local|database] [--data-dir=PATH] [--quiet]";

    private enum Mode { CONSOLE, DEMO, WEB }

    private record Options(Mode mode, StorageType storage, Path dataDir, int port, boolean quiet) {

        static Options parse(String[] args) {
            Mode mode = Mode.CONSOLE;
            StorageType storage = null;
            Path dataDir = Path.of("data");
            int port = WebServer.DEFAULT_PORT;
            boolean quiet = false;

            for (String arg : args) {
                String value = arg.substring(arg.indexOf('=') + 1);
                if (arg.equals("--demo")) {
                    mode = Mode.DEMO;
                } else if (arg.equals("--web")) {
                    mode = mode == Mode.DEMO ? Mode.DEMO : Mode.WEB;
                } else if (arg.equals("--quiet")) {
                    quiet = true;
                } else if (arg.startsWith("--port=")) {
                    port = parsePort(value);
                } else if (arg.startsWith("--storage=")) {
                    storage = StorageType.parse(value);
                } else if (arg.startsWith("--data-dir=")) {
                    dataDir = Path.of(value);
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new Options(mode, storage, dataDir, port, quiet);
        }

        private static int parsePort(String value) {
            try {
                int port = Integer.parseInt(value.trim());
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
            }
            throw new IllegalArgumentException("Invalid port '" + value + "' (use a number from 1 to 65535)");
        }
    }

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException | FileManagerException e) {
            // FileManagerException comes from StorageType.parse (for example --storage=floppy)
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        try {
            switch (options.mode()) {
                case DEMO -> runDemo(options);
                case WEB -> runWeb(options);
                case CONSOLE -> runConsole(options);
            }
        } catch (BindException e) {
            fail("port " + options.port() + " is already in use. Pick another with --port=NNNN.");
        } catch (FileManagerException e) {
            fail(e.getMessage());
        }
    }

    private static void runDemo(Options options) throws IOException {
        StorageType storage = options.storage() != null ? options.storage() : StorageType.LOCAL;
        new DemoRunner(System.out).run(storage);
    }

    private static void runWeb(Options options) throws IOException {
        AuditTrail trail = new AuditTrail(consoleLog(options));
        FileSystemContext context = FileSystemContext.open(options.dataDir(), options.storage(), trail);
        WebServer server = new WebServer(context, trail, options.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("File manager web UI is running (bound to this computer only).");
        System.out.println("Open  http://localhost:" + server.port() + "/  in your browser.  Press Ctrl+C to stop.");
    }

    private static void runConsole(Options options) {
        FileSystemContext context = FileSystemContext.open(options.dataDir(), options.storage(), consoleLog(options));
        new ConsoleApp(System.in, System.out, context).run();
    }

    private static Consumer<String> consoleLog(Options options) {
        return options.quiet() ? null : message -> System.out.println("    " + message);
    }

    private static void fail(String message) {
        System.err.println("Error: " + message);
        System.exit(1);
    }
}
