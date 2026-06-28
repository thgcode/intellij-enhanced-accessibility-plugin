package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.AccessibleAnnouncer;
import com.jetbrains.JBR;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.util.List;
import java.util.Set;

public class MyCaretPositionListener implements CaretListener {
    private final AccessibleAnnouncer accessibleAnnouncer = JBR.getAccessibleAnnouncer();
    private int lastReportedLine;

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        CaretListener.super.caretPositionChanged(event);
        checkLineForProblems(event);
        checkCoverage(event);
        lastReportedLine = event.getNewPosition().line;
    }

    private void checkLineForProblems(CaretEvent event) {
        int line = event.getNewPosition().line;

        if (line == lastReportedLine) {
            return;
        }

        Editor editor = event.getEditor();

        Project project = editor.getProject();

        if (project == null) {
            return;
        }

        Document document = editor.getDocument();

        int startOffset = document.getLineStartOffset(line);
        int endOffset = document.getLineEndOffset(line);

        List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, editor.getProject());

        for (HighlightInfo info: infos) {
            if (info.startOffset >= startOffset && info.endOffset <= endOffset) {
                playSound(info);
                break;
            }
        }
    }

    private void play(String filename)  {
        try {
            BufferedInputStream sound = new BufferedInputStream(getClass().getResourceAsStream(filename + ".mp3"));

            final AudioDevice audioDevice = new JavaSoundAudioDevice();
            final Player player = new Player(sound, audioDevice);
            new Thread(() -> {
                try {
                    player.play();
                } catch (JavaLayerException e) {
                    throw new RuntimeException(e);
                } finally {
                    player.close();
                    audioDevice.close();
                }
            }).start();
        } catch (JavaLayerException e) {
            throw new RuntimeException(e);
        }
    }

    public void playSound(HighlightInfo info) {
        if (info.getSeverity().equals(HighlightSeverity.WARNING)) {
            play("warning");
        } else if (info.getSeverity().equals(HighlightSeverity.ERROR)) {
            play("error");
        }

        speak(info.getDescription());
    }

    private void speak(String text) {
        accessibleAnnouncer.announce(null, text, AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
    }

    private void checkCoverage(CaretEvent event) {
        int line = event.getNewPosition().line;

        if (line == lastReportedLine) {
            return;
        }

        Editor editor = event.getEditor();

        Project project = event.getEditor().getProject();

        if (project == null) {
            return;
        }

        Document document = editor.getDocument();
        VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
        if (vFile == null) {
            return;
        }

        CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
        if (suitesBundle == null) {
            return;
        }

        System.out.println("Trying to get coverage for: " + vFile + " line: " + line);

        Module module = ModuleUtilCore.findModuleForFile(vFile, project);

        if (module == null) {
            System.out.println("Can't get module!");
            return;
        }

        CoverageEngine engine = suitesBundle.getCoverageEngine();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

        if (psiFile == null) {
            System.out.println("Can't get PSI file!");
            return;
        }

        Set<File> files = engine.getCorrespondingOutputFiles(psiFile, module, suitesBundle);
        List<Integer> coveredLines = engine.collectSrcLinesForUntouchedFile(files.stream().findFirst().orElseThrow(), suitesBundle);

        if (coveredLines == null || coveredLines.isEmpty()) {
            System.out.println("Error getting coverage!");
            return;
        }

        Integer lineHits = coveredLines.get(line);
        boolean isCovered = lineHits != null && lineHits > 0;

        if (!isCovered) {
            play("notcovered");
        }

        speak(isCovered ? "Covered" : "Not covered");

    }

}
