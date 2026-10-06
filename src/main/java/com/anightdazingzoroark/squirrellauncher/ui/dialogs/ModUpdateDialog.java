package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ModUpdateDialog extends AbstractDialog<Void> {
    public ModUpdateDialog(@NotNull JFrame owner, @NotNull ManagedMod managedMod, @NotNull List<ModDownloadFile> downloadFile) {
        super(owner, Localization.text("instance.mods.dialog.update"));
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setLayout(new BorderLayout(0, 12));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));

        GridBagConstraints introduction = new GridBagConstraints();
        introduction.gridx = 0;
        introduction.gridy = 0;
        introduction.weightx = 1;
        introduction.fill = GridBagConstraints.HORIZONTAL;
        introduction.anchor = GridBagConstraints.LINE_START;
        introduction.insets = new Insets(0, 0, 10, 0);
        content.add(new JLabel("An update is available for the selected mod!"), introduction);

        System.out.println("downloadFile: "+downloadFile);

        this.add(content, BorderLayout.CENTER);

        this.setMinimumSize(new Dimension(680, 320));
        this.resizeToContent();
        this.setSize(this.getMinimumSize().width, this.getHeight());
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
        this.setVisible(true);
    }
}
