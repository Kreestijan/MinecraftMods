package io.github.kreestijan.alchemizer.brewing.block;

import com.mojang.serialization.MapCodec;
import io.github.kreestijan.alchemizer.brewing.blockentity.BellowsBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bellows: the one still part that knows which way it is hung, and the first that moves.
 *
 * <p>Every other part is symmetric enough to be a fixed model. The bellows is not: its mounting
 * plate has to sit against the valve it draws through, and the accordion has to push away from it.
 * A horizontal facing is the whole difference. The validator still reads it as an ordinary bellows
 * on any of a valve's four side faces — it never looks at a block state — so this is appearance
 * only, and an unfaced bellows from an older world loads facing north.
 *
 * <p><b>The animation is drawn, not stepped.</b> A Java block model cannot move on its own, and
 * swapping block states every frame would rebuild the chunk section every frame — the wrong cost
 * for a modpack. So the moving bellows is drawn by {@code BellowsRenderer} from the client's own
 * clock. The state here only says whether it should be drawn: {@link #ACTIVE} switches the world
 * model between the rest pose and an empty model, once per activation.
 *
 * <p><b>The animation mode.</b> A player shift-right-clicks the bellows to cycle it between always
 * working, working only while the still brews, and standing still. The default is always, and it is
 * a block state, not item data: breaking the bellows and placing it again puts it back to always.
 * Only the while-brewing mode listens to the chamber, and the chamber only calls the bellows its
 * validator actually counts, so that mode has to be on a valve of the machine rather than merely
 * nearby.
 */
public class BellowsBlock extends StillPartBlock implements AnimatedStillPart, EntityBlock {

  /** The direction the mounting plate points: at the block the bellows was placed against. */
  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

  /** Whether the client should draw the moving bellows rather than the static rest model. */
  public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

  /** How the bellows decides to move. */
  public enum Mode implements StringRepresentable {
    /** Move all the time, with no still and no brew needed. The default. */
    ALWAYS("always"),
    /** Move only while a brew is progressing and the bellows is a counted part of the machine. */
    WHILE_BREWING("while_brewing"),
    /** Never move. */
    NEVER("never");

    private final String name;

    Mode(String name) {
      this.name = name;
    }

    @Override
    public String getSerializedName() {
      return name;
    }

    /**
     * @return the next mode in the cycle, wrapping back to always
     */
    public Mode next() {
      return values()[(ordinal() + 1) % values().length];
    }

    /**
     * @return the chat translation key for this mode
     */
    public String messageKey() {
      return "message.alchemizer.bellows." + name;
    }
  }

  public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

  /**
   * A rendering selector, not a game rule.
   *
   * <p>The world only ever uses {@link #WHOLE}. The client renderer asks for the cap and pleat
   * models by setting this property on a copy of the state, which lets it draw those pieces
   * separately (and with their own transforms) without a second block or hand-built geometry. The
   * validator never reads it.
   */
  public enum Part implements StringRepresentable {
    /** The whole bellows: the item model and the world model while it is still. */
    WHOLE("whole"),
    /** One cap, used twice by the renderer. */
    CAP("cap"),
    /** The two lower pleat bands. */
    LOWER_PLEATS("lower_pleats"),
    /** The two upper pleat bands. */
    UPPER_PLEATS("upper_pleats");

    private final String name;

    Part(String name) {
      this.name = name;
    }

    @Override
    public String getSerializedName() {
      return name;
    }
  }

  public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

  public static final MapCodec<BellowsBlock> CODEC = simpleCodec(BellowsBlock::new);

  /** The north-authored shape, rotated once for the other three facings. */
  private static final Map<Direction, VoxelShape> SHAPES =
      Shapes.rotateHorizontal(PartShapes.BELLOWS_NORTH);

  /**
   * @param properties the block's properties, already stamped with its registry id
   */
  public BellowsBlock(Properties properties) {
    super(properties, PartShapes.BELLOWS_NORTH);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MODE, Mode.ALWAYS)
            .setValue(ACTIVE, true)
            .setValue(PART, Part.WHOLE));
  }

  @Override
  protected MapCodec<? extends BellowsBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, MODE, ACTIVE, PART);
  }

  /**
   * @param pos the block's position
   * @param state the block's state
   * @return the presentation entity the renderer hangs on
   */
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BellowsBlockEntity(pos, state);
  }

  /**
   * Points the plate at the block that was clicked. Clicking a block's south face puts the bellows
   * south of it, so the plate has to face north — the clicked face's opposite, which is also the
   * convention vanilla's hopper uses. A click on a top or bottom face has no horizontal support, so
   * the player's own facing decides instead.
   */
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    Direction clicked = context.getClickedFace();
    Direction facing =
        clicked.getAxis().isHorizontal()
            ? clicked.getOpposite()
            : context.getHorizontalDirection().getOpposite();
    return defaultBlockState().setValue(FACING, facing);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPES.get(state.getValue(FACING));
  }

  /**
   * Cycles the mode on a shift-right-click, with a chat line and a ding so the change is obvious. A
   * plain right-click is left alone for whatever the player is holding.
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!player.isSecondaryUseActive()) {
      return InteractionResult.PASS;
    }
    if (!level.isClientSide()) {
      Mode next = state.getValue(MODE).next();
      level.setBlock(
          pos, state.setValue(MODE, next).setValue(ACTIVE, next == Mode.ALWAYS), Block.UPDATE_ALL);
      player.sendSystemMessage(Component.translatable(next.messageKey()));
      level.playSound(
          null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }
    return InteractionResult.SUCCESS;
  }

  /**
   * Starts or stops the breathing at the chamber's request. Only the while-brewing mode obeys; the
   * other two are the player's choice and the chamber does not override them.
   *
   * <p>This only flips the state bit that tells the client to draw the moving bellows. The motion
   * itself is the renderer's, so there is no timer here and nothing to schedule.
   */
  @Override
  public void setAnimating(Level level, BlockPos pos, BlockState state, boolean animating) {
    if (state.getValue(MODE) != Mode.WHILE_BREWING || state.getValue(ACTIVE) == animating) {
      return;
    }
    // Clients draw the moving model; neighbours do not care.
    level.setBlock(pos, state.setValue(ACTIVE, animating), Block.UPDATE_CLIENTS);
  }
}
