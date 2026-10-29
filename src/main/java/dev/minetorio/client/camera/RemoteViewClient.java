package dev.minetorio.client.camera;

import dev.minetorio.Minetorio;
import dev.minetorio.client.map.MapScreen;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.map.ClientWatchedChunks;
import dev.minetorio.content.map.RemotePayloads;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.camera.RemoteArea;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the remote view ({@link RemoteView}): from the full map, at the closest zoom, a
 * click (or one more wheel notch in) over a chunk a radar watches or the player sees himself asks
 * the server for a remote view there ({@link #requestEnter}); on its go-ahead the map closes and
 * {@link BuildCamera} hangs over that place. WASD fly the camera, Q/E turn it, the wheel zooms, Esc
 * (or the map key) goes back to the map, where Esc closes the map.
 *
 * <p>Every click is handled here, never by vanilla: right-click with a Minetorio block item or a
 * ghost item places a ghost (the ghost key is implied, so {@code DragClient} lays single ghosts and
 * ghost drag lines as usual), right-click on an assembler or furnace (without sneak) opens its GUI
 * (look, change the recipe; no item moves), left-click on a ghost removes it, pick-block gives ghost
 * items as usual. Anything else does nothing. Only targets in the view's area count
 * ({@link #inArea}); the server checks the same.
 *
 * <p>The focus is sent to the server when its chunk changes, at most every
 * {@value RemoteArea#CLIENT_MOVE_INTERVAL} ticks. A small label at the top says the view is remote.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class RemoteViewClient {
    /** Ticks between ghost removals of the same block while the button is held. */
    private static final int REMOVE_REPEAT = 4;
    /** Ticks a refusal / closing message shows on the map. */
    private static final int MESSAGE_TICKS = 80;

    private static boolean pending;
    @Nullable
    private static ChunkPos sentChunk;
    private static int moveCooldown;
    @Nullable
    private static BlockPos lastRemoved;
    private static long lastRemovedTick;
    @Nullable
    private static Component message;
    private static int messageTicks;

    private RemoteViewClient() {}

    /** Installs the hook for the server's answers ({@link RemotePayloads.State}); called on client setup. */
    public static void register() {
        RemoteView.clientHandler = RemoteViewClient::onState;
    }

    // --- eligibility ------------------------------------------------------------------------------

    /** Whether the view may be over chunk {@code (cx, cz)}: a radar watches it or the player sees it himself. */
    public static boolean eligible(int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) return false;
        if (ClientWatchedChunks.isWatched(cx, cz)) return true;
        ChunkPos own = mc.player.chunkPosition();
        return RemoteArea.ownView(own.x, own.z, Math.max(2, mc.options.getEffectiveRenderDistance()), cx, cz);
    }

    /** Whether block {@code pos} is in the area of the open remote view. */
    public static boolean inArea(BlockPos pos) {
        if (!BuildCamera.isRemote()) return false;
        return RemoteArea.inArea(RemoteArea.chunkOf(BuildCamera.focusX()), RemoteArea.chunkOf(BuildCamera.focusZ()),
                pos.getX() >> 4, pos.getZ() >> 4, RemoteViewClient::eligible);
    }

    /** A message about the last refusal or closing (shown on the map for a few seconds), or null. */
    @Nullable
    public static Component message() {
        return messageTicks > 0 ? message : null;
    }

    // --- switching ---------------------------------------------------------------------------------

    /** Asks the server for a remote view over block column {@code (x, z)} (from the map); false when not eligible here. */
    public static boolean requestEnter(double x, double z) {
        if (!eligible(RemoteArea.chunkOf(x), RemoteArea.chunkOf(z))) {
            show(Component.translatable("minetorio.remote.not_watched"));
            return false;
        }
        pending = true;
        PacketDistributor.sendToServer(new RemotePayloads.Enter(x, z));
        return true;
    }

    /** Leaves the remote view and shows the map centred on where the camera was. */
    public static void leaveToMap() {
        if (!BuildCamera.isRemote()) return;
        double x = BuildCamera.focusX(), z = BuildCamera.focusZ();
        PacketDistributor.sendToServer(new RemotePayloads.Exit());
        close();
        Minecraft.getInstance().setScreen(new MapScreen(x, z));
    }

    private static void close() {
        BuildCamera.exitRemote();
        RemoteView.clientActive = false;
        sentChunk = null;
        lastRemoved = null;
    }

    /** The server's answer ({@link RemotePayloads.State}). */
    private static void onState(boolean active, double x, double z, RemoteView.Reason reason) {
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            if (BuildCamera.isRemote()) { // a refused move: back to where the server has the focus
                if (reason != RemoteView.Reason.NONE) {
                    BuildCamera.setFocus(x, z);
                    sentChunk = new ChunkPos(RemoteArea.chunkOf(x), RemoteArea.chunkOf(z));
                }
                return;
            }
            if (!pending || mc.player == null) { // we left meanwhile
                PacketDistributor.sendToServer(new RemotePayloads.Exit());
                return;
            }
            pending = false;
            BuildCamera.enterRemote(x, mc.player.getY(), z);
            RemoteView.clientActive = true;
            sentChunk = new ChunkPos(RemoteArea.chunkOf(x), RemoteArea.chunkOf(z));
            if (mc.screen instanceof MapScreen) mc.setScreen(null);
            return;
        }
        pending = false;
        if (reason == RemoteView.Reason.NOT_WATCHED) show(Component.translatable("minetorio.remote.not_watched"));
        else if (reason == RemoteView.Reason.LOST_COVERAGE) show(Component.translatable("minetorio.remote.lost_coverage"));
        else if (reason == RemoteView.Reason.NOT_ALLOWED) show(Component.translatable("minetorio.remote.not_allowed"));
        if (BuildCamera.isRemote()) {
            boolean dead = mc.player == null || !mc.player.isAlive();
            close();
            if (mc.screen instanceof AbstractContainerScreen<?> && mc.player != null) mc.player.closeContainer();
            if (!dead && mc.level != null && mc.level.dimension() == Level.OVERWORLD) mc.setScreen(new MapScreen(x, z));
        }
    }

    private static void show(Component text) {
        message = text;
        messageTicks = MESSAGE_TICKS;
    }

    // --- ticking -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (messageTicks > 0) messageTicks--;
        Minecraft mc = Minecraft.getInstance();
        if (!BuildCamera.isRemote()) {
            if (RemoteView.clientActive) close();
            return;
        }
        if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            close();
            return;
        }
        if (moveCooldown > 0) moveCooldown--;
        ChunkPos now = new ChunkPos(RemoteArea.chunkOf(BuildCamera.focusX()), RemoteArea.chunkOf(BuildCamera.focusZ()));
        if (moveCooldown == 0 && !now.equals(sentChunk)) {
            PacketDistributor.sendToServer(new RemotePayloads.Move(BuildCamera.focusX(), BuildCamera.focusZ()));
            sentChunk = now;
            moveCooldown = RemoteArea.CLIENT_MOVE_INTERVAL;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        RemoteView.clientActive = false;
        sentChunk = null;
        messageTicks = 0;
    }

    // --- clicks ------------------------------------------------------------------------------------

    /** Every click in the remote view lands here first; what is not a ghost action or a GUI is dropped. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!BuildCamera.isRemote() || event.isPickBlock()) return; // pick-block: ghost items as usual (GhostItemClient)
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
        if (event.isAttack()) {
            drop(event);
            if (hit != null && mc.level.getBlockState(hit.getBlockPos()).is(ModBlocks.GHOST.get())) removeGhost(mc, hit.getBlockPos());
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            drop(event);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (dev.minetorio.content.blueprint.BlueprintStacks.isBlueprintLike(held)) return; // BlueprintClient pastes / selects
        if (hit != null && !mc.options.keyShift.isDown() && hasGui(mc.level, hit.getBlockPos())) {
            drop(event);
            PacketDistributor.sendToServer(new RemotePayloads.OpenMenu(hit.getBlockPos()));
            return;
        }
        if (held.getItem() instanceof GhostItem || HandGhosts.canGhost(held)) return; // DragClient places the ghost (or line)
        drop(event);
    }

    private static void drop(InputEvent.InteractionKeyMappingTriggered event) {
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void removeGhost(Minecraft mc, BlockPos pos) {
        long now = mc.level.getGameTime();
        if (pos.equals(lastRemoved) && now - lastRemovedTick < REMOVE_REPEAT) return;
        lastRemoved = pos.immutable();
        lastRemovedTick = now;
        PacketDistributor.sendToServer(new RemotePayloads.RemoveGhost(pos));
    }

    /** Whether the block at {@code pos} is (part of) a machine whose GUI the remote view opens. */
    private static boolean hasGui(Level level, BlockPos pos) {
        BlockPos at = level.getBlockState(pos).getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.controllerPos(level, pos) : pos;
        if (at == null) return false;
        BlockEntity be = level.getBlockEntity(at);
        return be instanceof AssemblerBlockEntity || be instanceof StoneFurnaceBlockEntity
                || be instanceof dev.minetorio.content.oil.OilProcessingBlockEntity
                || be instanceof dev.minetorio.content.oil.SteelFurnaceBlockEntity;
    }

    // --- HUD ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!BuildCamera.isRemote() || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Component label = Component.translatable("minetorio.remote.label");
        int w = mc.font.width(label);
        int x = (g.guiWidth() - w) / 2;
        g.fill(x - 4, 4, x + w + 4, 17, 0x90000000);
        g.drawString(mc.font, label, x, 7, 0xFF7FD4FF, false);
        Component help = Component.translatable("minetorio.remote.help");
        g.drawCenteredString(mc.font, help, g.guiWidth() / 2, 20, 0xFFB0B0B0);
    }
}
