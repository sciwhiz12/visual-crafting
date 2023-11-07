package dev.cerus.visualcrafting.v19r3;

import com.mojang.math.Transformation;
import dev.cerus.visualcrafting.api.config.Config;
import dev.cerus.visualcrafting.api.version.FakeItemDisplay;
import dev.cerus.visualcrafting.api.version.FakeMap;
import dev.cerus.visualcrafting.api.version.Feature;
import dev.cerus.visualcrafting.api.version.VersionAdapter;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutMap;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class VersionAdapter19R3 extends VersionAdapter {

    private Config config;
    private BiConsumer<Player, Integer> entityClickCallback;
    private int nextEntityId;
    private int nextMapId;
    private Field netManField;

    private NetworkManager getNetworkManager(final PlayerConnection b) {
        try {
            if (this.netManField == null) {
                this.netManField = PlayerConnection.class.getDeclaredField("h");
                this.netManField.setAccessible(true);
            }
            return (NetworkManager) this.netManField.get(b);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(final Config config, final BiConsumer<Player, Integer> entityClickCallback) {
        this.config = config;
        this.entityClickCallback = entityClickCallback;
        this.nextEntityId = config.entityIdRangeMin();
        this.nextMapId = config.mapIdRangeMin();
    }

    @Override
    public void inject(final Player player) {
        if (this.config.enablePacketListening()) {
            final NetworkManager netMan = this.getNetworkManager(((CraftPlayer) player).getHandle().b);
            netMan.m.pipeline()
                    .addBefore("packet_handler", "visual_crafting", new ChannelDuplexHandler() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
                            if (msg instanceof final PacketPlayInUseEntity useEntity) {
                                VersionAdapter19R3.this.handlePacketIn(player, useEntity);
                            }
                            super.channelRead(ctx, msg);
                        }
                    });
        }
    }

    private void handlePacketIn(final Player player, final PacketPlayInUseEntity packet) {
        try {
            final Field a = packet.getClass().getDeclaredField("a");
            a.setAccessible(true);
            this.entityClickCallback.accept(player, (Integer) a.get(packet));
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int spawnItemFrame(final Location location, final BlockFace direction) {
        final int eid = this.getNewEntityId();
        final PacketPlayOutSpawnEntity packet = new PacketPlayOutSpawnEntity(
                eid,
                UUID.randomUUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                direction == BlockFace.DOWN ? 90 : direction == BlockFace.UP ? -90 : 0,
                switch (direction) {
                    case NORTH -> -180;
                    case EAST -> -90;
                    case WEST -> 90;
                    default -> 0;
                },
                EntityTypes.af,
                switch (direction) {
                    case UP -> 1;
                    case NORTH -> 2;
                    case SOUTH -> 3;
                    case WEST -> 4;
                    case EAST -> 5;
                    default -> 0;
                },
                new Vec3D(0, 0, 0),
                switch (direction) {
                    case NORTH -> -180;
                    case EAST -> -90;
                    case WEST -> 90;
                    default -> 0;
                }
        );
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
        return eid;
    }

    @Override
    public int spawnItemDisplay(final FakeItemDisplay itemDisplay) {
        final int eid = this.getNewEntityId();
        final PacketPlayOutSpawnEntity packet = new PacketPlayOutSpawnEntity(
                eid,
                UUID.randomUUID(),
                itemDisplay.getLocation().getBlockX(),
                itemDisplay.getLocation().getBlockY(),
                itemDisplay.getLocation().getBlockZ(),
                0,
                0,
                EntityTypes.ae,
                0,
                new Vec3D(0, 0, 0),
                0
        );
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
        return eid;
    }

    @Override
    public void updateItemFrame(final int frameId, final ItemStack itemStack, final Rotation rotation, final boolean invisible) {
        final PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(frameId, Arrays.asList(
                new DataWatcher.b<>(8, DataWatcherRegistry.h, CraftItemStack.asNMSCopy(itemStack)),
                new DataWatcher.b<>(9, DataWatcherRegistry.b, rotation.ordinal()),
                new DataWatcher.b<>(0, DataWatcherRegistry.a, (byte) (invisible ? 0x20 : 0))
        ));
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
    }

    @Override
    public void updateItemDisplay(final int displayId, final FakeItemDisplay itemDisplay) {
        final Transformation nmsTransf = new Transformation(itemDisplay.getTransformationMatrix());
        final PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(displayId, Arrays.asList(
                new DataWatcher.b<>(10, DataWatcherRegistry.A, nmsTransf.d()),
                new DataWatcher.b<>(11, DataWatcherRegistry.A, nmsTransf.f()),
                new DataWatcher.b<>(12, DataWatcherRegistry.B, nmsTransf.e()),
                new DataWatcher.b<>(13, DataWatcherRegistry.B, nmsTransf.g()),
                new DataWatcher.b<>(22, DataWatcherRegistry.h, CraftItemStack.asNMSCopy(itemDisplay.getItemStack())),
                new DataWatcher.b<>(23, DataWatcherRegistry.a, (byte) itemDisplay.getTransform().ordinal())
        ));
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
    }

    @Override
    public void destroyEntity(final int entityId) {
        final PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(entityId);
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
    }

    @Override
    public FakeMap createMap() {
        return this.createMap(this.getNewMapId());
    }

    @Override
    public void sendMap(final FakeMap map) {
        final PacketPlayOutMap packet = new PacketPlayOutMap(
                map.getId(),
                (byte) 0,
                true,
                Collections.emptyList(),
                new WorldMap.b(0,
                        0,
                        128,
                        128,
                        this.getMapData(map))
        );
        Bukkit.getOnlinePlayers().forEach(player -> this.sendPacket(player, packet));
    }

    @Override
    public EnumSet<Feature> getImplementedFeatures() {
        return FEATURES_DISPLAY;
    }

    private void sendPacket(final Player player, final Packet<?> packet) {
        ((CraftPlayer) player).getHandle().b.a(packet);
    }

    private int getNewEntityId() {
        if (this.nextEntityId >= this.config.entityIdRangeMax()) {
            this.nextEntityId = this.config.entityIdRangeMin();
            return this.nextEntityId;
        } else {
            return this.nextEntityId++;
        }
    }

    private int getNewMapId() {
        if (this.nextMapId >= this.config.mapIdRangeMax()) {
            this.nextMapId = this.config.mapIdRangeMin();
            return this.nextMapId;
        } else {
            return this.nextMapId++;
        }
    }

}
