package com.anightdazingzoroark.squirrellauncher.ui.dialogs.modDownloadDialog;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProjectDescription;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;

public final class ModDownloadProjectDescriptionPanel extends JPanel {
    private static final int MAX_DESCRIPTION_CHARACTERS = 500_000;
    private static final int MAX_DESCRIPTION_IMAGES = 8;
    private static final int MAX_DESCRIPTION_IMAGE_WIDTH = 360;
    private static final int MAX_DESCRIPTION_IMAGE_HEIGHT = 240;
    @NotNull
    private static final List<Extension> MARKDOWN_EXTENSIONS = List.of(TablesExtension.create());
    @NotNull
    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .extensions(ModDownloadProjectDescriptionPanel.MARKDOWN_EXTENSIONS)
            .build();
    @NotNull
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder()
            .extensions(ModDownloadProjectDescriptionPanel.MARKDOWN_EXTENSIONS)
            .build();
    @NotNull
    private final JPanel projectHeader = new JPanel(new BorderLayout(8, 0));
    @NotNull
    private final JLabel projectIconLabel = new JLabel();
    @NotNull
    private final JLabel projectTitleLabel = new JLabel();
    @NotNull
    private final JLabel projectAuthorLabel = new JLabel();
    @NotNull
    private final JEditorPane descriptionPane = new JEditorPane();
    @Nullable
    private URI projectPageUri;

    public ModDownloadProjectDescriptionPanel() {
        super(new BorderLayout(0, 8));
        this.setBorder(BorderFactory.createTitledBorder(Localization.text("mod.download.description")));
        this.projectIconLabel.setHorizontalAlignment(JLabel.CENTER);
        this.projectIconLabel.setVerticalAlignment(JLabel.TOP);
        this.projectIconLabel.setPreferredSize(new Dimension(56, 48));
        this.projectTitleLabel.setFont(this.projectTitleLabel.getFont().deriveFont(Font.BOLD, 18f));
        this.projectTitleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(@NotNull MouseEvent event) {
                if (ModDownloadProjectDescriptionPanel.this.projectPageUri != null) {
                    ModDownloadProjectDescriptionPanel.this.openLink(
                            ModDownloadProjectDescriptionPanel.this.projectPageUri
                    );
                }
            }
        });
        JPanel projectText = new JPanel(new BorderLayout(0, 4));
        projectText.add(this.projectTitleLabel, BorderLayout.CENTER);
        projectText.add(this.projectAuthorLabel, BorderLayout.SOUTH);
        this.projectHeader.add(this.projectIconLabel, BorderLayout.WEST);
        this.projectHeader.add(projectText, BorderLayout.CENTER);
        this.projectHeader.setVisible(false);
        this.add(this.projectHeader, BorderLayout.NORTH);

        this.descriptionPane.setEditorKit(new DescriptionHtmlEditorKit());
        this.descriptionPane.setEditable(false);
        this.descriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        this.descriptionPane.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED || event.getURL() == null) return;
            try {
                this.openLink(event.getURL().toURI());
            }
            catch (Exception exception) {
                JOptionPane.showMessageDialog(
                        this,
                        exception.getMessage(),
                        Localization.text("mod.download.error.open_link"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
        this.add(new JScrollPane(this.descriptionPane), BorderLayout.CENTER);
    }

    public void showProject(
            @Nullable ModDownloadProject project,
            @Nullable ModDownloadProjectDescription description
    ) {
        this.projectPageUri = null;
        this.projectIconLabel.setIcon(null);
        if (project == null) {
            this.projectTitleLabel.setText("");
            this.projectAuthorLabel.setText("");
            this.projectHeader.setVisible(false);
            this.showRenderedDescription("");
            return;
        }
        String pageUrl = project.pageUrl();
        if (pageUrl != null) {
            try {
                URI pageUri = URI.create(pageUrl);
                String scheme = pageUri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    this.projectPageUri = pageUri;
                }
            }
            catch (IllegalArgumentException ignored) {}
        }
        this.projectTitleLabel.setText(
                this.projectPageUri == null
                ? project.name()
                : "<html><a href=\"" + ModDownloadProjectDescriptionPanel.escapeHtml(
                        this.projectPageUri.toASCIIString()
                ) + "\">" + ModDownloadProjectDescriptionPanel.escapeHtml(project.name()) + "</a></html>"
        );
        this.projectTitleLabel.setCursor(this.projectPageUri == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.projectTitleLabel.setToolTipText(this.projectPageUri == null ? null : this.projectPageUri.toASCIIString());
        this.projectAuthorLabel.setText(project.author().isBlank() ? "" : Localization.text("mod.download.by", project.author()));
        this.projectHeader.setVisible(true);
        this.showRenderedDescription(this.renderProjectDescription(project, description));
    }

    public void setProjectIcon(@Nullable Icon icon) {
        this.projectIconLabel.setIcon(icon);
    }

    @NotNull
    public String renderProjectDescription(@NotNull ModDownloadProject project, @Nullable ModDownloadProjectDescription description) {
        String renderedDescription;
        if (description == null) {
            renderedDescription = "<p>" + ModDownloadProjectDescriptionPanel.escapeHtml(project.description())
                    + "</p><p><i>"
                    + ModDownloadProjectDescriptionPanel.escapeHtml(
                            Localization.text("mod.download.status.loading_description")
                    ) + "</i></p>";
        }
        else {
            String descriptionContent = description.content().length()
                    > ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_CHARACTERS
                    ? description.content().substring(
                            0,
                            ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_CHARACTERS
                    ) : description.content();
            renderedDescription = description.html()
                    ? descriptionContent
                    : ModDownloadProjectDescriptionPanel.MARKDOWN_RENDERER.render(
                            ModDownloadProjectDescriptionPanel.MARKDOWN_PARSER.parse(descriptionContent)
                    );
        }

        String baseUrl = project.pageUrl() == null ? "" : project.pageUrl();
        Document source = Jsoup.parseBodyFragment(renderedDescription, baseUrl);
        for (Element link : source.select("a[href]")) {
            String target = link.absUrl("href");
            String lowerTarget = target.toLowerCase(java.util.Locale.ROOT);
            if (lowerTarget.startsWith("https://") || lowerTarget.startsWith("http://")) link.attr("href", target);
            else link.unwrap();
        }
        int retainedImages = 0;
        for (Element image : source.select("img[src]")) {
            String imageUrl = image.absUrl("src");
            if (!imageUrl.toLowerCase(java.util.Locale.ROOT).startsWith("https://")
                    || retainedImages >= ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_IMAGES
            ) {
                image.remove();
                continue;
            }
            image.attr("src", imageUrl);
            image.removeAttr("srcset");
            image.removeAttr("sizes");
            retainedImages++;
        }
        for (Element table : source.select("table")) {
            table.attr("border", "1");
            table.attr("cellpadding", "4");
            table.attr("cellspacing", "0");
        }
        Safelist safelist = Safelist.relaxed()
                .addTags("hr", "s", "del")
                .addAttributes("img", "width", "height", "align")
                .addAttributes("table", "border", "cellpadding", "cellspacing")
                .addAttributes("th", "align")
                .addAttributes("td", "align")
                .removeProtocols("a", "href", "ftp", "mailto")
                .removeProtocols("img", "src", "http");
        Document cleaned = new Cleaner(safelist).clean(source);
        return "<html><head><style>body { margin: 8px 0 0 0; } "
                + "table { border-collapse: collapse; margin-top: 6px; margin-bottom: 6px; } "
                + "th, td { padding: 4px; }</style></head><body>"
                + cleaned.body().html() + "</body></html>";
    }

    public void showRenderedDescription(@NotNull String html) {
        this.descriptionPane.setText(html);
        this.descriptionPane.setCaretPosition(0);
    }

    public void openLink(@NotNull URI uri) {
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return;
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return;
        try {
            Desktop.getDesktop().browse(uri);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    Localization.text("mod.download.error.open_link"),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    @NotNull
    public static String escapeHtml(@NotNull String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final class DescriptionHtmlEditorKit extends HTMLEditorKit {
        @NotNull
        private final ViewFactory descriptionViewFactory = new HTMLFactory() {
            @Override
            @NotNull
            public View create(@NotNull javax.swing.text.Element element) {
                Object elementName = element.getAttributes().getAttribute(StyleConstants.NameAttribute);
                return elementName == HTML.Tag.IMG ? new ScaledImageView(element) : super.create(element);
            }
        };

        private DescriptionHtmlEditorKit() {}

        @Override
        @NotNull
        public ViewFactory getViewFactory() {
            return this.descriptionViewFactory;
        }
    }

    private static final class ScaledImageView extends ImageView {
        private ScaledImageView(@NotNull javax.swing.text.Element element) {
            super(element);
        }

        @Override
        public float getPreferredSpan(int axis) {
            float naturalWidth = super.getPreferredSpan(View.X_AXIS);
            float naturalHeight = super.getPreferredSpan(View.Y_AXIS);
            if (naturalWidth <= 0f || naturalHeight <= 0f) {
                return axis == View.X_AXIS ? naturalWidth : naturalHeight;
            }
            Container container = this.getContainer();
            int containerWidth = container == null ? 0 : container.getWidth();
            int availableWidth = containerWidth <= 0
                    ? ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_IMAGE_WIDTH
                    : Math.max(1, containerWidth - 16);
            int maximumWidth = Math.min(
                    ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_IMAGE_WIDTH,
                    availableWidth
            );
            float widthScale = maximumWidth / naturalWidth;
            float heightScale = ModDownloadProjectDescriptionPanel.MAX_DESCRIPTION_IMAGE_HEIGHT / naturalHeight;
            float scale = Math.min(1f, Math.min(widthScale, heightScale));
            return axis == View.X_AXIS ? Math.max(1f, naturalWidth * scale) : Math.max(1f, naturalHeight * scale);
        }

        @Override
        public void paint(@NotNull Graphics graphics, @NotNull Shape allocation) {
            Image image = this.getImage();
            Container container = this.getContainer();
            if (image == null || image.getWidth(container) <= 0 || image.getHeight(container) <= 0) {
                super.paint(graphics, allocation);
                return;
            }
            Rectangle bounds = allocation instanceof Rectangle rectangle ? rectangle : allocation.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0) return;
            int imageWidth = Math.clamp(Math.round(this.getPreferredSpan(View.X_AXIS)), 1, bounds.width);
            int imageHeight = Math.clamp(Math.round(this.getPreferredSpan(View.Y_AXIS)), 1, bounds.height);
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                drawing.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawing.drawImage(image, bounds.x, bounds.y, imageWidth, imageHeight, container);
            }
            finally {
                drawing.dispose();
            }
        }
    }
}
