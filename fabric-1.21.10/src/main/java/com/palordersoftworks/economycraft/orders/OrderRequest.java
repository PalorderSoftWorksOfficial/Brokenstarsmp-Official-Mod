package com.palordersoftworks.economycraft.orders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.Registries;
import com.palordersoftworks.economycraft.util.IdentifierCompat;
import net.minecraft.item.ItemStack;


import java.util.UUID;

public class OrderRequest {
    public int id;
    public UUID requester;
    public ItemStack item;
    public int amount;
    public long price;

    public JsonObject save(WrapperLookup provider) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (requester != null) obj.addProperty("requester", requester.toString());
        obj.addProperty("price", price);
        obj.addProperty("item", Registries.ITEM.getId(item.getItem()).toString());
        obj.addProperty("amount", amount);
        JsonElement stackEl = ItemStack.CODEC.encodeStart(provider.getOps(JsonOps.INSTANCE), item).result().orElse(new JsonObject());
        obj.add("stack", stackEl);
        return obj;
    }

    public static OrderRequest load(JsonObject obj, WrapperLookup provider) {
        OrderRequest r = new OrderRequest();
        r.id = obj.get("id").getAsInt();
        if (obj.has("requester")) r.requester = UUID.fromString(obj.get("requester").getAsString());
        r.price = obj.get("price").getAsLong();
        r.amount = obj.get("amount").getAsInt();
        if (obj.has("stack")) {
            r.item = ItemStack.CODEC.parse(provider.getOps(JsonOps.INSTANCE), obj.get("stack")).result().orElse(ItemStack.EMPTY);
        }
        if (r.item == null || r.item.isEmpty()) {
            String itemId = obj.get("item").getAsString();
            IdentifierCompat.Id rl = IdentifierCompat.tryParse(itemId);
            if (rl != null) {
                java.util.Optional<net.minecraft.item.Item> opt = IdentifierCompat.registryGetOptional(Registries.ITEM, rl);
                opt.ifPresent(item -> r.item = new ItemStack(item));
            }
        }
        return r;
    }
}
