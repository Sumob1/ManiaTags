package com.tiertagger.api;

import com.tiertagger.api.TierProvider.Mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registry of every supported tier list and all of its gamemodes. */
public final class Providers {
    private Providers() {}

    private static final List<TierProvider> ALL = new ArrayList<>();

    private static Mode m(String key, String label) {
        return new Mode(key, label);
    }

    static {
        // --- Built-in APIs (endpoints taken from the Tiers mod) -------------------------------
        ALL.add(new StandardProvider("mctiers", "MCTiers", 0xE8A33A, "https://mctiers.net/api/v2/profile/", List.of(
            m("vanilla", "Vanilla"), m("uhc", "UHC"), m("pot", "Pot"), m("nethop", "Netherite OP"),
            m("smp", "SMP"), m("sword", "Sword"), m("axe", "Axe"), m("mace", "Mace"))));

        ALL.add(new StandardProvider("pvptiers", "PvPTiers", 0xF2762E, "https://pvptiers.com/api/profile/", List.of(
            m("crystal", "Crystal"), m("sword", "Sword"), m("uhc", "UHC"), m("pot", "Pot"),
            m("neth_pot", "Netherite Pot"), m("smp", "SMP"), m("axe", "Axe"), m("mace", "Mace"))));

        ALL.add(new StandardProvider("subtiers", "Subtiers", 0x3FA7F5, "https://subtiers.net/api/profile/", List.of(
            m("minecart", "Minecart"), m("diamond_vanilla", "Diamond Vanilla"), m("debuff", "Debuff"),
            m("elytra", "Elytra"), m("speed", "Speed"), m("creeper", "Creeper"), m("manhunt", "Manhunt"),
            m("diamond_smp", "Diamond SMP"), m("bow", "Bow"), m("bed", "Bed"),
            m("og_vanilla", "OG Vanilla"), m("trident", "Trident"))));

        // --- Endpoint must be configured (no public API found): real kit lists, URL set in config ----
        List<Mode> mcpvpKits = List.of(
            m("sword", "Sword"), m("shield", "Shield"), m("pot", "Pot"), m("early_game", "Early Game"),
            m("end_game", "End Game"), m("mace", "Mace"), m("late_game", "Late Game"), m("spear", "Spear"),
            m("diamond_smp", "Diamond SMP"), m("netherite_pot", "Netherite Pot"), m("creeper", "Creeper"),
            m("cart", "Cart"), m("bow", "Bow"), m("smp", "SMP"), m("crystal", "Crystal"), m("uhc", "UHC"));
        ALL.add(new PvpHqProvider());
        // MCPVP uses the MCTiers JSON shape; kits are discovered automatically from the response.
        ALL.add(new StandardProvider("mcpvp", "MCPVP", 0x4AD97B, "https://www.mcpvp.com/tiers/data/profile/", mcpvpKits));

        // StatTiers: "Mainlist" + "Sublist" kits as shown on stattiers.com
        ALL.add(new StatTiersProvider(List.of(
            m("crystal", "Crystal"), m("sword", "Sword"), m("diapot", "Diapot"), m("nethpot", "Nethpot"),
            m("smp", "SMP"), m("mace", "Mace"), m("uhc", "UHC"), m("speed", "Speed"),
            m("diacrystal", "DiaCrystal"), m("diasmp", "DiaSMP"), m("ogv", "OGV"),
            m("spearmace", "SpearMace"), m("axe", "Axe"),
            m("soup", "Soup"), m("goldogv", "GoldOGV"), m("speedop", "SpeedOP"), m("nethsword", "NethSword"),
            m("ironcrystal", "IronCrystal"), m("creeper", "Creeper"), m("elytra", "Elytra"),
            m("trident", "Trident"), m("bed", "Bed"), m("minecart", "Minecart"), m("bow", "Bow"))));
    }

    public static List<TierProvider> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<TierProvider> enabled() {
        List<TierProvider> out = new ArrayList<>();
        for (TierProvider p : ALL) if (p.isEnabled()) out.add(p);
        return out;
    }

    public static TierProvider byId(String id) {
        for (TierProvider p : ALL) if (p.id().equals(id)) return p;
        return null;
    }
}
