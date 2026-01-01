// Copyright 2025-2026 Thiago Seus and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class MyProjectStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        // Connect to the project's message bus
        MessageBusConnection connection = project.getMessageBus().connect();

        // Subscribe to the FILE_EDITOR_MANAGER topic
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorListener(project));

    }
}
