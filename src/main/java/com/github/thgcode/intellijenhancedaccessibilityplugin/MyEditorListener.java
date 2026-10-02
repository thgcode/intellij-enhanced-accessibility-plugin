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
        System.out.println("File opened: " + file.getName());
        Editor editor = source.getSelectedTextEditor();
        editor.getCaretModel().addCaretListener(new MyCaretPositionListener());
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile newFile = event.getNewFile();
        if (newFile != null) {
            System.out.println("Switched to file: " + newFile.getName());
        }
    }

}
