package com.anightdazingzoroark.squirrellauncher.minecraft.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.time.Duration;
import java.util.function.Consumer;

/** Performs Microsoft, Xbox Live, XSTS, and Minecraft Services authentication. */
public final class MicrosoftAuthenticator {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final String CLIENT_ID_PROPERTY = "squirrellauncher.microsoft.clientId";
    private static final String CLIENT_ID_ENVIRONMENT_VARIABLE = "SQUIRREL_CLIENT_ID";
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public MicrosoftAuthenticator() {}

    @NotNull
    public MinecraftAccount login(@NotNull Consumer<DeviceCode> deviceCodeConsumer) throws Exception {
        String clientId = this.clientId();
        String deviceForm = this.form("client_id", clientId, "scope", "XboxLive.signin offline_access");
        HttpRequest deviceRequest = HttpRequest.newBuilder().uri(URI.create(DEVICE_CODE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(deviceForm))
                .build();
        HttpResponse<String> deviceResponse = HTTP.send(deviceRequest, HttpResponse.BodyHandlers.ofString());
        this.requireSuccess(deviceResponse, "Microsoft device-code request");

        JsonObject device = JsonParser.parseString(deviceResponse.body()).getAsJsonObject();
        String verificationUri = device.get("verification_uri").getAsString();
        String userCode = device.get("user_code").getAsString();
        String deviceCode = device.get("device_code").getAsString();
        int interval = device.get("interval").getAsInt();
        int expiresIn = device.get("expires_in").getAsInt();
        deviceCodeConsumer.accept(new DeviceCode(verificationUri, userCode));

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(verificationUri));
            }
        }
        catch (Exception ignored) {}

        long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        JsonObject microsoftToken = null;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);
            String tokenForm = this.form(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", deviceCode
            );
            HttpRequest tokenRequest = HttpRequest.newBuilder().uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenForm))
                    .build();
            HttpResponse<String> tokenResponse = HTTP.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(tokenResponse.body()).getAsJsonObject();
            if (tokenResponse.statusCode() >= 200 && tokenResponse.statusCode() < 300) {
                microsoftToken = json;
                break;
            }
            if (!json.has("error")) {
                throw new IOException("Unexpected Microsoft OAuth response: " + tokenResponse.body());
            }

            String error = json.get("error").getAsString();
            switch (error) {
                case "authorization_pending" -> {}
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new IOException("Microsoft login was declined.");
                case "expired_token" -> throw new IOException("Microsoft login code expired.");
                default -> throw new IOException("Microsoft OAuth error: " + tokenResponse.body());
            }
        }
        if (microsoftToken == null) throw new IOException("Microsoft login timed out.");

        String microsoftAccessToken = microsoftToken.get("access_token").getAsString();
        String refreshToken = microsoftToken.get("refresh_token").getAsString();
        return this.exchangeForMinecraft(microsoftAccessToken, refreshToken);
    }

    @NotNull
    public MinecraftAccount refresh(@NotNull MinecraftAccount account) throws Exception {
        if (account.type() != MinecraftAccount.AccountType.MICROSOFT
                || account.refreshToken() == null || account.refreshToken().isBlank()) {
            throw new IllegalArgumentException("This account cannot be refreshed with Microsoft.");
        }

        String clientId = this.clientId();
        String tokenForm = this.form(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", account.refreshToken(),
                "scope", "XboxLive.signin offline_access"
        );
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenForm))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Microsoft session refresh failed. Remove and add the account again.\n\n"
                            + "HTTP " + response.statusCode() + "\n" + response.body()
            );
        }

        JsonObject token = JsonParser.parseString(response.body()).getAsJsonObject();
        String refreshToken = token.has("refresh_token")
                ? token.get("refresh_token").getAsString()
                : account.refreshToken();
        return this.exchangeForMinecraft(token.get("access_token").getAsString(), refreshToken);
    }

    @NotNull
    private MinecraftAccount exchangeForMinecraft(
            @NotNull String microsoftAccessToken,
            @NotNull String refreshToken
    ) throws Exception {
        JsonObject xboxProperties = new JsonObject();
        xboxProperties.addProperty("AuthMethod", "RPS");
        xboxProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProperties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        JsonObject xboxBody = new JsonObject();
        xboxBody.add("Properties", xboxProperties);
        xboxBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxBody.addProperty("TokenType", "JWT");
        JsonObject xbox = this.postJson(XBOX_AUTH_URL, xboxBody);

        JsonArray tokens = new JsonArray();
        tokens.add(xbox.get("Token").getAsString());
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        xstsProperties.add("UserTokens", tokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProperties);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = this.postJson(XSTS_AUTH_URL, xstsBody);
        String userHash = xsts.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0)
                .getAsJsonObject()
                .get("uhs")
                .getAsString();

        JsonObject minecraftBody = new JsonObject();
        minecraftBody.addProperty(
                "identityToken",
                "XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString()
        );
        HttpRequest minecraftRequest = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_LOGIN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(minecraftBody.toString()))
                .build();
        HttpResponse<String> minecraftResponse = HTTP.send(
                minecraftRequest,
                HttpResponse.BodyHandlers.ofString()
        );
        if (minecraftResponse.statusCode() == 403
                && minecraftResponse.body().contains("Invalid app registration")) {
            throw new IOException(
                    "Minecraft Services rejected this application registration. "
                            + "Confirm that the configured client ID is the approved SquirrelLauncher application."
            );
        }
        this.requireSuccess(minecraftResponse, "Minecraft Services login");
        String minecraftAccessToken = JsonParser.parseString(minecraftResponse.body())
                .getAsJsonObject()
                .get("access_token")
                .getAsString();

        HttpRequest profileRequest = HttpRequest.newBuilder()
                .uri(URI.create(MINECRAFT_PROFILE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + minecraftAccessToken)
                .GET()
                .build();
        HttpResponse<String> profileResponse = HTTP.send(profileRequest, HttpResponse.BodyHandlers.ofString());
        if (profileResponse.statusCode() == 404) {
            throw new IOException("This Microsoft account does not have a Minecraft Java profile.");
        }
        this.requireSuccess(profileResponse, "Minecraft profile");
        JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
        String skinUrl = null;
        if (profile.has("skins")) {
            for (JsonElement element : profile.getAsJsonArray("skins")) {
                JsonObject skin = element.getAsJsonObject();
                if (!skin.has("url") || skin.get("url").isJsonNull()) continue;
                if (skinUrl == null) skinUrl = skin.get("url").getAsString();
                if (skin.has("state") && "ACTIVE".equalsIgnoreCase(skin.get("state").getAsString())) {
                    skinUrl = skin.get("url").getAsString();
                    break;
                }
            }
        }
        return new MinecraftAccount(
                profile.get("name").getAsString(),
                profile.get("id").getAsString(),
                minecraftAccessToken,
                refreshToken,
                skinUrl,
                MinecraftAccount.AccountType.MICROSOFT
        );
    }

    @NotNull
    private JsonObject postJson(@NotNull String url, @NotNull JsonObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        this.requireSuccess(response, url);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void requireSuccess(
            @NotNull HttpResponse<String> response,
            @NotNull String operation
    ) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(operation + " failed: HTTP " + response.statusCode() + "\n" + response.body());
        }
    }

    @NotNull
    private String form(@NotNull String... entries) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < entries.length; index += 2) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entries[index], StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(entries[index + 1], StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    @NotNull
    private String clientId() {
        String clientId = System.getProperty(CLIENT_ID_PROPERTY);
        if (clientId == null || clientId.isBlank()) clientId = System.getenv(CLIENT_ID_ENVIRONMENT_VARIABLE);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "The SquirrelLauncher Microsoft application ID is not configured.\n\n"
                            + "Set SQUIRREL_CLIENT_ID or -D" + CLIENT_ID_PROPERTY + " to the approved client ID."
            );
        }
        return clientId.trim();
    }

    public record DeviceCode(@NotNull String verificationUri, @NotNull String userCode) {}
}
