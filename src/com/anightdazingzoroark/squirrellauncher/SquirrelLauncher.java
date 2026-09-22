package com.anightdazingzoroark.squirrellauncher;

import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MicrosoftAuthenticator;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class SquirrelLauncher {
    public static final String NAME = "SquirrelLauncher";
    public static final String VERSION = "1.12.2";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== "+NAME+" ===");

        try {
            System.out.print("Sign in with a Microsoft account? [Y/N]: ");
            String answer = scanner.nextLine().trim();

            //---get account---
            MinecraftAccount account;
            if (answer.equalsIgnoreCase("Y")) {
                System.out.println();
                System.out.println("Starting Microsoft authentication...");
                account = MicrosoftAuthenticator.login();
            }
            else {
                System.out.println();
                System.out.print("Offline username [Squirrel]: ");

                String username = scanner.nextLine().trim();
                if (username.isEmpty()) username = "Squirrel";

                account = MinecraftAccount.offline(username);

                System.out.println("Starting in offline mode as " + account.username() + "...");
            }

            //---get instance---
            System.out.println();
            System.out.print("Instance name: ");
            String instanceName = scanner.nextLine();
            MinecraftInstance instance;
            if (!InstanceManager.exists(instanceName)) {
                System.out.println("Creating instance of name " + instanceName + "...");
                System.out.print("Instance type [VANILLA/FORGE/CLEANROOM]: ");
                String typeInput = scanner.nextLine().trim().toUpperCase();
                InstanceType type = InstanceType.valueOf(typeInput);

                String loaderVersion = null;
                if (type == InstanceType.FORGE) {
                    System.out.print("Forge version [14.23.5.2859]: ");
                    loaderVersion = scanner.nextLine().trim();

                    if (loaderVersion.isEmpty()) loaderVersion = "14.23.5.2859";
                }
                else if (type == InstanceType.CLEANROOM) {
                    System.out.println("Cleanroom Version [0.6.13-alpha]: ");
                    loaderVersion = scanner.nextLine().trim();

                    if (loaderVersion.isEmpty()) loaderVersion = "0.6.13-alpha";
                }

                instance = InstanceManager.create(instanceName, instanceName, type, loaderVersion);
            }
            else {
                System.out.println("Loading instance of name " + instanceName + "...");
                instance = InstanceManager.load(instanceName);
            }

            //---launch le game---
            System.out.println();
            System.out.println("Launching as: " + account.username());
            System.out.println("Account type: " + account.type());

            //mod management
            if (instance.type() != InstanceType.VANILLA) manageMods(scanner, instance);

            Process minecraft = instance.type().instanceCreator.apply(account, instance);

            //---exit le game---
            int exitCode = minecraft.waitFor();
            System.out.println();
            System.out.println("Minecraft exited with code " + exitCode);

        }
        catch (Exception e) {
            System.err.println();
            System.err.println(NAME+" failed:");
            e.printStackTrace();
        }
    }

    private static void manageMods(Scanner scanner, MinecraftInstance instance) throws Exception {
        ModManager manager = new ModManager(instance);

        while (true) {
            System.out.println();
            System.out.println("=== Mods: " + instance.name() + " ===");
            System.out.println("[1] List mods");
            System.out.println("[2] Install mod");
            System.out.println("[3] Enable mod");
            System.out.println("[4] Disable mod");
            System.out.println("[5] Remove mod");
            System.out.println("[6] Launch Minecraft");
            System.out.print("> ");

            String command = scanner.nextLine().trim();
            switch (command) {
                case "1" -> {
                    List<ManagedMod> mods = manager.list();
                    if (mods.isEmpty()) {
                        System.out.println("No mods installed.");
                        continue;
                    }

                    for (ManagedMod mod : mods) {
                        System.out.println("[" + mod.state() + "] " + mod.fileName());
                    }
                }
                case "2" -> {
                    System.out.print("Mod JAR path: ");
                    String path = scanner.nextLine().trim();
                    manager.install(Path.of(path));
                }
                case "3" -> {
                    System.out.print("Disabled mod filename: ");
                    String fileName = scanner.nextLine().trim();
                    manager.enable(fileName);
                }
                case "4" -> {
                    System.out.print("Enabled mod filename: ");
                    String fileName = scanner.nextLine().trim();
                    manager.disable(fileName);
                }
                case "5" -> {
                    System.out.print("Mod filename: ");
                    String fileName = scanner.nextLine().trim();
                    manager.remove(fileName);
                }
                case "6" -> {
                    return;
                }
                default ->System.out.println("Unknown command.");
            }
        }
    }
}