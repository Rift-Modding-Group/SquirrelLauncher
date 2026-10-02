package com.anightdazingzoroark.squirrellauncher.ui.custom;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

//a variant of JTextArea with placeholder text if none exists
public class JTextAreaPlaceholderable extends JTextArea {
    @NotNull
    private final String placeholderText;

    public JTextAreaPlaceholderable(@NotNull String placeholderText) {
        super();
        this.placeholderText = placeholderText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!this.getText().isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.GRAY);
        g2.setFont(UIManager.getFont("TextArea.font"));
        int x = this.getInsets().left + 2;
        int y = this.getInsets().top + g2.getFontMetrics().getAscent();
        g2.drawString(this.placeholderText, x, y);
        g2.dispose();
    }
}
