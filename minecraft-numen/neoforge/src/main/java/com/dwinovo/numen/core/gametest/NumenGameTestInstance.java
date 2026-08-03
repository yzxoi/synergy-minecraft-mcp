package com.dwinovo.numen.core.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A serializable game-test instance that delegates to a Java test method. */
public final class NumenGameTestInstance extends GameTestInstance {
    public static final MapCodec<NumenGameTestInstance> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    TestData.CODEC.fieldOf("data").forGetter(NumenGameTestInstance::info),
                    Codec.STRING.fieldOf("test_id").forGetter(test -> test.testId)
            ).apply(instance, NumenGameTestInstance::new));

    private static final Map<String, Consumer<GameTestHelper>> FUNCTIONS = new ConcurrentHashMap<>();

    private final String testId;

    public NumenGameTestInstance(TestData<Holder<TestEnvironmentDefinition>> data, String testId) {
        super(data);
        this.testId = testId;
    }

    static void bind(String testId, Consumer<GameTestHelper> function) {
        Consumer<GameTestHelper> previous = FUNCTIONS.putIfAbsent(testId, function);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Numen game test id: " + testId);
        }
    }

    static void clearBindings() {
        FUNCTIONS.clear();
    }

    @Override
    public void run(GameTestHelper helper) {
        Consumer<GameTestHelper> function = FUNCTIONS.get(testId);
        if (function == null) {
            throw new IllegalStateException("Missing Numen game test function: " + testId);
        }
        function.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal("Numen Java test");
    }
}
