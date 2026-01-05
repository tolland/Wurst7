/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum BlockPlacer {
    ;

    private static final WurstClient WURST = WurstClient.INSTANCE;
    private static final Minecraft MC = WurstClient.MC;
    private static final IMinecraftClient IMC = WurstClient.IMC;

    public static boolean placeOneBlock(BlockPos pos) {
        BlockPlacingParams params = getBlockPlacingParams(pos);
        if (params == null)
            return false;

        // face block
        WURST.getRotationFaker().faceVectorPacket(params.hitVec);

        // place block
        IMC.getInteractionManager().rightClickBlock(params.neighbor,
                params.side, params.hitVec);

        return true;
    }

    /**
     * Returns everything you need to place a block at the given position, such
     * as the position of the block to place against (can be a neighbor or the
     * block itself), the side of that block to place on, the hit vector, the
     * squared distance to that hit vector, and whether there is line of sight
     * to that hit vector.
     */
    public static BlockPlacingParams getBlockPlacingParams(BlockPos pos) {
        // if there is a replaceable block at the position, we need to place
        // against the block itself instead of a neighbor
        if (BlockUtils.canBeClicked(pos)
                && BlockUtils.getState(pos).canBeReplaced()) {
            // the parameters for this happen to be the same as for breaking
            // the block, so we can just use BlockBreaker to get them
            BlockBreakingParams breakParams =
                    BlockBreaker.getBlockBreakingParams(pos);

            // should never happen, but just in case
            if (breakParams == null)
                return null;

            return new BlockPlacingParams(pos, breakParams.side(),
                    breakParams.hitVec(), breakParams.distanceSq(),
                    breakParams.lineOfSight());
        }

        Direction[] sides = Direction.values();
        Vec3[] hitVecs = new Vec3[sides.length];

        // get hit vectors for all usable sides
        for (int i = 0; i < sides.length; i++) {
            BlockPos neighbor = pos.relative(sides[i]);
            BlockState state = BlockUtils.getState(neighbor);
            VoxelShape shape = state.getShape(MC.level, neighbor);

            // if neighbor has no shape or is replaceable, it can't be used
            if (shape.isEmpty() || state.canBeReplaced())
                continue;

            AABB box = shape.bounds();
            Vec3 halfSize = new Vec3(box.maxX - box.minX, box.maxY - box.minY,
                    box.maxZ - box.minZ).scale(0.5);
            Vec3 center = Vec3.atLowerCornerOf(neighbor).add(box.getCenter());

            Vec3i dirVec = sides[i].getOpposite().getUnitVec3i();
            Vec3 relHitVec = new Vec3(halfSize.x * dirVec.getX(),
                    halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
            hitVecs[i] = center.add(relHitVec);
        }

        Vec3 eyesPos = RotationUtils.getEyesPos();
        Vec3 posVec = Vec3.atCenterOf(pos);

        double distanceSqToPosVec = eyesPos.distanceToSqr(posVec);
        double[] distancesSq = new double[sides.length];
        boolean[] linesOfSight = new boolean[sides.length];

        // calculate distances and line of sight
        for (int i = 0; i < sides.length; i++) {
            // skip unusable sides
            if (hitVecs[i] == null) {
                distancesSq[i] = Double.MAX_VALUE;
                continue;
            }

            distancesSq[i] = eyesPos.distanceToSqr(hitVecs[i]);

            // to place against a neighbor in front of the block, we would
            // have to place against that neighbor's rear face, which can't
            // possibly have line of sight
            if (distancesSq[i] <= distanceSqToPosVec)
                continue;

            linesOfSight[i] = BlockUtils.hasLineOfSight(eyesPos, hitVecs[i]);
        }

        // decide which side to use
        Direction side = sides[0];
        for (int i = 1; i < sides.length; i++) {
            int bestSide = side.ordinal();

            // skip unusable sides
            if (hitVecs[i] == null)
                continue;

            if (!isLikelyInteractable(MC.level, pos)) {
                continue;
            }

            // prefer sides with LOS
            if (!linesOfSight[bestSide] && linesOfSight[i]) {
                side = sides[i];
                continue;
            }

            if (linesOfSight[bestSide] && !linesOfSight[i])
                continue;

            // then pick the furthest side
            if (distancesSq[i] > distancesSq[bestSide])
                side = sides[i];
        }

        // if no usable side was found, return null
        if (hitVecs[side.ordinal()] == null)
            return null;

        return new BlockPlacingParams(pos.relative(side), side.getOpposite(),
                hitVecs[side.ordinal()], distancesSq[side.ordinal()],
                linesOfSight[side.ordinal()]);
    }

    /*
     * Helper: guess whether a block will consume right-click.
     * - fast path: block entity that is a MenuProvider (chests, furnaces, etc.)
     * - reflection: detect if the concrete block class (or a superclass under
     * Block)
     * declares an interaction-like method (common names mapped in decompiled
     * code)
     * - cache results per block class to avoid repeated reflection overhead
     */
    private static final Map<Class<?>, Boolean> LIKELY_INTERACTABLE_CACHE =
            new ConcurrentHashMap<>();

    private static boolean isLikelyInteractable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // 1) block entity that provides menus -> almost certainly interactive
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider)
            return true;

        // 2) class-based cache
        Class<?> cls = block.getClass();
        Boolean cached = LIKELY_INTERACTABLE_CACHE.get(cls);
        if (cached != null)
            return cached;

        boolean result = detectInteractableOnClass(cls);
        LIKELY_INTERACTABLE_CACHE.put(cls, result);
        return result;
    }

    /*
     * Reflection-based detection: look for declared/overridden methods that
     * usually
     * indicate right-click handling or menu provision. This only checks for
     * method
     * declarations (no invocation), so it's side-effect free.
     */
    private static boolean detectInteractableOnClass(Class<?> cls) {
        Class<?> current = cls;
        // stop at net.minecraft.world.level.block.Block to avoid scanning
        // unrelated ancestors
        while (current != null && current != Block.class) {
            try {
                // common mapped signatures in decompiled code:
                // InteractionResult use(BlockState, Level, BlockPos, Player,
                // InteractionHand, BlockHitResult)
                current.getDeclaredMethod("use",
                        net.minecraft.world.level.block.state.BlockState.class,
                        Level.class, BlockPos.class, Player.class,
                        InteractionHand.class, BlockHitResult.class);
                return true;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                // some blocks expose/get a MenuProvider via
                // getMenuProvider(BlockState, Level, BlockPos)
                current.getDeclaredMethod("getMenuProvider",
                        net.minecraft.world.level.block.state.BlockState.class,
                        Level.class, BlockPos.class);
                return true;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                // chest/decor patterns: protected InteractionResult
                // useWithoutItem(...)
                current.getDeclaredMethod("useWithoutItem",
                        net.minecraft.world.level.block.state.BlockState.class,
                        Level.class, BlockPos.class, Player.class,
                        BlockHitResult.class);
                return true;
            } catch (NoSuchMethodException ignored) {
            }

            current = current.getSuperclass();
        }

        // fallback: no obvious interactive methods declared
        return false;
    }

    public static record BlockPlacingParams(BlockPos neighbor, Direction side,
                                            Vec3 hitVec, double distanceSq, boolean lineOfSight) {
        public BlockHitResult toHitResult() {
            return new BlockHitResult(hitVec, side, neighbor, false);
        }
    }
}
