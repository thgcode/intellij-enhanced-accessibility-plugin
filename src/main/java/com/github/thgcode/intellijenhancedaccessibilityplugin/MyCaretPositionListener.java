package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.AccessibleAnnouncer;
import com.jetbrains.JBR;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.lang.reflect.Field;
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

    private void playSound(String filename)  {
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
            playSound("warning");
        } else if (info.getSeverity().equals(HighlightSeverity.ERROR)) {
            playSound("error");
        }

        speak(info.getDescription());
    }

    private void speak(String text) {
        accessibleAnnouncer.announce(null, text, AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
    }

    @SuppressWarnings("unchecked")
    public static <T> Key<T> getCoverageHighlightersKey() {
        try {
            // Dynamically look up the internal package-private class
            Class<?> clazz = Class.forName("com.intellij.coverage.CoverageEditorAnnotatorImpl");
            Field field = clazz.getDeclaredField("COVERAGE_HIGHLIGHTERS");
            field.setAccessible(true);
            return (Key<T>) field.get(null);
        } catch (Exception e) {
            // Fallback or log if the internal implementation changes across IDE versions
            return Key.create("COVERAGE_HIGHLIGHTERS");
        }
    }

    private void checkCoverage(CaretEvent event) {
        Editor editor = event.getEditor();
        int currentLine = editor.getCaretModel().getLogicalPosition().line;

        List<RangeHighlighter> highligters = editor.getUserData(getCoverageHighlightersKey());

        for (RangeHighlighter highlighter : highligters) {
            int lineStart = editor.getDocument().getLineNumber(highlighter.getStartOffset());

            if (lineStart == currentLine) {
                LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();

                if (renderer == null) continue;

                String className = renderer.getClass().getName();

                String rendererString = null;

                try {
                            Field field = renderer.getClass().getDeclaredField("myKey");
                    field.setAccessible(true);
                    rendererString = field.get(renderer).toString().toLowerCase();
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }

                if (rendererString.contains("full")) {
                    speak("Covered");
                    break;
                } else if (rendererString.contains("none") || rendererString.contains("red")) {
                    playSound("notcovered");
                    speak("Not covered");
                    break;
                } else if (rendererString.contains("partial")) {
                    playSound("notcovered");
                    speak("Partial coverage");
                    break;
                }
            }
        }
    }
}
