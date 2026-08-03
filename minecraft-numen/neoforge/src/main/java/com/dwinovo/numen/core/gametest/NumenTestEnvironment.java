package com.dwinovo.numen.core.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Registry-backed batch environment that invokes the suite's Java setup callback. */
public record NumenTestEnvironment(String batch) implements TestEnvironmentDefinition {
    public static final MapCodec<NumenTestEnvironment> CODEC = Codec.STRING.fieldOf("batch")
            .xmap(NumenTestEnvironment::new, NumenTestEnvironment::batch);

    private static final Map<String, Consumer<ServerLevel>> SETUPS = new ConcurrentHashMap<>();

    static void bind(String batch, Consumer<ServerLevel> setup) {
        Consumer<ServerLevel> previous = SETUPS.putIfAbsent(batch, setup);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Numen game test batch setup: " + batch);
        }
    }

    static void clearBindings() {
        SETUPS.clear();
    }

    @Override
    public void setup(ServerLevel level) {
        SETUPS.getOrDefault(batch, ignored -> {}).accept(level);
    }

    @Override
    public MapCodec<? extends TestEnvironmentDefinition> codec() {
        return CODEC;
    }
}
