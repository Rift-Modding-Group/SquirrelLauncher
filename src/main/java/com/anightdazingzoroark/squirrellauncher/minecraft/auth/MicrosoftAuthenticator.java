package com.anightdazingzoroark.squirrellauncher.minecraft.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class MicrosoftAuthenticator {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private MicrosoftAuthenticator() {}

    @NotNull
    public static MinecraftAccount login() throws Exception {
        String clientId = System.getenv("SQUIRREL_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    """
                    SQUIRREL_CLIENT_ID is not set.

                    Example:

                    export SQUIRREL_CLIENT_ID="your-client-id"
                    """
            );
        }

        //---microsoft device-code login---
        JsonObject device = requestDeviceCode(clientId);
        String verificationUri = device.get("verification_uri").getAsString();
        String userCode = device.get("user_code").getAsString();
        String deviceCode = device.get("device_code").getAsString();
        int interval = device.get("interval").getAsInt();
        int expiresIn = device.get("expires_in").getAsInt();

        System.out.println();
        System.out.println("=== Microsoft Login ===");
        System.out.println("Open:");
        System.out.println(verificationUri);

        System.out.println();
        System.out.println("Enter code:");
        System.out.println(userCode);

        //Try opening the user's browser automatically.
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(verificationUri));
            }

        }
        catch (Exception ignored) {}

        JsonObject microsoftToken = waitForMicrosoftToken(clientId, deviceCode, interval, expiresIn);
        String microsoftAccessToken = microsoftToken.get("access_token").getAsString();
        String refreshToken = microsoftToken.has("refresh_token") ?
                microsoftToken.get("refresh_token").getAsString() : null;

        System.out.println("Microsoft authentication successful.");

        //---xbox Live---
        JsonObject xbox = authenticateXbox(microsoftAccessToken);
        String xboxToken = xbox.get("Token").getAsString();
        System.out.println("Xbox Live authentication successful.");

        //---XSTS---
        JsonObject xsts = authenticateXsts(xboxToken);
        String xstsToken = xsts.get("Token").getAsString();
        String userHash = xsts.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0)
                .getAsJsonObject()
                .get("uhs")
                .getAsString();

        System.out.println("XSTS authentication successful.");

        //---Minecraft Services---
        String minecraftAccessToken = authenticateMinecraft(userHash, xstsToken);
        System.out.println("Minecraft Services authentication successful.");

        //---minecraft profile---
        JsonObject profile = getMinecraftProfile(minecraftAccessToken);
        String username = profile.get("name").getAsString();
        String uuid = profile.get("id").getAsString();

        System.out.println();
        System.out.println("Logged in as: " + username);

        System.out.println("UUID: " + uuid);

        return new MinecraftAccount(username, uuid, minecraftAccessToken, refreshToken, MinecraftAccount.AccountType.MICROSOFT);
    }

    @NotNull
    private static JsonObject requestDeviceCode(@NotNull String clientId) throws IOException, InterruptedException {
        String form = form("client_id", clientId, "scope", "XboxLive.signin offline_access");
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(DEVICE_CODE_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        requireSuccess(response, "Microsoft device-code request"
        );

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @NotNull
    private static JsonObject waitForMicrosoftToken(
            @NotNull String clientId, @NotNull String deviceCode,
            int intervalSeconds, int expiresInSeconds
    ) throws Exception {
        long deadline = System.currentTimeMillis() + expiresInSeconds * 1000L;
        int interval = intervalSeconds;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);

            String form = form(
                "grant_type",
                "urn:ietf:params:oauth:grant-type:device_code",
                "client_id",
                clientId,
                "device_code",
                deviceCode
            );

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return json;
            }

            if (!json.has("error")) {
                throw new IOException("Unexpected Microsoft OAuth response: " + response.body());
            }

            String error = json.get("error").getAsString();
            switch (error) {
                //User hasn't finished signing in yet.
                case "authorization_pending" -> {}
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new IOException("Microsoft login was declined.");
                case "expired_token" -> throw new IOException("Microsoft login code expired.");
                default -> throw new IOException("Microsoft OAuth error: " + response.body());
            }
        }

        throw new IOException("Microsoft login timed out.");
    }

    @NotNull
    private static JsonObject authenticateXbox(@NotNull String microsoftAccessToken) throws Exception {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");

        //device-code microsoft tokens use the d= prefix when supplied as an Xbox RPS ticket
        properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        return postJson(XBOX_AUTH_URL, body);
    }

    @NotNull
    private static JsonObject authenticateXsts(@NotNull String xboxToken) throws Exception {
        JsonArray tokens = new JsonArray();
        tokens.add(xboxToken);

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", tokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        return postJson(XSTS_AUTH_URL, body);
    }

    @NotNull
    private static String authenticateMinecraft(String userHash, String xstsToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_LOGIN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403 && response.body().contains("Invalid app registration")) {
            throw new IOException(
                    """
                    Minecraft Services rejected the SquirrelLauncher
                    client ID:

                    403 Invalid app registration

                    The Microsoft/Xbox authentication itself worked,
                    but this application ID is not currently authorized
                    by Minecraft Services.
                    """
            );
        }

        requireSuccess(response, "Minecraft Services login");
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    @NotNull
    private static JsonObject getMinecraftProfile(@NotNull String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_PROFILE_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException(
                    """
                    This Microsoft account does not appear
                    to have a Minecraft Java profile.
                    """
            );
        }

        requireSuccess(response, "Minecraft profile");

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    //---HTTP helpers from here on out---
    @NotNull
    private static JsonObject postJson(@NotNull String url, @NotNull JsonObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        requireSuccess(response, url);

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void requireSuccess(@NotNull HttpResponse<String> response, @NotNull String operation) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(operation + " failed: HTTP " + response.statusCode() + "\n" + response.body());
        }
    }

    @NotNull
    private static String form(String... entries) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < entries.length; i += 2) {
            if (!result.isEmpty()) result.append('&');

            result.append(URLEncoder.encode(entries[i], StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(entries[i + 1], StandardCharsets.UTF_8));
        }

        return result.toString();
    }
}