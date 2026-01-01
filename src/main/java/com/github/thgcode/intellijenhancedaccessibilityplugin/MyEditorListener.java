// Copyright 2025-2026 Thiago Seus and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class MyEditorListener implements FileEditorManagerListener {

    private final Project project;

    public MyEditorListener(Project project) {
        this.project = project;
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // Your code here: this method is called when a new file is opened
        System.out.println("File opened: " + file.getName());
        // You can get the editor using source.getSelectedTextEditor() or other methods
        Editor editor = source.getSelectedTextEditor();
        editor.getCaretModel().addCaretListener(new MyCaretPositionListener());
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        // Your code here: this method is called when the user switches between already open files
        VirtualFile newFile = event.getNewFile();
        if (newFile != null) {
            System.out.println("Switched to file: " + newFile.getName());
        }
    }
    // Other methods like fileClosed must also be implemented (can be empty if not needed)


}
