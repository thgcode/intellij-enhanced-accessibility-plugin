package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.jetbrains.AccessibleAnnouncer;
import com.jetbrains.JBR;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.util.List;

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

        String primaryClassQualifiedName = getPrimaryQualifiedName(project, vFile);

        if (primaryClassQualifiedName == null) {
            return;
        }

        CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
        if (suitesBundle == null) {
            return;
        }

        Object coverageData;
        coverageData = tryGetCoverageData(suitesBundle);
        boolean isCovered = false;
        if (coverageData != null) {
            System.out.println("Getting coverage for: " + line);
            Integer hits = tryGetHitsForFileAndLine(coverageData, vFile.getPath(), primaryClassQualifiedName, line + 1);
            isCovered = (hits != null && hits > 0);
        }

        if (!isCovered) {
            play("notcovered");
        }

        speak(isCovered ? "Covered" : "Not covered");

    }

    private String getPrimaryQualifiedName(Project project, VirtualFile vFile) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (psiFile instanceof PsiJavaFile javaFile) {
            if (javaFile.getClasses().length > 0) {
                return javaFile.getClasses()[0].getQualifiedName();
            }
        }
        return null;
    }

    /**
     * Try to obtain the CoverageData object for the given suites bundle.
     * This method attempts likely APIs used in 2024.2.x and falls back via reflection.
     */
    private Object tryGetCoverageData(CoverageSuitesBundle bundle) {
        return bundle.getCoverageData();
    }

    /**
     * Try several ways to ask the CoverageData object for hits on the given file/line.
     * Returns null if no information available; otherwise the hit count (0 or positive).
     *
     * Common ways:
     *  - coverageData.getHitsForFile(String path) -> int[] or Integer[] (each index -> hit count)
     *  - coverageData.getHits(String fileUrl, int line) -> Integer
     *  - coverageData.getClassData(String className) -> classData.getLineHits() -> int[]
     */
    private Integer tryGetHitsForFileAndLine(Object coverageData, String filePath, String fqName, int oneBasedLine) {
        if (fqName == null) {
            return null;
        }

        System.out.println("Getting coverage for: " + fqName + "Line: " + oneBasedLine);

        ProjectData data = (ProjectData) coverageData;
        ClassData classData = data.getClassData(fqName);

        if (classData == null) {
            return null;
        }

        int i = 0;
        for (Object lineDataO: classData.getLines()) {
            if (lineDataO instanceof LineData lineDataF){
                System.out.println("" + i + ": " + lineDataF.getHits());
            } else {
                System.out.println("" + i + ": " + lineDataO);
            }

            i++;
        }


        LineData lineData = classData.getLineData(oneBasedLine);

        if (lineData == null) {
            return null;
        }

        return lineData.getHits();
    }

}
