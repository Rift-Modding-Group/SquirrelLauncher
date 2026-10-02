package com.anightdazingzoroark.squirrellauncher.ui.settingsControls;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.launcher.JvmArguments;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

public final class AdvancedGameSettingsControls extends JPanel {
    @Nullable
    private final JCheckBox useDefaultJvmCheckBox;
    @NotNull
    private final JTextArea jvmArgumentsArea = new JTextArea(12, 60);
    @NotNull
    private List<String> savedJvmArguments = GameSettings.defaults().jvmArguments();
    @NotNull
    private Consumer<List<String>> saveListener = arguments -> {};
    @NotNull
    private Consumer<Boolean> useDefaultsListener = useDefaults -> {};
    @NotNull
    private Consumer<Boolean> actionsAvailableListener = available -> {};
    private boolean controlsAvailable = true;

    public AdvancedGameSettingsControls(boolean showDefaultSelector) {
        super(new BorderLayout(0, 10));
        this.useDefaultJvmCheckBox = showDefaultSelector
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTextArea instructions = new JTextArea(
                Localization.text("settings.game.jvm_arguments.instructions"),
                2,
                60
        );
        instructions.setEditable(false);
        instructions.setFocusable(false);
        instructions.setOpaque(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JPanel heading = new JPanel(new BorderLayout(0, 2));
        heading.add(instructions, BorderLayout.CENTER);
        if (this.useDefaultJvmCheckBox != null) heading.add(this.useDefaultJvmCheckBox, BorderLayout.SOUTH);
        this.add(heading, BorderLayout.NORTH);
        this.jvmArgumentsArea.setLineWrap(true);
        this.jvmArgumentsArea.setWrapStyleWord(true);
        this.jvmArgumentsArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        this.jvmArgumentsArea.setFont(new Font(
                Font.MONOSPACED,
                Font.PLAIN,
                this.jvmArgumentsArea.getFont().getSize()
        ));
        JScrollPane argumentsScrollPane = new JScrollPane(this.jvmArgumentsArea);
        argumentsScrollPane.setBorder(BorderFactory.createTitledBorder(
                Localization.text("settings.game.jvm_arguments.heading")
        ));
        this.add(argumentsScrollPane, BorderLayout.CENTER);

        this.jvmArgumentsArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(@NotNull DocumentEvent event) {
                AdvancedGameSettingsControls.this.updateControlState();
            }

            @Override
            public void removeUpdate(@NotNull DocumentEvent event) {
                AdvancedGameSettingsControls.this.updateControlState();
            }

            @Override
            public void changedUpdate(@NotNull DocumentEvent event) {
                AdvancedGameSettingsControls.this.updateControlState();
            }
        });
        if (this.useDefaultJvmCheckBox != null) {
            this.useDefaultJvmCheckBox.addActionListener(event -> {
                if (this.useDefaultJvmCheckBox.isSelected() && !this.confirmDiscardUnsavedChanges()) {
                    this.useDefaultJvmCheckBox.setSelected(false);
                    return;
                }
                this.updateControlState();
                this.useDefaultsListener.accept(this.useDefaultJvmCheckBox.isSelected());
            });
        }
        this.showArguments(this.savedJvmArguments);
    }

    public void setSaveListener(@NotNull Consumer<List<String>> saveListener) {
        this.saveListener = saveListener;
    }

    public void setUseDefaultsListener(@NotNull Consumer<Boolean> useDefaultsListener) {
        this.useDefaultsListener = useDefaultsListener;
    }

    public void setActionsAvailableListener(@NotNull Consumer<Boolean> actionsAvailableListener) {
        this.actionsAvailableListener = actionsAvailableListener;
        this.updateControlState();
    }

    public void showArguments(@NotNull List<String> arguments) {
        this.savedJvmArguments = List.copyOf(arguments);
        this.jvmArgumentsArea.setText(JvmArguments.format(this.savedJvmArguments));
        this.jvmArgumentsArea.setCaretPosition(0);
        this.updateControlState();
    }

    public void setAvailable(boolean available) {
        this.controlsAvailable = available;
        this.updateControlState();
    }

    public void setUseDefaults(boolean useDefaults) {
        if (this.useDefaultJvmCheckBox == null) {
            if (useDefaults) throw new IllegalStateException("Global settings cannot use inherited JVM arguments.");
            return;
        }
        this.useDefaultJvmCheckBox.setSelected(useDefaults);
        this.updateControlState();
    }

    public boolean confirmDiscardUnsavedChanges() {
        if (!this.hasUnsavedChanges()) return true;
        int result = JOptionPane.showConfirmDialog(
                this,
                Localization.text("settings.game.jvm_arguments.unsaved"),
                Localization.text("settings.game.jvm_arguments.unsaved_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) return false;
        this.cancelChanges();
        return true;
    }

    public void saveChanges() {
        List<String> arguments;
        try {
            arguments = JvmArguments.parse(this.jvmArgumentsArea.getText());
            int maximumMemoryGigabytes = JvmArguments.maximumMemoryGigabytes(arguments);
            int minimumMemoryMegabytes = JvmArguments.minimumMemoryMegabytes(arguments);
            if (maximumMemoryGigabytes < GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES
                    || maximumMemoryGigabytes > GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES) {
                throw new IllegalArgumentException(
                        "Maximum memory must be between 1 and "
                                + GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES + " GB."
                );
            }
            if (minimumMemoryMegabytes > (long) maximumMemoryGigabytes * 1024L) {
                throw new IllegalArgumentException("Initial JVM memory cannot exceed maximum JVM memory.");
            }
        }
        catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    Localization.text("settings.game.jvm_arguments.invalid_title"),
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        this.showArguments(arguments);
        this.saveListener.accept(arguments);
    }

    public void cancelChanges() {
        this.jvmArgumentsArea.setText(JvmArguments.format(this.savedJvmArguments));
        this.jvmArgumentsArea.setCaretPosition(0);
        this.updateControlState();
    }

    private boolean hasUnsavedChanges() {
        return !this.jvmArgumentsArea.getText().equals(JvmArguments.format(this.savedJvmArguments));
    }

    private void updateControlState() {
        boolean unsavedChanges = this.hasUnsavedChanges();
        boolean useDefaults = this.useDefaultJvmCheckBox != null && this.useDefaultJvmCheckBox.isSelected();
        boolean argumentsAvailable = this.controlsAvailable && !useDefaults;
        if (this.useDefaultJvmCheckBox != null) this.useDefaultJvmCheckBox.setEnabled(this.controlsAvailable);
        this.jvmArgumentsArea.setEnabled(argumentsAvailable);
        this.jvmArgumentsArea.setEditable(argumentsAvailable);
        this.actionsAvailableListener.accept(argumentsAvailable && unsavedChanges);
    }
}
