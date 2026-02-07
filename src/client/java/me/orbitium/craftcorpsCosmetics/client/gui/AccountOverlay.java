package me.orbitium.craftcorpsCosmetics.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import me.orbitium.craftcorpsCosmetics.client.session.ModSession;
import me.orbitium.craftcorpsCosmetics.client.session.SessionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AccountOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountOverlay.class);

    private static final int PADDING = 5;
    private static final int HEAD_SIZE = 16;
    private static final int BACKGROUND_COLOR = 0xDD000000;

    private static final int WIDTH = 130;
    private static final int HEIGHT = 28;

    private static boolean showCosmeticsMenu = false;
    private static int selectedTab = 0;
    private static final String[] TABS = { "Capes", "Hats", "Wings", "Pets" };

    private static Identifier fetchedHeadId = null;
    private static UUID lastFetchedUuid = null;
    private static boolean isFetching = false;

    public static void render(DrawContext context, int mouseX, int mouseY, float delta) {
        SessionManager sessionManager = SessionManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        boolean authenticated = sessionManager.isAuthenticated();

        String displayName;
        String statusText;
        int statusColor;
        boolean showDot;

        if (authenticated) {
            /*
             * ModSession session = sessionManager.getSession();
             * displayName = session.getUsername();
             * if (displayName == null)
             * displayName = "Unknown User";
             */

            statusText = "Active";
            statusColor = 0xFFAAAAAA;
            showDot = true;
        } else {
            if (client.getSession() != null) {
                displayName = client.getSession().getUsername();
            } else {
                displayName = "Player";
            }
            // Final safety net
            if (displayName == null)
                displayName = client.getSession().getUsername();

            statusText = "Click To Sign In";
            statusColor = 0xFF55FF55; // Emerald for the "button"
            showDot = false;
        }

        displayName = client.getSession().getUsername();

        int width = WIDTH;
        int height = HEIGHT;
        int x = PADDING;
        int y = PADDING;

        // Draw background
        context.fill(x, y, x + width, y + height, BACKGROUND_COLOR);

        // Manual 1px border
        int borderColor = authenticated ? 0xFF55FF55 : 0xFF555555;
        context.fill(x, y, x + width, y + 1, borderColor);
        context.fill(x, y + height - 1, x + width, y + height, borderColor);
        context.fill(x, y, x + 1, y + height, borderColor);
        context.fill(x + width - 1, y, x + width, y + height, borderColor);

        // Draw head
        drawPlayerHead(context, client, x + 6, y + 6, HEAD_SIZE);

        // Draw text area
        int textX = x + 26;
        int textY = y + 5;

        int nameColor = authenticated ? 0xFFFFAA00 : 0xFFFFFFFF; // Gold or White

        // Username
        context.drawText(client.textRenderer, Text.literal(displayName), textX, textY, nameColor, true);

        // Cosmetics Button
        int btnSize = 20;
        int btnX = x + width + 5;
        int btnY = y + (height - btnSize) / 2;

        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
        int btnColor = isHovered ? 0xDD444444 : BACKGROUND_COLOR;

        context.fill(btnX, btnY, btnX + btnSize, btnY + btnSize, btnColor);

        // Use same border color as profile
        context.fill(btnX, btnY, btnX + btnSize, btnY + 1, borderColor);
        context.fill(btnX, btnY + btnSize - 1, btnX + btnSize, btnY + btnSize, borderColor);
        context.fill(btnX, btnY, btnX + 1, btnY + btnSize, borderColor);
        context.fill(btnX + btnSize - 1, btnY, btnX + btnSize, btnY + btnSize, borderColor);

        String btnText = "C";
        int btnTextWidth = client.textRenderer.getWidth(btnText);
        context.drawText(client.textRenderer, Text.literal(btnText), btnX + (btnSize - btnTextWidth) / 2,
                btnY + (btnSize - 8) / 2 + 1, 0xFFFFFFFF, true);

        if (isHovered && !showCosmeticsMenu) {
            String tooltip = "Cosmetics";
            int tw = client.textRenderer.getWidth(tooltip);
            context.fill(btnX + btnSize / 2 - tw / 2 - 2, btnY + btnSize + 2, btnX + btnSize / 2 + tw / 2 + 2,
                    btnY + btnSize + 14, 0xAA000000);
            context.drawText(client.textRenderer, Text.literal(tooltip), btnX + btnSize / 2 - tw / 2,
                    btnY + btnSize + 4, 0xFFFFFFFF, true);
        }

        // Status line
        int statusY = textY + 10;
        if (showDot) {
            context.fill(textX, statusY + 2, textX + 3, statusY + 5, 0xFF55FF55); // Emerald dot
            context.drawText(client.textRenderer, Text.literal(statusText), textX + 5, statusY, statusColor, true);
        } else {
            // "Click To Sign In" - Background removed as requested
            context.drawText(client.textRenderer, Text.literal(statusText), textX, statusY, statusColor, true);

            // Hover effect (if mouse is over)
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                context.fill(x, y, x + width, y + height, 0x22FFFFFF);
            }
        }

        if (showCosmeticsMenu) {
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // Darken background
            context.fill(0, 0, screenWidth, screenHeight, 0x88000000);

            int overlayWidth = (int) (screenWidth * 0.8);
            int overlayHeight = (int) (screenHeight * 0.8);
            int overlayX = (screenWidth - overlayWidth) / 2;
            int overlayY = (screenHeight - overlayHeight) / 2;

            // Main Background
            context.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + overlayHeight, BACKGROUND_COLOR);

            // 1px Border
            context.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + 1, borderColor);
            context.fill(overlayX, overlayY + overlayHeight - 1, overlayX + overlayWidth, overlayY + overlayHeight,
                    borderColor);
            context.fill(overlayX, overlayY, overlayX + 1, overlayY + overlayHeight, borderColor);
            context.fill(overlayX + overlayWidth - 1, overlayY, overlayX + overlayWidth, overlayY + overlayHeight,
                    borderColor);

            // Header separator
            int headerHeight = 30;
            context.fill(overlayX, overlayY + headerHeight, overlayX + overlayWidth, overlayY + headerHeight + 1,
                    0xFF555555);

            // Title
            context.drawText(client.textRenderer, Text.literal("COSMETICS"), overlayX + 10, overlayY + 11, 0xFFFFAA00,
                    true);

            // Close button (X)
            int closeBtnSize = 20;
            int closeBtnX = overlayX + overlayWidth - closeBtnSize - 5;
            int closeBtnY = overlayY + (headerHeight - closeBtnSize) / 2;
            boolean closeHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize && mouseY >= closeBtnY
                    && mouseY <= closeBtnY + closeBtnSize;

            if (closeHovered) {
                context.fill(closeBtnX, closeBtnY, closeBtnX + closeBtnSize, closeBtnY + closeBtnSize, 0x44FF0000);
            }
            String closeText = "X";
            int closeTextWidth = client.textRenderer.getWidth(closeText);
            context.drawText(client.textRenderer, Text.literal(closeText),
                    closeBtnX + (closeBtnSize - closeTextWidth) / 2, closeBtnY + (closeBtnSize - 8) / 2 + 1, 0xFFFFFFFF,
                    true);

            // Tabs
            int tabX_off = overlayX + 110;
            for (int i = 0; i < TABS.length; i++) {
                String tab = TABS[i];
                int tabWidth = client.textRenderer.getWidth(tab);
                boolean isSelected = (i == selectedTab);
                int color = isSelected ? 0xFFFFAA00 : 0xFFAAAAAA;

                // Hover effect for tab
                boolean tabHovered = mouseX >= tabX_off - 5 && mouseX <= tabX_off + tabWidth + 5
                        && mouseY >= overlayY + 5
                        && mouseY <= overlayY + 25;
                if (tabHovered && !isSelected)
                    color = 0xFFFFFFFF;

                context.drawText(client.textRenderer, Text.literal(tab), tabX_off, overlayY + 11, color, true);

                if (isSelected) {
                    // Selection underline
                    context.fill(tabX_off, overlayY + 22, tabX_off + tabWidth, overlayY + 23, 0xFFFFAA00);
                }

                tabX_off += tabWidth + 25;
            }

            // Body Content Mockup based on selected tab
            String bodyText = "Currently viewing: " + TABS[selectedTab];
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(bodyText), overlayX + overlayWidth / 2,
                    overlayY + overlayHeight / 2 - 10, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Grid coming soon..."),
                    overlayX + overlayWidth / 2, overlayY + overlayHeight / 2 + 10, 0xFFAAAAAA);
        }

    }

    public static boolean onMouseClicked(net.minecraft.client.gui.Click click, boolean leftButton) {
        double mx = click.x();
        double my = click.y();

        // Check Cosmetics Button
        int btnSize = 20;
        int btnX = PADDING + WIDTH + 5;
        int btnY = PADDING + (HEIGHT - btnSize) / 2;

        if (mx >= btnX && mx <= btnX + btnSize && my >= btnY && my <= btnY + btnSize) {
            showCosmeticsMenu = !showCosmeticsMenu;
            return true;
        }

        if (showCosmeticsMenu) {
            MinecraftClient client = MinecraftClient.getInstance();
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();
            int overlayWidth = (int) (screenWidth * 0.8);
            int overlayHeight = (int) (screenHeight * 0.8);
            int overlayX = (screenWidth - overlayWidth) / 2;
            int overlayY = (screenHeight - overlayHeight) / 2;
            int headerHeight = 30;

            // Close button check
            int closeBtnSize = 20;
            int closeBtnX = overlayX + overlayWidth - closeBtnSize - 5;
            int closeBtnY = overlayY + (headerHeight - closeBtnSize) / 2;

            if (mx >= closeBtnX && mx <= closeBtnX + closeBtnSize && my >= closeBtnY
                    && my <= closeBtnY + closeBtnSize) {
                showCosmeticsMenu = false;
                return true;
            }

            // Tab clicks
            int tabX_off = overlayX + 110;
            for (int i = 0; i < TABS.length; i++) {
                String tab = TABS[i];
                int tabWidth = client.textRenderer.getWidth(tab);
                if (mx >= tabX_off - 5 && mx <= tabX_off + tabWidth + 5 && my >= overlayY + 5 && my <= overlayY + 25) {
                    selectedTab = i;
                    return true;
                }
                tabX_off += tabWidth + 25;
            }

            return true;
        }

        SessionManager sessionManager = SessionManager.getInstance();
        if (sessionManager.isAuthenticated())
            return false;

        // Check if the click is within the overlay bounds
        if (mx >= PADDING && mx <= PADDING + WIDTH && my >= PADDING && my <= PADDING + HEIGHT) {
            LOGGER.info("[AccountOverlay] User clicked Sign In - Starting Device Flow...");
            sessionManager.startDeviceFlowManually();
            return true;
        }
        return false;
    }

    private static void drawPlayerHead(DrawContext context, MinecraftClient client, int x, int y, int size) {
        UUID uuid = client.getGameProfile().id();

        if (uuid == null) {
            return;
        }

        if (!uuid.equals(lastFetchedUuid)) {
            LOGGER.info("[AccountOverlay] UUID changed to {}, fetching head...", uuid);
            fetchHead(uuid);
        }

        if (fetchedHeadId != null) {
            RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, fetchedHeadId, x, y, 0.0f, 0.0f, size, size, 64, 64, 64, 64, -1);
        }
    }

    private static void fetchHead(UUID uuid) {
        if (isFetching)
            return;
        isFetching = true;
        lastFetchedUuid = uuid;

        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("[AccountOverlay] Downloading head for {}", uuid);
                URL url = URI.create("https://minotar.net/helm/" + uuid.toString() + "/64.png").toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (CraftCorps Cosmetics Mod)");
                connection.connect();

                int code = connection.getResponseCode();
                if (code == 200) {
                    try (InputStream is = connection.getInputStream()) {
                        NativeImage image = NativeImage.read(is);
                        MinecraftClient.getInstance().execute(() -> {
                            try {
                                String textureId = "craftcorps_head_" + uuid.toString();
                                Identifier id = Identifier.of("craftcorps-cosmetics", textureId);

                                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> textureId, image);
                                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

                                fetchedHeadId = id;
                                isFetching = false;
                            } catch (Exception e) {
                                LOGGER.error("[AccountOverlay] Failed to register texture", e);
                                isFetching = false;
                            }
                        });
                    }
                } else {
                    isFetching = false;
                }
            } catch (Exception e) {
                LOGGER.error("[AccountOverlay] Exception fetching head", e);
                isFetching = false;
            }
        });
    }
}
