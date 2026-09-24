package com.anightdazingzoroark.squirrellauncher.ui.settings;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.ui.AccountIconProvider;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class AccountSettingsPanel extends AbstractSettingsPanel {
    @NotNull
    private final LauncherService launcherService;
    @NotNull
    private final Consumer<Boolean> busyListener;
    @NotNull
    private final DefaultListModel<MinecraftAccount> accountModel = new DefaultListModel<>();
    @NotNull
    private final JList<MinecraftAccount> accountList = new JList<>(this.accountModel);
    @NotNull
    private final JButton addMicrosoftButton = new JButton(Localization.text("accounts.button.add_microsoft"));
    @NotNull
    private final JButton cancelMicrosoftButton = new JButton(Localization.text("accounts.button.cancel_sign_in"));
    @NotNull
    private final JButton addOfflineButton = new JButton(Localization.text("accounts.button.add_offline"));
    @NotNull
    private final JButton removeButton = new JButton(Localization.text("accounts.button.remove"));
    @NotNull
    private final JButton useButton = new JButton(Localization.text("accounts.button.use"));
    @NotNull
    private final JLabel statusLabel = new JLabel(" ");
    @NotNull
    private final JLabel verificationLabel = new JLabel(" ");
    @NotNull
    private final JTextField codeField = new JTextField(12);
    @NotNull
    private final JProgressBar progressBar = new JProgressBar();
    @Nullable
    private SwingWorker<MinecraftAccount, Void> microsoftWorker;
    private boolean busy;

    public AccountSettingsPanel(
            @NotNull LauncherService launcherService,
            @NotNull Consumer<Boolean> busyListener
    ) {
        super(new BorderLayout(0, 10));
        this.launcherService = launcherService;
        this.busyListener = busyListener;

        JPanel headingPanel = new JPanel(new BorderLayout(0, 5));
        JLabel heading = this.createHeader();
        headingPanel.add(heading, BorderLayout.NORTH);
        headingPanel.add(new JLabel(this.launcherService.accounts().isEmpty()
                ? Localization.text("accounts.intro.empty")
                : Localization.text("accounts.intro.choose")), BorderLayout.SOUTH);
        this.add(headingPanel, BorderLayout.NORTH);

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
                        list, value, index, selected, focused
                );
                MinecraftAccount account = (MinecraftAccount) value;
                MinecraftAccount activeAccount = AccountSettingsPanel.this.launcherService.account();
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
                        AccountSettingsPanel.this.accountList::repaint
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
        this.accountList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(@NotNull MouseEvent event) {
                if (event.getClickCount() == 2 && !AccountSettingsPanel.this.busy) {
                    AccountSettingsPanel.this.selectAccount();
                }
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
                MinecraftAccount account = this.launcherService.addOfflineAccount(username);
                this.statusLabel.setText(Localization.text("accounts.status.added_offline", account.username()));
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
            this.microsoftWorker = new SwingWorker<>() {
                @Override
                protected MinecraftAccount doInBackground() throws Exception {
                    return AccountSettingsPanel.this.launcherService.addMicrosoftAccount(deviceCode ->
                            SwingUtilities.invokeLater(() -> {
                                AccountSettingsPanel.this.verificationLabel.setText(
                                        Localization.text("accounts.verification", deviceCode.verificationUri())
                                );
                                AccountSettingsPanel.this.codeField.setText(deviceCode.userCode());
                                AccountSettingsPanel.this.codeField.setVisible(true);
                                AccountSettingsPanel.this.statusLabel.setText(
                                        Localization.text("accounts.status.waiting_microsoft")
                                );
                            })
                    );
                }

                @Override
                protected void done() {
                    AccountSettingsPanel.this.microsoftWorker = null;
                    AccountSettingsPanel.this.setBusy(false);
                    if (this.isCancelled()) {
                        AccountSettingsPanel.this.verificationLabel.setText(" ");
                        AccountSettingsPanel.this.codeField.setVisible(false);
                        AccountSettingsPanel.this.statusLabel.setText(
                                Localization.text("accounts.status.microsoft_cancelled")
                        );
                        return;
                    }
                    try {
                        MinecraftAccount account = this.get();
                        AccountSettingsPanel.this.statusLabel.setText(
                                Localization.text("accounts.status.added_microsoft", account.username())
                        );
                        AccountSettingsPanel.this.verificationLabel.setText(" ");
                        AccountSettingsPanel.this.codeField.setVisible(false);
                        AccountSettingsPanel.this.refreshAccounts(account);
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        AccountSettingsPanel.this.showError(
                                Localization.text("accounts.error.microsoft_interrupted"),
                                exception
                        );
                    }
                    catch (ExecutionException exception) {
                        AccountSettingsPanel.this.showError(
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
                this.launcherService.removeAccount(account);
                this.statusLabel.setText(Localization.text("accounts.status.removed", account.username()));
                this.refreshAccounts(this.launcherService.account());
            }
            catch (Exception exception) {
                this.showError(Localization.text("accounts.error.remove"), exception);
            }
        });
        this.useButton.addActionListener(event -> this.selectAccount());

        this.refreshAccounts(this.launcherService.account());
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("accounts.heading");
    }

    private void selectAccount() {
        MinecraftAccount account = this.accountList.getSelectedValue();
        if (account == null) return;
        MinecraftAccount activeAccount = this.launcherService.account();
        if (activeAccount != null && activeAccount.key().equals(account.key())) return;
        try {
            this.launcherService.selectAccount(account);
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
        for (MinecraftAccount account : this.launcherService.accounts()) this.accountModel.addElement(account);
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
        this.busyListener.accept(busy);
        this.updateControlState();
    }

    private void updateControlState() {
        MinecraftAccount selectedAccount = this.accountList.getSelectedValue();
        MinecraftAccount activeAccount = this.launcherService.account();
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
}
