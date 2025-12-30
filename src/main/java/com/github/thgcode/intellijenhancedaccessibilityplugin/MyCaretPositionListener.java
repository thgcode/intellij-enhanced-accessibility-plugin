package com.github.thgcode.intellijenhancedaccessibilityplugin;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
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
    private AccessibleAnnouncer accessibleAnnouncer = JBR.getAccessibleAnnouncer();
    private int lastReportedLine;

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        CaretListener.super.caretPositionChanged(event);
        checkLineForProblems(event);
    }

    private void checkLineForProblems(CaretEvent event) {
        int line = event.getNewPosition().line;

        if (line == lastReportedLine) {
            return;
        }

        lastReportedLine = line;

        Editor editor = event.getEditor();

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

        accessibleAnnouncer.announce(null, info.getDescription(), AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
    }
}
