package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.screen.TradeCatalogPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the config-gated trade controls (set level, re-roll, catalog) to the vanilla trade screen,
 * and hosts the catalogue panel docked against its left edge.
 *
 * <p>Control buttons send their action through the vanilla ButtonClick packet (see
 * {@link MerchantScreenHandlerMixin}); only the catalogue's <em>reply</em> needs a custom packet,
 * because it carries per-trade metadata no vanilla screen sync can express.
 *
 * <p>Extends the real superclass {@link AbstractContainerScreen} so inherited members ({@code x}, {@code y},
 * {@code backgroundWidth}, {@code width}, {@code addDrawableChild}, {@code getScreenHandler}) are
 * used directly without shadows.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    private MerchantScreenMixin() {
        super(null, null, null); // never executed; present only to satisfy the compiler
    }

    /**
     * Vanilla's trade-list scrollbar drag flag. {@code MerchantScreen.mouseClicked} is the only
     * place it is ever cleared, so cancelling that method would strand a drag in progress.
     */
    @Shadow
    private boolean isDragging;

    /** Which trade the right-hand panel shows. Vanilla indexes the offer list with it unguarded. */
    @Shadow
    private int shopItem;

    @Unique private int villagertradingplus$level = 1;
    @Unique private Button villagertradingplus$levelDisplay;
    @Unique private Button villagertradingplus$setLevelButton;
    @Unique private Button villagertradingplus$rerollLevelButton;
    @Unique private Button villagertradingplus$catalogButton;
    @Unique private TradeCatalogPanel villagertradingplus$catalog;
    @Unique private boolean villagertradingplus$shortLabels;

    @Inject(method = "init", at = @At("TAIL"))
    private void villagertradingplus$addControls(CallbackInfo ci) {
        boolean allowReroll = VillagerTradingPlus.CONFIG.allow_trade_reroll;
        boolean allowSetLevel = VillagerTradingPlus.CONFIG.allow_set_villager_level;
        boolean allowView = VillagerTradingPlus.CONFIG.allow_view_all_trades;
        if (!allowReroll && !allowSetLevel && !allowView) {
            return;
        }

        if (allowView && this.villagertradingplus$catalog == null) {
            this.villagertradingplus$catalog = new TradeCatalogPanel(Minecraft.getInstance());
            // First init of this screen: drop whatever the last merchant sent. Guarded on the panel
            // being absent because init also re-runs on window resize, which must keep the data.
            TradeCatalogClientState.clear();
        }

        int step = 23;
        int gap = 5;
        int margin = 2;
        int maxWidth = 110;
        int minWidth = 68;

        // The control column takes the right gutter and the catalogue takes the left, so the two
        // never compete. The column is width-adaptive because the gutter is not: MerchantScreen is
        // 276 units wide and centred, so at 1920x1080 with GUI scale 4 the screen is only 480 units
        // across and each gutter only 102, too narrow for a fixed 110-wide column.
        int gutter = this.width - (this.leftPos + this.imageWidth) - gap - margin;
        int rows = 1 + (allowSetLevel ? 1 : 0) + (allowReroll ? 2 : 0) + (allowView ? 1 : 0);

        int w;
        int baseX;
        int row;
        if (gutter >= minWidth) {
            w = Math.min(maxWidth, gutter);
            baseX = this.leftPos + this.imageWidth + gap;
            row = this.topPos;
        } else {
            // Only reachable on a window narrower than any standard resolution produces. Sit below
            // the trade panel and, if that runs off the bottom, ride the bottom edge; overlapping
            // the hotbar is far less destructive than overlapping the trades.
            w = maxWidth;
            baseX = Math.max(margin, Math.min(this.leftPos, this.width - w - margin));
            row = Math.min(this.topPos + this.imageHeight + 4, this.height - rows * step - margin);
        }
        row = Math.max(margin, row);

        // "Re-roll level 1" is about 74px wide, so below this the full labels clip and the short
        // variants are used instead.
        this.villagertradingplus$shortLabels = w < 90;

        // Shared level selector (used by set-level, per-level reroll and catalog). The arrows shrink
        // alongside the labels so the readout between them keeps a usable width.
        int arrowWidth = villagertradingplus$shortLabels ? 16 : 20;
        villagertradingplus$levelDisplay = addRenderableWidget(Button.builder(villagertradingplus$levelText(), b -> {})
                .bounds(baseX + arrowWidth + 4, row, w - 2 * arrowWidth - 8, 20).build());
        villagertradingplus$levelDisplay.active = false;
        addRenderableWidget(Button.builder(Component.literal("<"),
                b -> villagertradingplus$changeLevel(-1)).bounds(baseX, row, arrowWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> villagertradingplus$changeLevel(1)).bounds(baseX + w - arrowWidth, row, arrowWidth, 20).build());
        row += step;

        if (allowSetLevel) {
            villagertradingplus$setLevelButton = addRenderableWidget(Button.builder(
                            villagertradingplus$label("set_level"),
                            b -> villagertradingplus$click(100 + villagertradingplus$level))
                    .bounds(baseX, row, w, 20).build());
            row += step;
        }

        if (allowReroll) {
            villagertradingplus$rerollLevelButton = addRenderableWidget(Button.builder(
                            villagertradingplus$label("reroll_level"),
                            b -> villagertradingplus$click(110 + villagertradingplus$level))
                    .bounds(baseX, row, w, 20).build());
            row += step;
            addRenderableWidget(Button.builder(
                            villagertradingplus$shortLabels
                                    ? Component.translatable("gui.villagertradingplus.reroll_all_short")
                                    : Component.translatable("gui.villagertradingplus.reroll_all"),
                            b -> villagertradingplus$click(120))
                    .bounds(baseX, row, w, 20).build());
            row += step;
        }

        if (allowView) {
            villagertradingplus$catalogButton = addRenderableWidget(Button.builder(
                            villagertradingplus$label("catalog_button"),
                            b -> villagertradingplus$toggleCatalog())
                    .bounds(baseX, row, w, 20).build());
        }
    }

    /** Picks the full or short label variant for a level-parameterised button. */
    @Unique
    private Component villagertradingplus$label(String key) {
        return Component.translatable("gui.villagertradingplus." + key + (villagertradingplus$shortLabels ? "_short" : ""),
                villagertradingplus$level);
    }

    /**
     * Drawn at TAIL so it lands after vanilla's own {@code drawMouseoverTooltip} and after
     * {@code Screen.render} has drawn the control buttons; the panel is entirely hand-drawn, so
     * there are no widgets of ours underneath for it to paint over.
     */
    /**
     * Keeps {@link #shopItem} inside the offer list.
     *
     * <p>{@code MerchantScreen.render} does {@code getRecipes().get(this.selectedIndex)} with no
     * bounds check, and setting a villager's level or re-rolling its trades replaces that list with
     * a shorter one while the screen stays open - the selection then points past the end and the
     * next frame throws. Clamped here rather than where the offers arrive, so it holds no matter
     * which side shortened the list or in what order the packets landed.
     */
    @Inject(method = "extractContents", at = @At("HEAD"))
    private void villagertradingplus$clampSelectedIndex(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int size = this.getMenu().getOffers().size();
        if (this.shopItem >= size) {
            this.shopItem = Math.max(0, size - 1);
        }
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void villagertradingplus$renderCatalog(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        villagertradingplus$layoutCatalog();
        this.villagertradingplus$catalog.render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        villagertradingplus$layoutCatalog();
        if (!this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            return;
        }

        // Vanilla would treat this as a click outside the GUI and throw the cursor stack on the
        // ground; cancel both halves of the click instead.
        this.isDragging = false;
        ((HandledScreenAccessor) this).villagertradingplus$setCancelNextRelease(true);

        int id = this.villagertradingplus$catalog.mouseClicked(mouseX, mouseY);
        if (id >= 0) {
            villagertradingplus$click(id);
        }
        cir.setReturnValue(true);
    }

    /**
     * Vanilla's {@code mouseScrolled} scrolls the trade list from anywhere on screen: it does no
     * bounds check and never calls {@code super}, so the panel has to claim the wheel explicitly.
     *
     * <p>Four parameters since 1.20.5: the wheel reports a horizontal axis as well. The panel is a
     * vertical list, so it reads {@code verticalAmount}, which is also the only axis vanilla's own
     * trade list looks at.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogScroll(double mouseX, double mouseY, double horizontalAmount,
                                                  double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        villagertradingplus$layoutCatalog();
        if (this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            this.villagertradingplus$catalog.mouseScrolled(verticalAmount);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void villagertradingplus$layoutCatalog() {
        this.villagertradingplus$catalog.layout(this.leftPos, this.topPos, this.width, this.height);
    }

    @Unique
    private void villagertradingplus$toggleCatalog() {
        int id = this.villagertradingplus$catalog.toggle(this.villagertradingplus$level);
        if (id >= 0) {
            villagertradingplus$click(id);
        }
    }

    @Unique
    private void villagertradingplus$changeLevel(int delta) {
        villagertradingplus$level = Mth.clamp(villagertradingplus$level + delta, 1, 5);
        if (villagertradingplus$levelDisplay != null) {
            villagertradingplus$levelDisplay.setMessage(villagertradingplus$levelText());
        }
        if (villagertradingplus$setLevelButton != null) {
            villagertradingplus$setLevelButton.setMessage(villagertradingplus$label("set_level"));
        }
        if (villagertradingplus$rerollLevelButton != null) {
            villagertradingplus$rerollLevelButton.setMessage(villagertradingplus$label("reroll_level"));
        }
        if (villagertradingplus$catalogButton != null) {
            villagertradingplus$catalogButton.setMessage(villagertradingplus$label("catalog_button"));
        }
    }

    @Unique
    private Component villagertradingplus$levelText() {
        return villagertradingplus$label("level");
    }

    @Unique
    private void villagertradingplus$click(int id) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(this.getMenu().containerId, id);
        }
    }
}
