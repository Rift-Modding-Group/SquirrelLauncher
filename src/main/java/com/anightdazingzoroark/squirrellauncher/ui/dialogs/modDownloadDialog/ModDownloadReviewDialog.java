package com.anightdazingzoroark.squirrellauncher.ui.dialogs.modDownloadDialog;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModDownloadReviewDialog extends JDialog {
    @NotNull
    private final Map<String, JCheckBox> fileSelections = new LinkedHashMap<>();
    @NotNull
    private final List<JCheckBox> dependencySelections = new ArrayList<>();
    private boolean confirmed;

    public ModDownloadReviewDialog(
            @NotNull Window owner,
            @NotNull List<ModDownloadFile> selectedFiles,
            @NotNull List<ModDownloadFile> dependencies
    ) {
        super(owner, Localization.text("mod.download.review.title"), ModalityType.APPLICATION_MODAL);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setResizable(false);
        this.setLayout(new BorderLayout(0, 12));
        this.getRootPane().registerKeyboardAction(
                event -> this.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        JPanel headingPanel = new JPanel(new BorderLayout(0, 6));
        headingPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        JLabel heading = new JLabel(Localization.text("mod.download.review.heading"));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        headingPanel.add(heading, BorderLayout.NORTH);
        headingPanel.add(new JLabel(Localization.text(
                "mod.download.review.summary",
                selectedFiles.size(),
                dependencies.size()
        )), BorderLayout.CENTER);
        this.add(headingPanel, BorderLayout.NORTH);

        JPanel filePanel = new JPanel();
        filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
        filePanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        this.addFiles(filePanel, selectedFiles, false);
        this.addFiles(filePanel, dependencies, true);
        JScrollPane fileScroll = new JScrollPane(filePanel);
        fileScroll.setPreferredSize(new Dimension(700, Math.min(420, 90 + this.fileSelections.size() * 34)));
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        centerPanel.add(fileScroll, BorderLayout.CENTER);
        this.add(centerPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        JButton cancelButton = new JButton(Localization.text("mod.download.button.close"));
        cancelButton.addActionListener(event -> this.dispose());
        actions.add(cancelButton);
        JButton confirmButton = new JButton(Localization.text("mod.download.review.confirm"));
        confirmButton.addActionListener(event -> {
            this.confirmed = true;
            this.dispose();
        });
        actions.add(confirmButton);
        this.getRootPane().setDefaultButton(confirmButton);
        this.add(actions, BorderLayout.SOUTH);
        this.pack();
        this.setLocationRelativeTo(owner);
    }

    public boolean showModal() {
        this.setVisible(true);
        return this.confirmed;
    }

    public boolean isSelected(@NotNull ModDownloadFile file) {
        JCheckBox selection = this.fileSelections.get(ModDownloadReviewDialog.fileKey(file));
        return selection != null && selection.isSelected();
    }

    private void addFiles(@NotNull JPanel filePanel, @NotNull List<ModDownloadFile> files, boolean dependency) {
        for (ModDownloadFile file : files) {
            String version = file.versionName().isBlank() ? file.fileName() : file.versionName();
            String provider = Localization.text("mod.download.provider." + file.platform().name().toLowerCase());
            String label = dependency
                    ? Localization.text("mod.download.review.dependency", file.projectName(), version, provider)
                    : Localization.text("mod.download.review.selected", file.projectName(), version, provider);
            JCheckBox selection = new JCheckBox(label, true);
            selection.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
            this.fileSelections.put(ModDownloadReviewDialog.fileKey(file), selection);
            if (dependency) this.dependencySelections.add(selection);
            filePanel.add(selection);
        }
    }

    @NotNull
    private static String fileKey(@NotNull ModDownloadFile file) {
        return file.platform().name() + ':' + file.projectId() + ':' + file.providerFileId();
    }
}
