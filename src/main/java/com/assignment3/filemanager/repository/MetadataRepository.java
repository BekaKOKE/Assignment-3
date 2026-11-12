package com.assignment3.filemanager.repository;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.Folder;

import java.util.List;
import java.util.Optional;

public interface MetadataRepository {

    void saveFile(FileRecord file);

    Optional<FileRecord> findFile(String id);

    List<FileRecord> findAllFiles();

    List<FileRecord> findFilesInFolder(String folderId);

    void deleteFile(String id);

    void saveFolder(Folder folder);

    Optional<Folder> findFolder(String id);

    List<Folder> findAllFolders();

    void deleteFolder(String id);
}
