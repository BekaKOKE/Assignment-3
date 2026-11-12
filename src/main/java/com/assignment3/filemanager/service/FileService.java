package com.assignment3.filemanager.service;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.User;

import java.util.List;

/**
 * The common interface of the Proxy pattern (the Subject). Both the real file manager and the
 * protection proxy implement it, so a client cannot tell which one it is talking to.
 * Every call says which user it is made for: the real manager trusts that user, the proxy checks it.
 */
public interface FileService {

    FileRecord createFile(User actor, String name, FileType type, String content);

    String readFile(User actor, String fileId);

    FileRecord renameFile(User actor, String fileId, String newName);

    void deleteFile(User actor, String fileId);

    FileRecord shareFile(User actor, String fileId, User target);

    FileRecord getFileInfo(User actor, String fileId);

    List<FileRecord> listFiles(User actor);

    List<FileRecord> searchByName(User actor, String query);

    Folder createFolder(User actor, String name);

    Folder getFolder(User actor, String folderId);

    List<Folder> listFolders(User actor);

    List<FileRecord> listFolderContents(User actor, String folderId);

    FileRecord moveFileToFolder(User actor, String fileId, String folderId);

    void deleteFolder(User actor, String folderId);
}
