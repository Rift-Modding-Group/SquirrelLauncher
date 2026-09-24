package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherLanguage;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Shared layout and behavior for panels displayed in the settings dialog.
 * */
public abstract class SquirrelLauncherSettingsPanel extends JPanel {
    protected SquirrelLauncherSettingsPanel(@NotNull LayoutManager layout) {
        super(layout);
        this.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        this.initializeSettingsPanel();
    }

    //----public static stuff for making each tab----
    @NotNull
    public static SquirrelLauncherSettingsPanel createGameSettingsTab(@NotNull LauncherService launcherService) {
        return new SquirrelLauncherSettingsPanel(new BorderLayout()) {
            private JCheckBox fullscreenCheckBox;
            private JSpinner windowWidthSpinner;
            private JSpinner windowHeightSpinner;
            private JButton saveButton;

            @Override
            protected void initializeSettingsPanel() {
                this.fullscreenCheckBox = new JCheckBox(Localization.text("settings.game.fullscreen"));
                this.windowWidthSpinner = new JSpinner(new SpinnerNumberModel(1280, 320, 7680, 1));
                this.windowHeightSpinner = new JSpinner(new SpinnerNumberModel(720, 240, 4320, 1));
                this.saveButton = new JButton(Localization.text("settings.button.save"));

                LauncherSettings settings = launcherService.settings();
                this.fullscreenCheckBox.setSelected(settings.fullscreen());
                this.windowWidthSpinner.setValue(settings.windowWidth());
                this.windowHeightSpinner.setValue(settings.windowHeight());

                JPanel form = new JPanel(new GridBagLayout());
                GridBagConstraints title = new GridBagConstraints();
                title.gridx = 0;
                title.gridy = 0;
                title.gridwidth = 2;
                title.weightx = 1;
                title.anchor = GridBagConstraints.LINE_START;
                title.insets = new Insets(0, 0, 16, 0);
                form.add(this.createHeader(), title);

                GridBagConstraints fullscreen = new GridBagConstraints();
                fullscreen.gridx = 0;
                fullscreen.gridy = 1;
                fullscreen.gridwidth = 2;
                fullscreen.anchor = GridBagConstraints.LINE_START;
                fullscreen.insets = new Insets(0, 0, 14, 0);
                form.add(this.fullscreenCheckBox, fullscreen);

                GridBagConstraints widthLabel = new GridBagConstraints();
                widthLabel.gridx = 0;
                widthLabel.gridy = 2;
                widthLabel.anchor = GridBagConstraints.LINE_START;
                widthLabel.insets = new Insets(5, 0, 5, 14);
                form.add(new JLabel(Localization.text("settings.game.width")), widthLabel);

                GridBagConstraints width = new GridBagConstraints();
                width.gridx = 1;
                width.gridy = 2;
                width.weightx = 1;
                width.anchor = GridBagConstraints.LINE_START;
                width.insets = new Insets(5, 0, 5, 0);
                this.windowWidthSpinner.setPreferredSize(new Dimension(110, 28));
                form.add(this.windowWidthSpinner, width);

                GridBagConstraints heightLabel = new GridBagConstraints();
                heightLabel.gridx = 0;
                heightLabel.gridy = 3;
                heightLabel.anchor = GridBagConstraints.LINE_START;
                heightLabel.insets = new Insets(5, 0, 5, 14);
                form.add(new JLabel(Localization.text("settings.game.height")), heightLabel);

                GridBagConstraints height = new GridBagConstraints();
                height.gridx = 1;
                height.gridy = 3;
                height.weightx = 1;
                height.anchor = GridBagConstraints.LINE_START;
                height.insets = new Insets(5, 0, 5, 0);
                this.windowHeightSpinner.setPreferredSize(new Dimension(110, 28));
                form.add(this.windowHeightSpinner, height);

                GridBagConstraints filler = new GridBagConstraints();
                filler.gridx = 0;
                filler.gridy = 4;
                filler.gridwidth = 2;
                filler.weighty = 1;
                filler.fill = GridBagConstraints.VERTICAL;
                form.add(new JPanel(), filler);
                this.add(form, BorderLayout.CENTER);

                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                actions.add(this.saveButton);
                this.add(actions, BorderLayout.SOUTH);

                this.fullscreenCheckBox.addActionListener(event -> this.updateControlState());
                this.windowWidthSpinner.addChangeListener(event -> this.updateControlState());
                this.windowHeightSpinner.addChangeListener(event -> this.updateControlState());
                this.saveButton.addActionListener(event -> {
                    try {
                        launcherService.updateSettings(new LauncherSettings(
                                this.fullscreenCheckBox.isSelected(),
                                (Integer) this.windowWidthSpinner.getValue(),
                                (Integer) this.windowHeightSpinner.getValue(),
                                launcherService.settings().language()
                        ));
                        this.updateControlState();
                    }
                    catch (Exception exception) {
                        this.showSaveError(exception);
                    }
                });
                this.updateControlState();
            }

            @Override
            @NotNull
            public String header() {
                return Localization.text("settings.game.heading");
            }

            private void updateControlState() {
                LauncherSettings savedSettings = launcherService.settings();
                boolean changed = savedSettings.fullscreen() != this.fullscreenCheckBox.isSelected()
                        || savedSettings.windowWidth() != (Integer) this.windowWidthSpinner.getValue()
                        || savedSettings.windowHeight() != (Integer) this.windowHeightSpinner.getValue();
                boolean windowed = !this.fullscreenCheckBox.isSelected();
                this.windowWidthSpinner.setEnabled(windowed);
                this.windowHeightSpinner.setEnabled(windowed);
                this.saveButton.setEnabled(changed);
            }
        };
    }

    @NotNull
    public static SquirrelLauncherSettingsPanel createLauncherSettingsTab(@NotNull LauncherService launcherService) {
        return new SquirrelLauncherSettingsPanel(new BorderLayout()) {
            private JComboBox<LauncherLanguage> languageSelector;
            private JButton saveButton;

            @Override
            protected void initializeSettingsPanel() {
                this.languageSelector = new JComboBox<>(LauncherLanguage.values());
                this.saveButton = new JButton(Localization.text("settings.button.save"));

                this.languageSelector.setSelectedItem(launcherService.settings().language());
                this.languageSelector.setPreferredSize(new Dimension(240, 28));
                this.languageSelector.setRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            JList<?> list, Object value, int index,
                            boolean selected, boolean focused
                    ) {
                        JLabel label = (JLabel) super.getListCellRendererComponent(
                                list, value, index,
                                selected, focused
                        );
                        if (value instanceof LauncherLanguage language) {
                            label.setText(Localization.text("settings.launcher.language." + language.code()));
                        }
                        return label;
                    }
                });

                JPanel form = new JPanel(new GridBagLayout());
                GridBagConstraints title = new GridBagConstraints();
                title.gridx = 0;
                title.gridy = 0;
                title.gridwidth = 2;
                title.weightx = 1;
                title.anchor = GridBagConstraints.LINE_START;
                title.insets = new Insets(0, 0, 16, 0);
                form.add(this.createHeader(), title);

                GridBagConstraints languageLabel = new GridBagConstraints();
                languageLabel.gridx = 0;
                languageLabel.gridy = 1;
                languageLabel.anchor = GridBagConstraints.LINE_START;
                languageLabel.insets = new Insets(5, 0, 5, 14);
                form.add(new JLabel(Localization.text("settings.launcher.language")), languageLabel);

                GridBagConstraints language = new GridBagConstraints();
                language.gridx = 1;
                language.gridy = 1;
                language.weightx = 1;
                language.anchor = GridBagConstraints.LINE_START;
                language.insets = new Insets(5, 0, 5, 0);
                form.add(this.languageSelector, language);

                GridBagConstraints restart = new GridBagConstraints();
                restart.gridx = 0;
                restart.gridy = 2;
                restart.gridwidth = 2;
                restart.weightx = 1;
                restart.anchor = GridBagConstraints.LINE_START;
                restart.insets = new Insets(12, 0, 0, 0);
                form.add(new JLabel(Localization.text("settings.launcher.language_restart")), restart);

                GridBagConstraints filler = new GridBagConstraints();
                filler.gridx = 0;
                filler.gridy = 3;
                filler.gridwidth = 2;
                filler.weighty = 1;
                filler.fill = GridBagConstraints.VERTICAL;
                form.add(new JPanel(), filler);
                this.add(form, BorderLayout.CENTER);

                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                actions.add(this.saveButton);
                this.add(actions, BorderLayout.SOUTH);

                this.languageSelector.addActionListener(event -> this.updateControlState());
                this.saveButton.addActionListener(event -> {
                    LauncherLanguage selectedLanguage = (LauncherLanguage) this.languageSelector.getSelectedItem();
                    if (selectedLanguage == null) return;
                    LauncherSettings settings = launcherService.settings();
                    try {
                        launcherService.updateSettings(new LauncherSettings(
                                settings.fullscreen(),
                                settings.windowWidth(),
                                settings.windowHeight(),
                                selectedLanguage
                        ));
                        this.updateControlState();
                    }
                    catch (Exception exception) {
                        this.showSaveError(exception);
                    }
                });
                this.updateControlState();
            }

            @Override
            @NotNull
            public String header() {
                return Localization.text("settings.launcher.heading");
            }

            private void updateControlState() {
                this.saveButton.setEnabled(
                        this.languageSelector.getSelectedItem() != launcherService.settings().language()
                );
            }
        };
    }

    @NotNull
    public static SquirrelLauncherSettingsPanel createAccountSettingsTab(@NotNull LauncherService launcherService, @NotNull Consumer<Boolean> busyListener) {
        return new SquirrelLauncherSettingsPanel(new BorderLayout(0, 10)) {
            private DefaultListModel<MinecraftAccount> accountModel;
            private JList<MinecraftAccount> accountList;
            private JButton addMicrosoftButton;
            private JButton cancelMicrosoftButton;
            private JButton addOfflineButton;
            private JButton removeButton;
            private JButton useButton;
            private JLabel statusLabel;
            private JLabel verificationLabel;
            private JTextField codeField;
            private JProgressBar progressBar;
            private SwingWorker<MinecraftAccount, Void> microsoftWorker;
            private boolean busy;

            @Override
            protected void initializeSettingsPanel() {
                this.accountModel = new DefaultListModel<>();
                this.accountList = new JList<>(this.accountModel);
                this.addMicrosoftButton = new JButton(Localization.text("accounts.button.add_microsoft"));
                this.cancelMicrosoftButton = new JButton(Localization.text("accounts.button.cancel_sign_in"));
                this.addOfflineButton = new JButton(Localization.text("accounts.button.add_offline"));
                this.removeButton = new JButton(Localization.text("accounts.button.remove"));
                this.useButton = new JButton(Localization.text("accounts.button.use"));
                this.statusLabel = new JLabel(" ");
                this.verificationLabel = new JLabel(" ");
                this.codeField = new JTextField(12);
                this.progressBar = new JProgressBar();

                JPanel headingPanel = new JPanel(new BorderLayout(0, 5));
                headingPanel.add(this.createHeader(), BorderLayout.NORTH);
                headingPanel.add(new JLabel(launcherService.accounts().isEmpty()
                        ? Localization.text("accounts.intro.empty")
                        : Localization.text("accounts.intro.choose")), BorderLayout.SOUTH);
                this.add(headingPanel, BorderLayout.NORTH);

                JList<MinecraftAccount> renderedAccountList = this.accountList;
                this.accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                this.accountList.setFixedCellHeight(48);
                this.accountList.setCellRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(
                            JList<?> list,
                            Object value,
                            int index,
                            boolean selected,
                            boolean focused
                    ) {
                        JLabel label = (JLabel) super.getListCellRendererComponent(
                                list, value, index,
                                selected, focused
                        );
                        MinecraftAccount account = (MinecraftAccount) value;
                        MinecraftAccount activeAccount = launcherService.account();
                        String type = account.type() == MinecraftAccount.AccountType.MICROSOFT
                                ? Localization.text("account.type.microsoft")
                                : Localization.text("account.type.offline");
                        String active = activeAccount != null && activeAccount.key().equals(account.key())
                                ? Localization.text("account.active")
                                : "";
                        label.setText(
                                "<html><b>" + account.username() + "</b><br>"
                                        + Localization.text("account.type.account", type) + active + "</html>"
                        );
                        label.setIcon(AccountIconProvider.INSTANCE.iconFor(
                                account,
                                renderedAccountList::repaint
                        ));
                        label.setIconTextGap(10);
                        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                        return label;
                    }
                });

                JPanel accountContent = new JPanel(new BorderLayout(0, 8));
                accountContent.add(new JScrollPane(this.accountList), BorderLayout.CENTER);
                JPanel signInState = new JPanel(new GridBagLayout());
                GridBagConstraints progress = new GridBagConstraints();
                progress.gridx = 0;
                progress.gridy = 0;
                progress.gridwidth = 2;
                progress.weightx = 1;
                progress.fill = GridBagConstraints.HORIZONTAL;
                progress.insets = new Insets(0, 0, 5, 0);
                this.progressBar.setIndeterminate(true);
                this.progressBar.setVisible(false);
                signInState.add(this.progressBar, progress);

                GridBagConstraints verification = new GridBagConstraints();
                verification.gridx = 0;
                verification.gridy = 1;
                verification.anchor = GridBagConstraints.LINE_START;
                verification.insets = new Insets(2, 0, 2, 10);
                signInState.add(this.verificationLabel, verification);

                GridBagConstraints code = new GridBagConstraints();
                code.gridx = 1;
                code.gridy = 1;
                code.weightx = 1;
                code.fill = GridBagConstraints.HORIZONTAL;
                this.codeField.setEditable(false);
                this.codeField.setHorizontalAlignment(SwingConstants.CENTER);
                this.codeField.setFont(this.codeField.getFont().deriveFont(Font.BOLD, 15f));
                this.codeField.setVisible(false);
                signInState.add(this.codeField, code);

                GridBagConstraints status = new GridBagConstraints();
                status.gridx = 0;
                status.gridy = 2;
                status.gridwidth = 2;
                status.weightx = 1;
                status.fill = GridBagConstraints.HORIZONTAL;
                status.insets = new Insets(4, 0, 0, 0);
                signInState.add(this.statusLabel, status);
                accountContent.add(signInState, BorderLayout.SOUTH);
                this.add(accountContent, BorderLayout.CENTER);

                JPanel accountActions = new JPanel(new BorderLayout(8, 0));
                JPanel additions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                additions.add(this.addMicrosoftButton);
                this.cancelMicrosoftButton.setVisible(false);
                additions.add(this.cancelMicrosoftButton);
                additions.add(this.addOfflineButton);
                additions.add(this.removeButton);
                accountActions.add(additions, BorderLayout.WEST);
                accountActions.add(this.useButton, BorderLayout.EAST);
                this.add(accountActions, BorderLayout.SOUTH);

                this.accountList.addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()) this.updateControlState();
                });
                BooleanSupplier busyState = () -> this.busy;
                Runnable accountSelector = this::selectAccount;
                this.accountList.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(@NotNull MouseEvent event) {
                        if (event.getClickCount() == 2 && !busyState.getAsBoolean()) accountSelector.run();
                    }
                });
                this.addOfflineButton.addActionListener(event -> {
                    String username = JOptionPane.showInputDialog(
                            this,
                            Localization.text("accounts.prompt.offline"),
                            Localization.text("accounts.dialog.add_offline"),
                            JOptionPane.PLAIN_MESSAGE
                    );
                    if (username == null) return;
                    try {
                        MinecraftAccount account = launcherService.addOfflineAccount(username);
                        this.statusLabel.setText(Localization.text(
                                "accounts.status.added_offline",
                                account.username()
                        ));
                        this.refreshAccounts(account);
                    }
                    catch (Exception exception) {
                        this.showError(Localization.text("accounts.error.add_offline"), exception);
                    }
                });
                this.addMicrosoftButton.addActionListener(event -> {
                    if (this.busy) return;
                    this.setBusy(true);
                    this.statusLabel.setText(Localization.text("accounts.status.starting_microsoft"));
                    this.verificationLabel.setText(" ");
                    this.codeField.setText("");
                    this.codeField.setVisible(false);

                    JLabel microsoftVerification = this.verificationLabel;
                    JTextField microsoftCode = this.codeField;
                    JLabel microsoftStatus = this.statusLabel;
                    Runnable clearWorker = () -> this.microsoftWorker = null;
                    Consumer<Boolean> busyUpdater = this::setBusy;
                    Consumer<MinecraftAccount> accountRefresher = this::refreshAccounts;
                    BiConsumer<String, Throwable> errorHandler = this::showError;
                    this.microsoftWorker = new SwingWorker<>() {
                        @Override
                        protected MinecraftAccount doInBackground() throws Exception {
                            return launcherService.addMicrosoftAccount(deviceCode ->
                                    SwingUtilities.invokeLater(() -> {
                                        microsoftVerification.setText(Localization.text(
                                                "accounts.verification",
                                                deviceCode.verificationUri()
                                        ));
                                        microsoftCode.setText(deviceCode.userCode());
                                        microsoftCode.setVisible(true);
                                        microsoftStatus.setText(Localization.text("accounts.status.waiting_microsoft"));
                                    })
                            );
                        }

                        @Override
                        protected void done() {
                            clearWorker.run();
                            busyUpdater.accept(false);
                            if (this.isCancelled()) {
                                microsoftVerification.setText(" ");
                                microsoftCode.setVisible(false);
                                microsoftStatus.setText(Localization.text("accounts.status.microsoft_cancelled"));
                                return;
                            }
                            try {
                                MinecraftAccount account = this.get();
                                microsoftStatus.setText(Localization.text(
                                        "accounts.status.added_microsoft",
                                        account.username()
                                ));
                                microsoftVerification.setText(" ");
                                microsoftCode.setVisible(false);
                                accountRefresher.accept(account);
                            }
                            catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                errorHandler.accept(
                                        Localization.text("accounts.error.microsoft_interrupted"),
                                        exception
                                );
                            }
                            catch (ExecutionException exception) {
                                errorHandler.accept(
                                        Localization.text("accounts.error.add_microsoft"),
                                        exception.getCause()
                                );
                            }
                        }
                    };
                    this.microsoftWorker.execute();
                });
                this.cancelMicrosoftButton.addActionListener(event -> {
                    if (this.microsoftWorker != null) this.microsoftWorker.cancel(true);
                });
                this.removeButton.addActionListener(event -> {
                    MinecraftAccount account = this.accountList.getSelectedValue();
                    if (account == null) return;
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            Localization.text("accounts.confirm.remove", account.username()),
                            Localization.text("accounts.dialog.remove"),
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (choice != JOptionPane.OK_OPTION) return;
                    try {
                        launcherService.removeAccount(account);
                        this.statusLabel.setText(Localization.text(
                                "accounts.status.removed",
                                account.username()
                        ));
                        this.refreshAccounts(launcherService.account());
                    }
                    catch (Exception exception) {
                        this.showError(Localization.text("accounts.error.remove"), exception);
                    }
                });
                this.useButton.addActionListener(event -> this.selectAccount());
                this.refreshAccounts(launcherService.account());
            }

            @Override
            @NotNull
            public String header() {
                return Localization.text("accounts.heading");
            }

            private void selectAccount() {
                MinecraftAccount account = this.accountList.getSelectedValue();
                if (account == null) return;

                MinecraftAccount activeAccount = launcherService.account();
                if (activeAccount != null && activeAccount.key().equals(account.key())) return;
                try {
                    launcherService.selectAccount(account);
                    this.statusLabel.setText(Localization.text("accounts.status.using", account.username()));
                    this.accountList.repaint();
                    this.updateControlState();
                }
                catch (Exception exception) {
                    this.showError(Localization.text("accounts.error.select"), exception);
                }
            }

            private void refreshAccounts(@Nullable MinecraftAccount preferredSelection) {
                this.accountModel.clear();
                for (MinecraftAccount account : launcherService.accounts()) {
                    this.accountModel.addElement(account);
                }
                if (preferredSelection != null) this.accountList.setSelectedValue(preferredSelection, true);
                if (this.accountList.getSelectedIndex() < 0 && !this.accountModel.isEmpty()) {
                    this.accountList.setSelectedIndex(0);
                }
                this.updateControlState();
            }

            private void setBusy(boolean busy) {
                this.busy = busy;
                this.progressBar.setVisible(busy);
                this.cancelMicrosoftButton.setVisible(busy);
                busyListener.accept(busy);
                this.updateControlState();
            }

            private void updateControlState() {
                MinecraftAccount selectedAccount = this.accountList.getSelectedValue();
                MinecraftAccount activeAccount = launcherService.account();
                boolean hasSelection = selectedAccount != null;
                boolean selectionIsActive = hasSelection && activeAccount != null
                        && activeAccount.key().equals(selectedAccount.key());
                this.accountList.setEnabled(!this.busy);
                this.addMicrosoftButton.setEnabled(!this.busy);
                this.addOfflineButton.setEnabled(!this.busy);
                this.removeButton.setEnabled(!this.busy && hasSelection);
                this.useButton.setEnabled(!this.busy && hasSelection && !selectionIsActive);
            }

            private void showError(@NotNull String title, @Nullable Throwable throwable) {
                Throwable cause = throwable == null ? new IllegalStateException(title) : throwable;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                String message = cause.getMessage();
                if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
                this.statusLabel.setText(title + ".");
                JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
            }
        };
    }

    @NotNull
    public static SquirrelLauncherSettingsPanel createAboutSettingsTab() {
        return new SquirrelLauncherSettingsPanel(new GridBagLayout()) {
            @Override
            protected void initializeSettingsPanel() {
                GridBagConstraints title = new GridBagConstraints();
                title.gridx = 0;
                title.gridy = 0;
                title.weightx = 1;
                title.anchor = GridBagConstraints.LINE_START;
                title.insets = new Insets(0, 0, 16, 0);
                this.add(this.createHeader(), title);

                JEditorPane description = new JEditorPane("text/html", Localization.text("about.description"));
                description.setEditable(false);
                description.setOpaque(false);
                description.addHyperlinkListener(event -> {
                    if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
                    try {
                        if (!Desktop.isDesktopSupported()
                                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            throw new IllegalStateException(Localization.text("about.error.unsupported"));
                        }
                        Desktop.getDesktop().browse(URI.create(event.getURL().toString()));
                    }
                    catch (Exception exception) {
                        String message = exception.getMessage();
                        if (message == null || message.isBlank()) {
                            message = exception.getClass().getSimpleName();
                        }
                        JOptionPane.showMessageDialog(
                                this,
                                message,
                                Localization.text("about.error.open_link"),
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                });

                GridBagConstraints linkConstraints = new GridBagConstraints();
                linkConstraints.gridx = 0;
                linkConstraints.gridy = 1;
                linkConstraints.weightx = 1;
                linkConstraints.weighty = 1;
                linkConstraints.fill = GridBagConstraints.BOTH;
                linkConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
                this.add(description, linkConstraints);
            }

            @Override
            @NotNull
            public String header() {
                return Localization.text("about.heading");
            }
        };
    }

    //----abstract methods----
    protected abstract void initializeSettingsPanel();

    @NotNull
    public abstract String header();

    //----methods common to the tabs----
    @NotNull
    protected final JLabel createHeader() {
        JLabel headerLabel = new JLabel(this.header());
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 20f));
        return headerLabel;
    }

    protected final void showSaveError(@NotNull Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        JOptionPane.showMessageDialog(
                this, message,
                Localization.text("settings.error.save"),
                JOptionPane.ERROR_MESSAGE
        );
    }
}
