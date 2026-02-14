package su.nightexpress.excellentenchants.enchantment.tool;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentenchants.EnchantsPlaceholders;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.EnchantsUtils;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.Modifier;
import su.nightexpress.excellentenchants.api.enchantment.type.MiningEnchant;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.BukkitThing;

import java.nio.file.Path;
import java.util.*;

public class VeinminerEnchant extends GameEnchantment implements MiningEnchant {

    private Modifier blocksLimit;
    private Set<Material> affectedBlocks;

    private boolean disableOnCrouch;
    private boolean mustSneak;
    private int searchRadius;
    private boolean allowDiagonals;

    public VeinminerEnchant(
            @NotNull EnchantsPlugin plugin,
            @NotNull EnchantManager manager,
            @NotNull Path file,
            @NotNull EnchantContext context
    ) {
        super(plugin, manager, file, context);
    }

    @Override
    protected void loadAdditional(@NotNull FileConfig config) {
        this.disableOnCrouch = ConfigValue.create(
                "Veinminer.Disable_On_Crouch",
                true,
                "If true: sneaking disables the effect."
        ).read(config);

        this.mustSneak = ConfigValue.create(
                "Veinminer.Must_Sneak",
                false,
                "If true: you MUST be sneaking to activate the effect."
        ).read(config);

        this.searchRadius = ConfigValue.create(
                "Veinminer.Search_Radius",
                1,
                "Radius around each processed block to search for more blocks.",
                "1 = 3x3x3 cube, 2 = 5x5x5 cube, etc."
        ).read(config);

        this.allowDiagonals = ConfigValue.create(
                "Veinminer.Allow_Diagonals",
                true,
                "If true: cube search includes diagonals (more like the Fabric implementation).",
                "If false: uses only 6-face adjacency within the radius."
        ).read(config);

        this.blocksLimit = Modifier.load(
                config,
                "Veinminer.Block_Limit",
                Modifier.addictive(6).perLevel(2).capacity(48),
                "Max possible amount of blocks to be mined at the same time."
        );

        this.affectedBlocks = ConfigValue.forSet(
                "Veinminer.Block_List",
                BukkitThing::getMaterial,
                (cfg, path, set) -> cfg.set(path, set.stream().map(Enum::name).toList()),
                () -> {
                    Set<Material> set = new HashSet<>();
                    set.addAll(Tag.COAL_ORES.getValues());
                    set.addAll(Tag.COPPER_ORES.getValues());
                    set.addAll(Tag.DIAMOND_ORES.getValues());
                    set.addAll(Tag.EMERALD_ORES.getValues());
                    set.addAll(Tag.GOLD_ORES.getValues());
                    set.addAll(Tag.IRON_ORES.getValues());
                    set.addAll(Tag.LAPIS_ORES.getValues());
                    set.addAll(Tag.REDSTONE_ORES.getValues());
                    set.add(Material.NETHER_GOLD_ORE);
                    set.add(Material.NETHER_QUARTZ_ORE);
                    return set;
                },
                "List of blocks affected by this enchantment."
        ).read(config);

        this.addPlaceholder(EnchantsPlaceholders.GENERIC_AMOUNT, level -> String.valueOf(getBlocksLimit(level)));
    }

    public int getBlocksLimit(int level) {
        return (int) this.blocksLimit.getValue(level);
    }

    @NotNull
    @Override
    public EnchantPriority getBreakPriority() {
        return EnchantPriority.LOWEST;
    }

    @Override
    public boolean onBreak(@NotNull BlockBreakEvent event, @NotNull LivingEntity entity, @NotNull ItemStack tool, int level) {
        if (!(entity instanceof Player player)) return false;
        if (EnchantsUtils.isBusy()) return false;

        if (this.mustSneak && !player.isSneaking()) return false;
        if (this.disableOnCrouch && player.isSneaking()) return false;

        Block source = event.getBlock();
        if (!this.affectedBlocks.contains(source.getType())) return false;

        if (source.getDrops(tool, player).isEmpty()) return false;

        veinmine(player, source, level);
        return true;
    }

    private void veinmine(@NotNull Player player, @NotNull Block source, int level) {
        final Material target = source.getType();
        final int limit = Math.min(getBlocksLimit(level), 128); // hard safety cap

        // BFS like the Fabric queue-based approach
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();

        queue.add(source);
        visited.add(source);

        // we do NOT break the “source” here; Bukkit will handle it from the original event
        int broken = 0;

        while (!queue.isEmpty() && visited.size() < limit + 1) {
            Block current = queue.poll();

            for (Block near : neighbors(current, target)) {
                if (visited.size() >= limit + 1) break;
                if (visited.add(near)) queue.add(near);
            }
        }

        // Remove source so we don’t double-break it
        visited.remove(source);

        // Break the rest safely using EE’s helper (important for recursion/compat)
        for (Block b : visited) {
            if (broken >= limit) break;
            EnchantsUtils.safeBusyBreak(player, b);
            broken++;
        }
    }

    private Collection<Block> neighbors(@NotNull Block block, @NotNull Material target) {
        int r = Math.max(0, this.searchRadius);
        if (r == 0) return List.of();

        List<Block> out = new ArrayList<>();

        // Cube scan, optionally skipping diagonals
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    if (!allowDiagonals) {
                        // keep only 6-face steps (Manhattan distance 1) when r==1,
                        // and for r>1 treat as “axis-aligned only”
                        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (manhattan != 1) continue;
                    }

                    Block rel = block.getWorld().getBlockAt(
                            block.getX() + dx,
                            block.getY() + dy,
                            block.getZ() + dz
                    );

                    if (rel.getType() == target) out.add(rel);
                }
            }
        }

        return out;
    }
}
