package net.mehvahdjukaar.sawmill;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RecipeSorter {

    private static final List<Item> ITEM_ORDER = new ArrayList<>();
    private static final List<Item> UNSORTED = new ArrayList<>();


    //called from server side by recipe stuff. We also need to call this from client side
    public static void accept(List<RecipeHolder<WoodcuttingRecipe>> sawmillRecipes) {
        sawmillRecipes.forEach(r -> UNSORTED.add(r.value().getResultItem(RegistryAccess.EMPTY).getItem()));
    }

    public static void acceptOrder(IntList list) {
        UNSORTED.clear();
        list.forEach(i -> ITEM_ORDER.add(BuiltInRegistries.ITEM.byId(i)));
    }

    // dont think we can repopulate offthread
    private static void refreshIfNeeded(Level level) {
        if (UNSORTED.isEmpty()) return;
        if (!CreativeModeTabs.getDefaultTab().hasAnyItems()) {
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
        }
        Map<CreativeModeTab, List<Item>> tabContent = new HashMap<>();

        for (var t : CreativeModeTabs.tabs()) {
            List<Pair<Item, Integer>> weights = new ArrayList<>();
            var list = tabContent.computeIfAbsent(t,
                    creativeModeTabs -> t.getDisplayItems().stream().map(ItemStack::getItem).toList());
            var iterator = UNSORTED.iterator();
            while (iterator.hasNext()) {
                var i = iterator.next();
                int index = list.indexOf(i);
                if (index != -1) {
                    weights.add(Pair.of(i, index));
                    iterator.remove();
                }
            }
            weights.sort(Comparator.comparingInt(Pair::getSecond));
            ITEM_ORDER.addAll(weights.stream().map(Pair::getFirst).toList());
        }

        UNSORTED.clear();
    }


    public static void sort(List<RecipeHolder<WoodcuttingRecipe>> recipes, Level level) {
        if (CommonConfigs.SORT_RECIPES.get()) {
            //Just runs once if needed. Needs to be the same from server and client
            refreshIfNeeded(level);

            recipes.sort(Comparator.comparingInt(value ->
                    ITEM_ORDER.indexOf(value.value().getResultItem(RegistryAccess.EMPTY).getItem())));
        }
    }

    public static void sendOrderToClient(@Nullable ServerPlayer player) {
        IntList list = new IntArrayList();
        ITEM_ORDER.forEach(i -> list.add(BuiltInRegistries.ITEM.getId(i)));
        NetworkStuff.SyncRecipeOrder message = new NetworkStuff.SyncRecipeOrder(list);
        if (player != null) {
            NetworkStuff.CHANNEL.sendToClientPlayer(player, message);
        } else {
            NetworkStuff.CHANNEL.sendToAllClientPlayers(message);
        }
    }
}