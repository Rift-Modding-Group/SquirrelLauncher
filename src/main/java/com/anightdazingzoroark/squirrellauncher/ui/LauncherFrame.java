package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.SettingsDialog;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceDetailsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceSidebarPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

//the main ui users will deal with
public final class LauncherFrame extends JFrame {
    @NotNull
    private final JComboBox<MinecraftAccount> accountSelector = new JComboBox<>();
    @NotNull
    private final JButton manageAccountsButton;
    @NotNull
    private final JButton settingsButton;
    @NotNull
    private final JLabel statusLabel;
    @NotNull
    private final JProgressBar progressBar = new JProgressBar();
    @NotNull
    private final JButton stopButton;
    @NotNull
    private final JButton launchButton;
    @NotNull
    private final LauncherService launcherService;
    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final InstanceSidebarPanel instanceSidebarPanel;
    @NotNull
    private final InstanceDetailsPanel instanceDetailsPanel;
    private boolean busy;
    private boolean updatingAccountSelector;
    @Nullable
    private volatile String taskActivityInstanceId;
    @Nullable
    private volatile Object taskActivityToken;
    @Nullable
    private volatile String runningActivityInstanceId;
    @Nullable
    private volatile String runningActivityPrefix;
    @Nullable
    private Process runningProcess;

    public LauncherFrame() {
        super(SquirrelLauncher.NAME);
        this.launcherService = new LauncherService(this::appendBackendOutput);
        Localization.configure(this.launcherService.settings().language());
        this.manageAccountsButton = new JButton(Localization.text("main.button.manage_accounts"));
        this.settingsButton = new JButton(Localization.text("main.button.settings"));
        this.statusLabel = new JLabel(Localization.text("main.status.ready"));
        this.stopButton = new JButton(Localization.text("main.button.stop"));
        this.launchButton = new JButton(Localization.text("main.button.launch"));
        this.launcherActions = new LauncherActions(this, this.launcherService);
        this.instanceSidebarPanel = new InstanceSidebarPanel(this.launcherActions);
        this.instanceDetailsPanel = new InstanceDetailsPanel(this.launcherActions);
        this.launcherActions.connectPanels(this.instanceSidebarPanel, this.instanceDetailsPanel);

        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setMinimumSize(new Dimension(820, 560));
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setLocationByPlatform(true);
        this.setLayout(new BorderLayout(0, 0));
        this.add(this.createAccountBar(), BorderLayout.NORTH);
        this.add(this.createMainContent(), BorderLayout.CENTER);
        this.add(this.createStatusBar(), BorderLayout.SOUTH);

        this.configureListeners();
        this.refreshAccountSelector();
        this.updateControlState();
        this.launcherActions.refreshInstances(null);
        SwingUtilities.invokeLater(() -> {
            if (this.launcherService.accounts().isEmpty()) {
                this.showSettings(SettingsDialog.SettingsTab.ACCOUNTS);
            }
        });
    }

    //---component listeners---
    private void configureListeners() {
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(@NotNull WindowEvent event) {
                LauncherFrame.this.launcherService.close();
            }
        });
        this.accountSelector.addActionListener(event -> {
            if (this.updatingAccountSelector || this.busy) return;
            MinecraftAccount account = (MinecraftAccount) this.accountSelector.getSelectedItem();
            if (account == null) return;
            try {
                this.launcherService.selectAccount(account);
                this.setStatus(Localization.text("main.status.using_account", account.username()));
                this.updateControlState();
            }
            catch (Exception exception) {
                this.showError(Localization.text("main.error.select_account"), exception, null);
                this.refreshAccountSelector();
            }
        });
        this.manageAccountsButton.addActionListener(event -> this.showSettings(SettingsDialog.SettingsTab.ACCOUNTS));
        this.settingsButton.addActionListener(event -> this.showSettings(SettingsDialog.SettingsTab.GAME));
        this.stopButton.addActionListener(event -> this.stopMinecraft());
        this.launchButton.addActionListener(event -> this.launcherActions.launchRequested());
    }

    //---init components---
    @NotNull
    private JPanel createAccountBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel title = new JLabel(SquirrelLauncher.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title, BorderLayout.WEST);

        JPanel accountControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountControls.add(new JLabel(Localization.text("main.label.account")));
        this.accountSelector.setPreferredSize(new Dimension(220, 38));
        this.accountSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list,
                    @Nullable Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof MinecraftAccount account) {
                    String accountName = account.username();
                    if (account.type() == MinecraftAccount.AccountType.OFFLINE) {
                        accountName += " (" + LauncherFrame.displayName(MinecraftAccount.AccountType.OFFLINE) + ")";
                    }
                    label.setText(accountName);
                    label.setIcon(AccountIconProvider.INSTANCE.iconFor(account, LauncherFrame.this.accountSelector::repaint));
                    label.setIconTextGap(8);
                }
                else {
                    label.setText(Localization.text("main.account.none"));
                    label.setIcon(null);
                }
                return label;
            }
        });
        accountControls.add(this.accountSelector);
        accountControls.add(this.manageAccountsButton);
        accountControls.add(this.settingsButton);
        panel.add(accountControls, BorderLayout.EAST);
        return panel;
    }

    @NotNull
    private JPanel createMainContent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(this.instanceSidebarPanel, BorderLayout.WEST);
        panel.add(this.instanceDetailsPanel, BorderLayout.CENTER);
        return panel;
    }

    @NotNull
    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, panel.getBackground().darker()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        this.progressBar.setPreferredSize(new Dimension(90, 16));
        JPanel status = new JPanel(new GridBagLayout());
        GridBagConstraints progress = new GridBagConstraints();
        progress.gridx = 0;
        progress.anchor = GridBagConstraints.LINE_START;
        progress.insets = new Insets(0, 0, 0, 8);
        status.add(this.progressBar, progress);
        GridBagConstraints statusText = new GridBagConstraints();
        statusText.gridx = 1;
        statusText.weightx = 1;
        statusText.anchor = GridBagConstraints.LINE_START;
        status.add(this.statusLabel, statusText);
        panel.add(status, BorderLayout.CENTER);
        JPanel gameControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        gameControls.add(this.stopButton);
        this.launchButton.setFont(this.launchButton.getFont().deriveFont(Font.BOLD));
        gameControls.add(this.launchButton);
        panel.add(gameControls, BorderLayout.EAST);
        return panel;
    }

    private void stopMinecraft() {
        Process process = this.runningProcess;
        if (process == null || !process.isAlive()) return;
        int choice = JOptionPane.showConfirmDialog(
                this,
                Localization.text("main.confirm.stop"),
                Localization.text("main.dialog.stop"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) return;
        this.stopButton.setEnabled(false);
        this.setStatus(Localization.text("main.status.stopping_minecraft"));
        process.destroyForcibly();
    }

    void monitorProcess(@NotNull MinecraftInstance instance, @NotNull Process process) {
        long launchTimeMillis = System.currentTimeMillis();
        long launchTimeNanos = System.nanoTime();
        this.runningProcess = process;
        this.runningActivityInstanceId = instance.id();
        this.runningActivityPrefix = "[" + instance.name() + "] ";
        this.setStatus(Localization.text("main.status.minecraft_running"));
        this.updateControlState();
        new SwingWorker<GameExit, Void>() {
            @Override
            @NotNull
            protected GameExit doInBackground() throws Exception {
                int exitCode = process.waitFor();
                long elapsedNanos = System.nanoTime() - launchTimeNanos;
                long elapsedSeconds = elapsedNanos <= 0 ? 0 : elapsedNanos / 1_000_000_000L;
                try {
                    MinecraftInstance updated = LauncherFrame.this.launcherService.recordPlaytime(
                            instance, elapsedSeconds, launchTimeMillis
                    );
                    return new GameExit(exitCode, updated, null);
                }
                catch (Exception exception) {
                    return new GameExit(exitCode, instance, exception);
                }
            }

            @Override
            protected void done() {
                LauncherFrame.this.runningProcess = null;
                try {
                    GameExit result = this.get();
                    LauncherFrame.this.setStatus(Localization.text("main.status.minecraft_exited", result.exitCode()));
                    LauncherFrame.this.appendActivity(
                            instance.id(),
                            Localization.text("main.activity.minecraft_exited", instance.name(), result.exitCode())
                    );
                    LauncherFrame.this.instanceSidebarPanel.replaceInstance(result.instance());
                    if (result.playtimeError() != null) {
                        LauncherFrame.this.showError(
                                Localization.text("main.error.save_playtime"), result.playtimeError(), instance.id()
                        );
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.waiting_interrupted"), exception, instance.id()
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.monitor_minecraft"), exception.getCause(), instance.id()
                    );
                }
                LauncherFrame.this.runningActivityPrefix = null;
                LauncherFrame.this.runningActivityInstanceId = null;
                LauncherFrame.this.updateControlState();
            }
        }.execute();
    }

    <T> void runTask(
            @Nullable String activityInstanceId, @NotNull String status,
            @NotNull Callable<T> task, @NotNull Consumer<T> onSuccess
    ) {
        if (this.busy) return;
        Object activityToken = new Object();
        this.taskActivityInstanceId = activityInstanceId;
        this.taskActivityToken = activityToken;
        this.setStatus(status);
        this.setBusy(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                LauncherFrame.this.setBusy(false);
                try {
                    onSuccess.accept(this.get());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_interrupted"), exception, activityInstanceId
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_failed"), exception.getCause(), activityInstanceId
                    );
                }
                catch (RuntimeException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_failed"), exception, activityInstanceId
                    );
                }
                finally {
                    if (LauncherFrame.this.taskActivityToken == activityToken) {
                        LauncherFrame.this.taskActivityToken = null;
                        LauncherFrame.this.taskActivityInstanceId = null;
                    }
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        this.progressBar.setVisible(busy);
        this.updateControlState();
    }

    void updateControlState() {
        boolean available = !this.busy;
        MinecraftInstance instance = this.instanceSidebarPanel.selectedInstance();
        boolean instanceRunning = this.runningProcess != null;
        this.accountSelector.setEnabled(available && this.accountSelector.getItemCount() > 0);
        this.manageAccountsButton.setEnabled(available);
        this.settingsButton.setEnabled(available);
        this.instanceSidebarPanel.updateControlState(available, instanceRunning);
        this.instanceDetailsPanel.updateControlState(available, instance != null, instanceRunning);
        this.stopButton.setEnabled(available && instanceRunning && this.runningProcess.isAlive());
        this.launchButton.setEnabled(
                available && instance != null && this.launcherService.account() != null && !instanceRunning
        );
        this.launchButton.setText(Localization.text(instanceRunning ? "main.button.running" : "main.button.launch"));
    }

    void refreshAccountSelector() {
        this.updatingAccountSelector = true;
        this.accountSelector.removeAllItems();
        for (MinecraftAccount account : this.launcherService.accounts()) this.accountSelector.addItem(account);
        this.accountSelector.setSelectedItem(this.launcherService.account());
        this.updatingAccountSelector = false;
        this.updateControlState();
    }

    private void showSettings(@NotNull SettingsDialog.SettingsTab selectedTab) {
        new SettingsDialog(this, this.launcherService, selectedTab).showModal();
        this.refreshAccountSelector();
        MinecraftAccount account = this.launcherService.account();
        if (account != null) this.setStatus(Localization.text("main.status.using_account", account.username()));
    }

    void setStatus(@NotNull String status) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> this.setStatus(status));
            return;
        }
        this.statusLabel.setText(status);
    }

    private void appendBackendOutput(@NotNull String line) {
        if (line.isBlank()) return;
        Object activityToken = this.taskActivityToken;
        String instanceId;
        if (activityToken != null) {
            instanceId = this.taskActivityInstanceId;
        }
        else {
            instanceId = this.runningActivityInstanceId;
            String prefix = this.runningActivityPrefix;
            if (prefix == null || !line.startsWith(prefix)) return;
        }
        if (instanceId != null) this.appendActivity(instanceId, line);
    }

    void appendActivity(@NotNull String instanceId, @NotNull String message) {
        this.instanceDetailsPanel.activityTab().append(instanceId, message);
    }

    void showError(@NotNull String title, @NotNull Throwable throwable, @Nullable String activityInstanceId) {
        Throwable cause = LauncherFrame.rootCause(throwable);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        this.setStatus(title + ".");
        if (activityInstanceId != null) {
            this.appendActivity(activityInstanceId, title + ": " + message.replace('\n', ' '));
        }
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    boolean isMinecraftRunning() {
        return this.runningProcess != null;
    }

    @NotNull
    private static Throwable rootCause(@NotNull Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }

    @NotNull
    public static String displayName(@NotNull Enum<?> value) {
        return switch (value) {
            case InstanceType type -> Localization.text("instance.type." + type.name().toLowerCase());
            case ModState state -> Localization.text("mod.state." + state.name().toLowerCase());
            case MinecraftAccount.AccountType accountType ->
                    Localization.text("account.type." + accountType.name().toLowerCase());
            default -> {
                String name = value.name().toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }

    @NotNull
    public static String playtimeText(long totalTimePlayedSeconds) {
        long safeTotalTimePlayedSeconds = Math.max(0, totalTimePlayedSeconds);
        if (safeTotalTimePlayedSeconds < 3_600) {
            long minutes = safeTotalTimePlayedSeconds / 60;
            return Localization.text(
                    minutes == 1 ? "main.instance.playtime.minute" : "main.instance.playtime.minutes", minutes
            );
        }
        long tenthsOfAnHour = safeTotalTimePlayedSeconds / 360;
        if (safeTotalTimePlayedSeconds % 360 >= 180) tenthsOfAnHour++;
        return Localization.text(
                "main.instance.playtime", (tenthsOfAnHour / 10) + "." + (tenthsOfAnHour % 10)
        );
    }

    private record GameExit(
            int exitCode,
            @NotNull MinecraftInstance instance,
            @Nullable Throwable playtimeError
    ) {}
}
