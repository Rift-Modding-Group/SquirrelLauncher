package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.io.Serial;

/**
 * Shared base and entry points for the launcher's modal dialogs.
 * */
public abstract class AbstractDialog<T> extends JDialog {
    @Serial
    private static final long serialVersionUID = 1L;
    @Nullable
    private T result;

    protected AbstractDialog(@NotNull JFrame owner, @NotNull String title) {
        super(owner, title, true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.getRootPane().registerKeyboardAction(
                e -> this.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    //----methods common to the dialogs----
    protected final void complete(@NotNull T result) {
        this.result = result;
        this.dispose();
    }

    protected final void resizeToContent() {
        this.pack();
    }

    protected final void addRow(@NotNull JPanel panel, int row, @NotNull JLabel label, @NotNull Component component) {
        this.addRow(panel, row, label, component, GridBagConstraints.LINE_START, 5);
    }

    protected final void addLoaderVersionRow(
            @NotNull JPanel panel,
            int row,
            @NotNull JLabel label,
            @NotNull LoaderVersionComboBox component
    ) {
        int labelTopInset = 5 + Math.max(
                0,
                (component.dropdownPreferredHeight() - label.getPreferredSize().height) / 2
        );
        this.addRow(panel, row, label, component, GridBagConstraints.FIRST_LINE_START, labelTopInset);
    }

    private void addRow(
            @NotNull JPanel panel,
            int row,
            @NotNull JLabel label,
            @NotNull Component component,
            int labelAnchor,
            int labelTopInset
    ) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = labelAnchor;
        left.insets = new Insets(labelTopInset, 0, 5, 12);
        panel.add(label, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 0, 5, 0);
        panel.add(component, right);
    }

    @Nullable
    public T showModal() {
        this.setVisible(true);
        return this.result;
    }

    protected record FieldListener(@NotNull Runnable callback) implements DocumentListener {
        @Override
        public void insertUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }

        @Override
        public void removeUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }

        @Override
        public void changedUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }
    }
}
