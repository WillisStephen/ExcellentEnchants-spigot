package su.nightexpress.excellentenchants.enchantment.tool;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentenchants.EnchantsPlugin;
import su.nightexpress.excellentenchants.api.EnchantPriority;
import su.nightexpress.excellentenchants.api.enchantment.component.EnchantComponent;
import su.nightexpress.excellentenchants.api.enchantment.meta.Probability;
import su.nightexpress.excellentenchants.api.enchantment.type.BlockDropEnchant;
import su.nightexpress.excellentenchants.enchantment.EnchantContext;
import su.nightexpress.excellentenchants.enchantment.GameEnchantment;
import su.nightexpress.excellentenchants.manager.EnchantManager;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.Players;

import java.nio.file.Path;
import java.util.Map;


public class TelekinesisEnchant extends GameEnchantment implements BlockDropEnchant {

    public TelekinesisEnchant(@NotNull EnchantsPlugin plugin, @NotNull EnchantManager manager, @NotNull Path file, @NotNull EnchantContext context) {
        super(plugin, manager, file, context);
        this.addComponent(EnchantComponent.PROBABILITY, Probability.oneHundred());
    }

    @Override
    protected void loadAdditional(@NotNull FileConfig config) {

    }

    @Override
    @NotNull
    public EnchantPriority getDropPriority() {
        return EnchantPriority.MONITOR;
    }

    @Override
    public boolean onDrop(@NotNull BlockDropItemEvent event, @NotNull LivingEntity entity, @NotNull ItemStack item, int level) {
        Bukkit.getLogger().info("[EE Tele] onDrop fired.. START");
        if (!(entity instanceof Player player)) return false;
        Bukkit.getLogger().info("[EE Tele] onDrop fired: drops=" + event.getItems().size()
                + " player=" + (entity instanceof Player p ? p.getName() : entity.getType()));

        return event.getItems().removeIf(drop -> {
            ItemStack itemStack = drop.getItemStack();
//            if (Players.countItemSpace(player, itemStack) > 0) {
//                Players.addItem(player, itemStack);
//                return true;
//            }
//            return false;

            if (itemStack == null || itemStack.isEmpty()) return true;

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());

            if (leftovers.isEmpty()) {
                return true;
            }

            // Partial fit fix
            int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();

            if (remaining <= 0) {
                return true;
            }

            ItemStack remainder = itemStack.clone();
            remainder.setAmount(remaining);
            drop.setItemStack(remainder);
            return false;
        });
    }
}
