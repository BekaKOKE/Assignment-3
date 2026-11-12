package com.assignment3.filemanager.app;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.Role;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.proxy.PermissionChecker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TreeSet;

final class Display {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final PermissionChecker CHECKER = new PermissionChecker();

    private static final String ROW = "  %-9s %-28s %8s  %-10s %-7s %s%n";

    private Display() {
    }

    static String fileTable(List<FileRecord> files, User viewer) {
        if (files.isEmpty()) {
            return "  (no files)";
        }
        StringBuilder table = new StringBuilder(String.format(ROW, "ID", "NAME", "SIZE", "OWNER", "ROLE", "FOLDER"));
        for (FileRecord file : files) {
            table.append(String.format(ROW, file.id(), file.displayName(), file.size() + "B",
                    file.ownerId(), role(file, viewer, "-"), folder(file)));
        }
        return table.toString().stripTrailing();
    }

    static String fileDetails(FileRecord file, User viewer) {
        String sharedWith = file.sharedWith().isEmpty()
                ? "(nobody)"
                : String.join(", ", new TreeSet<>(file.sharedWith()));
        return String.join(System.lineSeparator(),
                "  Name        : " + file.name(),
                "  Type        : " + file.type().extension(),
                "  Size        : " + file.size() + " bytes",
                "  Created     : " + time(file.createdAt()),
                "  Owner       : " + file.ownerId(),
                "  Folder      : " + folder(file),
                "  Shared with : " + sharedWith,
                "  Your role   : " + role(file, viewer, "none"),
                "  ID          : " + file.id());
    }

    static String folderLine(Folder folder) {
        return "  [" + folder.id() + "] " + folder.name()
                + "  (owner: " + folder.ownerId() + ", created " + time(folder.createdAt()) + ")";
    }

    private static String time(Instant instant) {
        return TIME.format(instant);
    }

    private static String folder(FileRecord file) {
        return file.folderId() == null ? "(root)" : file.folderId();
    }

    private static String role(FileRecord file, User viewer, String fallback) {
        return CHECKER.roleOf(viewer, file).map(Role::name).orElse(fallback);
    }
}
