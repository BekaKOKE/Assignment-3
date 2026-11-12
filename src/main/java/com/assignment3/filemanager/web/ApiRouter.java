package com.assignment3.filemanager.web;

import com.assignment3.filemanager.app.FileSystemContext;
import com.assignment3.filemanager.exception.AccessDeniedException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Role;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.proxy.PermissionChecker;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.StorageType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ApiRouter {

    record FileView(String id, String name, String type, String displayName, long size, Instant createdAt,
                    String ownerId, String folderId, Set<String> sharedWith, String role) {
    }

    private final FileSystemContext context;
    private final FileService service;
    private final AuditTrail trail;
    private final PermissionChecker checker = new PermissionChecker();

    ApiRouter(FileSystemContext context, AuditTrail trail) {
        this.context = context;
        this.service = context.service();
        this.trail = trail;
    }

    // the file manager is not thread safe, so requests are handled one at a time
    synchronized Response handle(Request request) {
        List<String> path = request.path();
        if (path.isEmpty()) {
            throw HttpError.notFound();
        }
        if (path.equals(List.of("session"))) {
            request.require("GET");
            return Response.ok(session());
        }

        User actor = actor(request);
        return switch (path.get(0)) {
            case "files" -> files(request, actor);
            case "folders" -> folders(request, actor);
            case "storage" -> storage(request, actor);
            case "log" -> log(request, actor);
            default -> throw HttpError.notFound();
        };
    }

    private Map<String, Object> session() {
        return Map.of(
                "users", context.users().all(),
                "storage", context.storageType(),
                "storages", List.of(StorageType.values()));
    }

    private Response files(Request request, User actor) {
        List<String> path = request.path();
        return switch (path.size()) {
            case 1 -> fileCollection(request, actor);
            case 2 -> singleFile(request, actor, path.get(1));
            case 3 -> fileAction(request, actor, path.get(1), path.get(2));
            default -> throw HttpError.notFound();
        };
    }

    private Response fileCollection(Request request, User actor) {
        Map<String, String> form = request.form();
        return switch (request.method()) {
            case "GET" -> {
                String search = request.query().get("q");
                List<FileRecord> files = search == null || search.isBlank()
                        ? service.listFiles(actor)
                        : service.searchByName(actor, search);
                yield Response.ok(Map.of("files", views(files, actor)));
            }
            case "POST" -> {
                FileType type = FileType.parse(form.get("type"));
                FileRecord file = service.createFile(actor, form.get("name"), type, form.get("content"));
                yield Response.created(fileBody(file, actor));
            }
            default -> throw HttpError.methodNotAllowed();
        };
    }

    private Response singleFile(Request request, User actor, String id) {
        return switch (request.method()) {
            case "GET" -> Response.ok(fileBody(service.getFileInfo(actor, id), actor));
            case "DELETE" -> {
                service.deleteFile(actor, id);
                yield Response.ok(Map.of("deleted", id));
            }
            default -> throw HttpError.methodNotAllowed();
        };
    }

    private Response fileAction(Request request, User actor, String id, String action) {
        Map<String, String> form = request.form();
        switch (action) {
            case "content" -> {
                request.require("GET");
                return Response.ok(Map.of("content", service.readFile(actor, id)));
            }
            case "rename" -> {
                request.require("POST");
                return Response.ok(fileBody(service.renameFile(actor, id, form.get("name")), actor));
            }
            case "share" -> {
                request.require("POST");
                String targetId = form.get("target");
                User target = context.users().find(targetId).orElseThrow(() ->
                        new NotFoundException("Unknown user '" + sanitize(targetId) + "'."));
                return Response.ok(fileBody(service.shareFile(actor, id, target), actor));
            }
            case "move" -> {
                request.require("POST");
                return Response.ok(fileBody(service.moveFileToFolder(actor, id, form.get("folderId")), actor));
            }
            default -> throw HttpError.notFound();
        }
    }

    private Response folders(Request request, User actor) {
        List<String> path = request.path();
        if (path.size() > 2) {
            throw HttpError.notFound();
        }
        if (path.size() == 1) {
            return switch (request.method()) {
                case "GET" -> Response.ok(Map.of("folders", service.listFolders(actor)));
                case "POST" -> Response.created(
                        Map.of("folder", service.createFolder(actor, request.form().get("name"))));
                default -> throw HttpError.methodNotAllowed();
            };
        }

        String id = path.get(1);
        return switch (request.method()) {
            case "GET" -> Response.ok(Map.of(
                    "folder", service.getFolder(actor, id),
                    "files", views(service.listFolderContents(actor, id), actor)));
            case "DELETE" -> {
                service.deleteFolder(actor, id);
                yield Response.ok(Map.of("deleted", id));
            }
            default -> throw HttpError.methodNotAllowed();
        };
    }

    private Response storage(Request request, User actor) {
        if (request.path().size() != 1) {
            throw HttpError.notFound();
        }
        request.require("POST");
        requireKnown(actor);

        StorageType before = context.storageType();
        StorageType target = StorageType.parse(request.form().get("type"));
        context.switchStorage(target);
        if (before != target) {
            trail.accept("[BRIDGE] " + actor.id() + " switched storage " + before.label() + " -> "
                    + target.label() + " : content migrated, same FileManager");
        }
        return Response.ok(Map.of("storage", target));
    }

    private Response log(Request request, User actor) {
        if (request.path().size() != 1) {
            throw HttpError.notFound();
        }
        request.require("GET");
        requireKnown(actor);
        try {
            long since = Long.parseLong(request.query().getOrDefault("since", "0"));
            return Response.ok(Map.of("entries", trail.since(since)));
        } catch (NumberFormatException e) {
            throw new HttpError(400, "'since' must be a number.");
        }
    }

    // an unknown name is passed on as a user so that the proxy rejects and logs it
    private User actor(Request request) {
        String name = request.user();
        if (name == null || name.isBlank()) {
            throw new HttpError(401, "Missing X-User header (the simulated login).");
        }
        return context.users().find(name).orElseGet(() -> new User(sanitize(name), sanitize(name)));
    }

    private void requireKnown(User actor) {
        if (!context.users().contains(actor)) {
            throw new AccessDeniedException("Access denied: unknown user '" + actor.id() + "'.");
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String text = name.strip();
        return text.substring(0, Math.min(text.length(), 32)).replaceAll("[^A-Za-z0-9_-]", "?");
    }

    private Map<String, Object> fileBody(FileRecord file, User actor) {
        return Map.of("file", view(file, actor));
    }

    private List<FileView> views(List<FileRecord> files, User actor) {
        return files.stream().map(file -> view(file, actor)).toList();
    }

    private FileView view(FileRecord file, User actor) {
        String role = checker.roleOf(actor, file).map(Role::name).orElse(null);
        return new FileView(file.id(), file.name(), file.type().extension(), file.displayName(), file.size(),
                file.createdAt(), file.ownerId(), file.folderId(), new TreeSet<>(file.sharedWith()), role);
    }
}
