package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ActivityTab extends JPanel {
    private static final int MAX_ACTIVITY_CHARACTERS = 250_000;
    @NotNull
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @NotNull
    private final JTextArea activityArea = new JTextArea();
    @NotNull
    private final Map<String, StringBuilder> instanceActivityLogs = new HashMap<>();
    @Nullable
    private String displayedInstanceId;

    public ActivityTab() {
        super(new BorderLayout());
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        this.activityArea.setEditable(false);
        this.activityArea.setLineWrap(true);
        this.activityArea.setWrapStyleWord(true);
        this.activityArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        this.add(new JScrollPane(this.activityArea), BorderLayout.CENTER);
    }

    public void showInstance(@NotNull String instanceId) {
        this.displayedInstanceId = instanceId;
        StringBuilder activityLog = this.instanceActivityLogs.get(instanceId);
        this.activityArea.setText(activityLog == null ? "" : activityLog.toString());
        this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
    }

    public void clearDisplayedInstance() {
        this.displayedInstanceId = null;
        this.activityArea.setText("");
    }

    public void append(@NotNull String instanceId, @NotNull String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> this.append(instanceId, message));
            return;
        }

        String line = "[" + TIME_FORMAT.format(LocalTime.now()) + "] " + message + System.lineSeparator();
        StringBuilder activityLog = this.instanceActivityLogs.computeIfAbsent(instanceId, ignored -> new StringBuilder());
        activityLog.append(line);
        int extra = activityLog.length() - MAX_ACTIVITY_CHARACTERS;
        if (extra > 0) activityLog.delete(0, extra);

        if (instanceId.equals(this.displayedInstanceId)) {
            this.activityArea.append(line);
            extra = this.activityArea.getDocument().getLength() - MAX_ACTIVITY_CHARACTERS;
            if (extra > 0) this.activityArea.replaceRange("", 0, extra);
            this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
        }
    }

    public void moveLog(@NotNull String oldInstanceId, @NotNull String newInstanceId) {
        StringBuilder activityLog = this.instanceActivityLogs.remove(oldInstanceId);
        if (activityLog != null) this.instanceActivityLogs.put(newInstanceId, activityLog);
        if (oldInstanceId.equals(this.displayedInstanceId)) this.displayedInstanceId = newInstanceId;
    }

    public void removeLog(@NotNull String instanceId) {
        this.instanceActivityLogs.remove(instanceId);
        if (instanceId.equals(this.displayedInstanceId)) this.clearDisplayedInstance();
    }

    public void retainLogs(@NotNull Set<String> instanceIds) {
        this.instanceActivityLogs.keySet().removeIf(id -> !instanceIds.contains(id));
        if (this.displayedInstanceId != null && !instanceIds.contains(this.displayedInstanceId)) {
            this.clearDisplayedInstance();
        }
    }
}
