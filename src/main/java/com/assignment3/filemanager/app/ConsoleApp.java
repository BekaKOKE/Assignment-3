package com.assignment3.filemanager.app;

import com.assignment3.filemanager.exception.FileManagerException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.StorageType;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

final class ConsoleApp {

    private final Scanner in;
    private final PrintStream out;
    private final FileSystemContext context;
    private final FileService service;
    private User user;

    ConsoleApp(InputStream in, PrintStream out, FileSystemContext context) {
        this.in = new Scanner(in);
        this.out = out;
        this.context = context;
        this.service = context.service();
    }

    void run() {
        out.println();
        out.println("=== File Management System (Bridge + Proxy) ===");
        out.println("Data directory: " + context.dataDir().toAbsolutePath());
        try {
            chooseUser();
            while (true) {
                printMenu();
                String choice = ask("Choose an option");
                if (choice.equals("0")) {
                    break;
                }
                try {
                    execute(choice);
                } catch (FileManagerException e) {
                    out.println("  ! " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            out.println();
            out.println("Input closed.");
        }
        out.println("Goodbye.");
    }

    private void printMenu() {
        out.println();
        out.println("--- user: " + user.id() + " | storage: " + context.storageType().label() + " ---");
        out.println(" Files:   1 create   2 list   3 search   4 read   5 info   6 rename   7 delete   8 share");
        out.println(" Folders: 9 create  10 list+contents  11 move file  12 delete folder");
        out.println(" System: 13 switch user  14 switch storage   0 exit");
    }

    private void execute(String choice) {
        switch (choice) {
            case "1" -> createFile();
            case "2" -> out.println(Display.fileTable(service.listFiles(user), user));
            case "3" -> out.println(Display.fileTable(service.searchByName(user, ask("Search text")), user));
            case "4" -> readFile();
            case "5" -> out.println(Display.fileDetails(service.getFileInfo(user, ask("File id")), user));
            case "6" -> renameFile();
            case "7" -> deleteFile();
            case "8" -> shareFile();
            case "9" -> createFolder();
            case "10" -> listFolders();
            case "11" -> moveFile();
            case "12" -> deleteFolder();
            case "13" -> chooseUser();
            case "14" -> switchStorage();
            default -> out.println("  ! Unknown option '" + choice + "'. Enter a number from the menu.");
        }
    }

    private void createFile() {
        String name = ask("File name (without extension)");
        FileType type = FileType.parse(ask("File type (" + FileType.allowed() + ")"));
        out.println("Content - finish with a line containing only a dot (.):");
        StringBuilder content = new StringBuilder();
        for (String line = in.nextLine(); !line.equals("."); line = in.nextLine()) {
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(line);
        }
        FileRecord file = service.createFile(user, name, type, content.toString());
        out.println("  Created " + file.displayName() + " (id " + file.id() + ", " + file.size() + " bytes)");
    }

    private void readFile() {
        String content = service.readFile(user, ask("File id"));
        out.println("  ----- content -----");
        out.println(content.isEmpty() ? "  (empty file)" : content);
        out.println("  -------------------");
    }

    private void renameFile() {
        String id = ask("File id");
        FileRecord file = service.renameFile(user, id, ask("New name (without extension)"));
        out.println("  Renamed to " + file.displayName());
    }

    private void deleteFile() {
        String id = ask("File id");
        FileRecord file = service.getFileInfo(user, id);
        if (confirm("Delete " + file.displayName() + "?")) {
            service.deleteFile(user, id);
            out.println("  Deleted.");
        }
    }

    private void shareFile() {
        String id = ask("File id");
        String userId = ask("Share with user (" + userIds() + ")");
        User target = context.users().find(userId).orElseThrow(() ->
                new NotFoundException("Unknown user '" + userId + "'. Known users: " + userIds()));
        FileRecord file = service.shareFile(user, id, target);
        out.println("  " + file.displayName() + " is now shared with " + target.id() + " (read-only).");
    }

    private void createFolder() {
        Folder folder = service.createFolder(user, ask("Folder name"));
        out.println("  Created folder " + folder.name() + " (id " + folder.id() + ")");
    }

    private void listFolders() {
        List<FileRecord> rootFiles = service.listFiles(user).stream()
                .filter(file -> file.folderId() == null)
                .toList();
        out.println("  (root)");
        out.println(rootFiles.isEmpty() ? "    (no files)" : Display.fileTable(rootFiles, user));

        for (Folder folder : service.listFolders(user)) {
            List<FileRecord> files = service.listFolderContents(user, folder.id());
            out.println(Display.folderLine(folder));
            out.println(files.isEmpty() ? "    (empty)" : Display.fileTable(files, user));
        }
    }

    private void moveFile() {
        String fileId = ask("File id");
        String folderId = ask("Target folder id (blank = move back to root)");
        FileRecord file = service.moveFileToFolder(user, fileId, folderId);
        String place = file.folderId() == null ? "(root)" : "folder " + file.folderId();
        out.println("  " + file.displayName() + " is now in " + place);
    }

    private void deleteFolder() {
        String id = ask("Folder id");
        Folder folder = service.getFolder(user, id);
        if (confirm("Delete folder " + folder.name() + "? (it must be empty)")) {
            service.deleteFolder(user, id);
            out.println("  Folder deleted.");
        }
    }

    private void chooseUser() {
        out.println("Users: " + userIds());
        while (true) {
            String id = ask("Act as which user");
            Optional<User> found = context.users().find(id);
            if (found.isPresent()) {
                user = found.get();
                out.println("  Now acting as " + user.id() + ".");
                return;
            }
            out.println("  ! Unknown user '" + id + "'.");
        }
    }

    private void switchStorage() {
        StorageType target = StorageType.parse(ask("Switch to storage (local / database)"));
        if (target == context.storageType()) {
            out.println("  Already using " + target.label() + " storage.");
            return;
        }
        context.switchStorage(target);
        out.println("  Storage switched to " + target.label() + ". Existing files were migrated.");
    }

    private String userIds() {
        return context.users().all().stream().map(User::id).collect(Collectors.joining(", "));
    }

    private String ask(String label) {
        out.print(label + "> ");
        out.flush();
        return in.nextLine().trim();
    }

    private boolean confirm(String question) {
        String answer = ask(question + " (y/N)");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }
}
