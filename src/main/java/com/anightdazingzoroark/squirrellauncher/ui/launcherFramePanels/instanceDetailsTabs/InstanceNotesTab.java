package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InstanceNotesTab extends JPanel {
    @NotNull
    private final JTextArea notesArea = new JTextArea();
    @NotNull
    private final Timer saveTimer;
    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final Map<String, String> queuedNotes = new LinkedHashMap<>();
    @NotNull
    private String savedNotes = "";
    @Nullable
    private String displayedInstanceId;
    private boolean controlsAvailable;
    private boolean updatingText;
    private boolean saveInProgress;

    public InstanceNotesTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.saveTimer = new Timer(750, event -> {
            if (this.displayedInstanceId == null) return;
            String notes = this.notesArea.getText();
            if (notes.equals(this.savedNotes)) return;
            this.queueSave(this.displayedInstanceId, notes);
        });
        this.saveTimer.setRepeats(false);
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        this.notesArea.setLineWrap(true);
        this.notesArea.setWrapStyleWord(true);
        this.notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(@NotNull DocumentEvent event) {
                if (!InstanceNotesTab.this.updatingText) InstanceNotesTab.this.saveTimer.restart();
            }

            @Override
            public void removeUpdate(@NotNull DocumentEvent event) {
                if (!InstanceNotesTab.this.updatingText) InstanceNotesTab.this.saveTimer.restart();
            }

            @Override
            public void changedUpdate(@NotNull DocumentEvent event) {
                if (!InstanceNotesTab.this.updatingText) InstanceNotesTab.this.saveTimer.restart();
            }
        });
        this.notesArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(@NotNull FocusEvent event) {
                if (InstanceNotesTab.this.displayedInstanceId == null
                        || InstanceNotesTab.this.notesArea.getText().equals(InstanceNotesTab.this.savedNotes)) return;
                InstanceNotesTab.this.saveTimer.stop();
                InstanceNotesTab.this.queueSave(
                        InstanceNotesTab.this.displayedInstanceId,
                        InstanceNotesTab.this.notesArea.getText()
                );
            }
        });
        this.add(new JScrollPane(this.notesArea), BorderLayout.CENTER);
        this.clearDisplayedInstance();
    }

    public void showInstance(@NotNull MinecraftInstance instance) {
        if (instance.id().equals(this.displayedInstanceId)
                && !this.notesArea.getText().equals(this.savedNotes)) return;
        if (this.displayedInstanceId != null
                && !this.notesArea.getText().equals(this.savedNotes)) {
            this.saveTimer.stop();
            this.queueSave(this.displayedInstanceId, this.notesArea.getText());
        }
        this.updatingText = true;
        this.displayedInstanceId = instance.id();
        this.savedNotes = instance.notes();
        this.notesArea.setText(this.savedNotes);
        this.notesArea.setCaretPosition(0);
        this.updatingText = false;
        this.updateControlState();
    }

    public void clearDisplayedInstance() {
        this.saveTimer.stop();
        this.updatingText = true;
        this.displayedInstanceId = null;
        this.controlsAvailable = false;
        this.savedNotes = "";
        this.notesArea.setText("");
        this.updatingText = false;
        this.updateControlState();
    }

    public void notesSaveFinished(@NotNull String instanceId, @NotNull String notes, boolean saved) {
        this.saveInProgress = false;
        if (saved && instanceId.equals(this.displayedInstanceId)) this.savedNotes = notes;
        if (saved && this.displayedInstanceId != null && !this.notesArea.getText().equals(this.savedNotes)) {
            this.queuedNotes.put(this.displayedInstanceId, this.notesArea.getText());
        }
        this.startNextSave();
    }

    public void updateControlState(boolean available) {
        this.controlsAvailable = available && this.displayedInstanceId != null;
        this.updateControlState();
    }

    private void updateControlState() {
        this.notesArea.setEnabled(this.controlsAvailable);
    }

    private void queueSave(@NotNull String instanceId, @NotNull String notes) {
        this.queuedNotes.put(instanceId, notes);
        this.startNextSave();
    }

    private void startNextSave() {
        if (this.saveInProgress || this.queuedNotes.isEmpty()) return;
        Map.Entry<String, String> pendingSave = this.queuedNotes.entrySet().iterator().next();
        String instanceId = pendingSave.getKey();
        String notes = pendingSave.getValue();
        this.queuedNotes.remove(instanceId);
        this.saveInProgress = true;
        this.launcherActions.saveInstanceNotesRequested(instanceId, notes);
    }
}
