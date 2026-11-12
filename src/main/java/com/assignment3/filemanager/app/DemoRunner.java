package com.assignment3.filemanager.app;

import com.assignment3.filemanager.exception.FileManagerException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.StorageType;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class DemoRunner {

    private final PrintStream out;
    private int step;

    DemoRunner(PrintStream out) {
        this.out = out;
    }

    void run(StorageType storage) throws IOException {
        Path dir = Files.createTempDirectory("fms-demo-");
        try {
            scenario(dir, storage);
        } finally {
            deleteRecursively(dir);
        }
    }

    private void scenario(Path dir, StorageType storage) {
        FileSystemContext context = FileSystemContext.open(dir, storage, out::println);
        FileService service = context.service();
        User beksultan = context.users().find("beksultan").orElseThrow();
        User ruslan = context.users().find("ruslan").orElseThrow();
        User kuanysh = context.users().find("kuanysh").orElseThrow();

        step("Setup");
        out.println("Users (simulated): beksultan, ruslan, kuanysh. Storage implementation: " + context.storageType().label());
        out.println("Clients talk to FileService = ProtectionProxy; every [PROXY] line is a permission decision.");

        step("Beksultan creates a file");
        FileRecord report = attempt("beksultan creates report.txt", () ->
                service.createFile(beksultan, "report", FileType.TXT, "Q3 report: revenue up 12%.\nConfidential."))
                .orElseThrow();
        out.println(Display.fileDetails(report, beksultan));

        step("The file is stored through the selected storage implementation");
        if (context.storageType() == StorageType.LOCAL) {
            Path onDisk = dir.resolve("files").resolve(report.id() + ".dat");
            out.println("  Content is a file on disk: " + onDisk + "  exists=" + Files.exists(onDisk));
        } else {
            out.println("  Content is a BLOB row in table file_content of " + dir.resolve("filemanager.db"));
        }
        out.println("  Metadata (name, type, size, owner, shares) is in SQLite table 'files'.");

        step("Kuanysh has no access yet -> read is DENIED by the proxy");
        attempt("kuanysh reads report", () -> service.readFile(kuanysh, report.id()));

        step("Beksultan (OWNER) shares the file with Ruslan");
        attempt("beksultan shares report with ruslan", () -> service.shareFile(beksultan, report.id(), ruslan));

        step("Ruslan (VIEWER) reads: proxy checks permission, then FileManager -> storage");
        attempt("ruslan reads report", () -> service.readFile(ruslan, report.id())).ifPresent(this::printContent);

        step("Ruslan (VIEWER) tries restricted operations -> all DENIED");
        attempt("ruslan renames report", () -> service.renameFile(ruslan, report.id(), "hacked"));
        attemptVoid("ruslan deletes report", () -> service.deleteFile(ruslan, report.id()));
        attempt("ruslan shares report with kuanysh", () -> service.shareFile(ruslan, report.id(), kuanysh));
        out.println("  Ruslan still sees the file as: " + service.getFileInfo(ruslan, report.id()).displayName());

        step("Beksultan (OWNER) rename, search, list");
        attempt("beksultan renames report -> q3-report", () -> service.renameFile(beksultan, report.id(), "q3-report"));
        out.println(Display.fileTable(service.searchByName(beksultan, "Q3"), beksultan));
        out.println("  Ruslan's file list:");
        out.println(Display.fileTable(service.listFiles(ruslan), ruslan));
        out.println("  Kuanysh's file list:");
        out.println(Display.fileTable(service.listFiles(kuanysh), kuanysh));

        step("Folders");
        Folder docs = attempt("beksultan creates folder 'docs'", () -> service.createFolder(beksultan, "docs")).orElseThrow();
        attempt("beksultan moves q3-report into docs", () -> service.moveFileToFolder(beksultan, report.id(), docs.id()));
        attempt("ruslan tries to move the file out of docs", () -> service.moveFileToFolder(ruslan, report.id(), null));
        attemptVoid("ruslan tries to delete beksultan's folder", () -> service.deleteFolder(ruslan, docs.id()));
        attemptVoid("beksultan deletes the non-empty folder (policy: refused)", () -> service.deleteFolder(beksultan, docs.id()));
        out.println("  Contents of docs as seen by ruslan (only what is shared with him):");
        out.println(Display.fileTable(service.listFolderContents(ruslan, docs.id()), ruslan));

        step("Input validation and error handling");
        attempt("create file with path characters in the name",
                () -> service.createFile(beksultan, "../etc/passwd", FileType.TXT, "x"));
        attempt("create file with unknown type",
                () -> service.createFile(beksultan, "notes", FileType.parse("exe"), "x"));
        attempt("create file with blank name", () -> service.createFile(beksultan, "   ", FileType.TXT, "x"));
        attempt("create duplicate name", () -> service.createFile(beksultan, "q3-report", FileType.TXT, "again"));
        attempt("read a file that does not exist", () -> service.readFile(beksultan, "nope1234"));

        step("BRIDGE: swap the storage implementation at runtime");
        StorageType before = context.storageType();
        context.switchStorage(before.other());
        out.println("  Storage changed " + before.label() + " -> " + context.storageType().label()
                + "  (DefaultFileManager / FileService / ProtectionProxy were not modified)");
        attempt("ruslan reads the same file again", () -> service.readFile(ruslan, report.id()))
                .ifPresent(this::printContent);

        step("Persistence: reopen the application from the same data directory");
        FileSystemContext reopened = FileSystemContext.open(dir, null, out::println);
        FileService afterRestart = reopened.service();
        out.println("  Reopened with storage '" + reopened.storageType().label() + "' (remembered from last run).");
        attempt("ruslan reads after restart", () -> afterRestart.readFile(ruslan, report.id()))
                .ifPresent(this::printContent);

        step("Clean up");
        attemptVoid("beksultan deletes the file", () -> afterRestart.deleteFile(beksultan, report.id()));
        attemptVoid("beksultan deletes the (now empty) folder", () -> afterRestart.deleteFolder(beksultan, docs.id()));
        out.println();
        out.println("Demo finished.");
    }

    private <T> Optional<T> attempt(String title, Supplier<T> action) {
        try {
            T result = action.get();
            out.println("  [OK]       " + title);
            return Optional.ofNullable(result);
        } catch (FileManagerException e) {
            out.println("  [REJECTED] " + title);
            out.println("             " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void attemptVoid(String title, Runnable action) {
        attempt(title, () -> {
            action.run();
            return true;
        });
    }

    private void step(String title) {
        out.println();
        out.println("=== Step " + (++step) + ": " + title + " ===");
    }

    private void printContent(String text) {
        out.println("  content: \"" + text.replace("\n", " / ") + "\"");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }
}
