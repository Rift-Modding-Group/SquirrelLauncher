package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

//special dialog for linux users
public final class LinuxJavaReminderDialog extends AbstractDialog<LinuxJavaReminderDialog.Result> {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotNull
    private final JCheckBox hideReminder;

    public LinuxJavaReminderDialog(@NotNull Window owner) {
        super(owner, Localization.text("settings.java.linux_reminder.title"));
        this.hideReminder = new JCheckBox(Localization.text("settings.java.linux_reminder.hide"));

        this.setLayout(new BorderLayout(0, 12));
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        this.addPackageManagerCommand(content);
        GridBagConstraints checkbox = new GridBagConstraints();
        checkbox.gridx = 0;
        checkbox.gridy = 2;
        checkbox.weightx = 1;
        checkbox.anchor = GridBagConstraints.LINE_START;
        content.add(this.hideReminder, checkbox);
        this.add(content, BorderLayout.CENTER);

        JButton okButton = new JButton(Localization.text("settings.java.linux_reminder.ok"));
        okButton.addActionListener(event -> this.closeDialog());
        JButton vendorButton = new JButton(Localization.text("settings.java.linux_reminder.vendor"));
        vendorButton.addActionListener(event -> this.complete(new Result(true, this.hideReminder.isSelected())));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(okButton);
        actions.add(vendorButton);
        this.add(actions, BorderLayout.SOUTH);

        this.getRootPane().setDefaultButton(vendorButton);
        this.resizeToContent();
        this.setLocationRelativeTo(owner);
    }

    private void addPackageManagerCommand(@NotNull JPanel content) {
        String distributionName = "Linux";
        String distributionId = "";
        String distributionLike = "";
        Path osRelease = Paths.get("/etc/os-release");
        if (Files.isRegularFile(osRelease)) {
            try {
                for (String line : Files.readAllLines(osRelease)) {
                    if (line.startsWith("NAME=")) {
                        distributionName = line.substring(5).replace("\"", "").trim();
                    }
                    else if (line.startsWith("ID=")) {
                        distributionId = line.substring(3).replace("\"", "").trim().toLowerCase(Locale.ROOT);
                    }
                    else if (line.startsWith("ID_LIKE=")) {
                        distributionLike = line.substring(8).replace("\"", "").trim().toLowerCase(Locale.ROOT);
                    }
                }
            }
            catch (java.io.IOException | SecurityException ignored) {}
        }

        String packageManager = null;
        String command = null;
        int repositoryJavaVersion = 0;
        int vendorJavaVersion = 0;
        String pathVariable = System.getenv("PATH");
        if (pathVariable != null && !pathVariable.isBlank()) {
            String[] managers = {"apt", "dnf", "xbps-install", "pacman"};
            managerSearch:
            for (String manager : managers) {
                for (String directory : pathVariable.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                    if (directory.isBlank()) continue;
                    try {
                        if (!Files.isExecutable(Paths.get(directory).resolve(manager))) continue;
                        packageManager = manager;
                        break managerSearch;
                    }
                    catch (java.nio.file.InvalidPathException ignored) {}
                }
            }
        }
        if (packageManager != null) {
            switch (packageManager) {
                case "apt" -> {
                    if (!distributionId.equals("ubuntu")
                            && !distributionLike.contains("ubuntu")
                            && (distributionId.equals("debian") || distributionLike.contains("debian"))
                    ) {
                        command = "sudo apt install openjdk-25-jre";
                        repositoryJavaVersion = 25;
                        vendorJavaVersion = 8;
                    }
                    //mint forever
                    else {
                        command = "sudo apt install openjdk-8-jre openjdk-25-jre";
                    }
                }
                case "dnf" -> command = "sudo dnf install java-1.8.0-openjdk java-25-openjdk";
                case "xbps-install" -> command = "sudo xbps-install -S openjdk8-jre openjdk25-jre";
                //fuck yay, me and my homies hate yay
                case "pacman" -> command = "sudo pacman -S jre8-openjdk jre25-openjdk";
                default -> {}
            }
        }

        GridBagConstraints introduction = new GridBagConstraints();
        introduction.gridx = 0;
        introduction.gridy = 0;
        introduction.weightx = 1;
        introduction.fill = GridBagConstraints.HORIZONTAL;
        introduction.anchor = GridBagConstraints.LINE_START;
        introduction.insets = new Insets(0, 0, 10, 0);
        String htmlStart = "<html><body style=\"width: 520px\">";
        String htmlEnd = "</body></html>";
        String escapedDistributionName = distributionName
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        content.add(new JLabel(htmlStart + Localization.text(
                "settings.java.linux_reminder.introduction",
                escapedDistributionName
        ) + htmlEnd), introduction);

        String instructionText;
        if (command == null) {
            instructionText = Localization.text("settings.java.linux_reminder.unsupported");
        }
        else if (repositoryJavaVersion == 0) {
            instructionText = Localization.text("settings.java.linux_reminder.both", command);
        }
        else {
            instructionText = Localization.text(
                    "settings.java.linux_reminder.limited",
                    escapedDistributionName,
                    vendorJavaVersion,
                    repositoryJavaVersion,
                    command
            );
        }
        GridBagConstraints instruction = new GridBagConstraints();
        instruction.gridx = 0;
        instruction.gridy = 1;
        instruction.weightx = 1;
        instruction.fill = GridBagConstraints.HORIZONTAL;
        instruction.anchor = GridBagConstraints.LINE_START;
        instruction.insets = new Insets(0, 0, 12, 0);
        content.add(new JLabel(htmlStart + instructionText + htmlEnd), instruction);
    }

    @Override
    protected void closeDialog() {
        this.complete(new Result(false, this.hideReminder.isSelected()));
    }

    public record Result(boolean downloadFromVendor, boolean hideReminder) {}
}
