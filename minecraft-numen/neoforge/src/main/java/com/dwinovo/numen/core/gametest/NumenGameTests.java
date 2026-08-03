package com.dwinovo.numen.core.gametest;

import com.dwinovo.numen.core.Constants;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** NeoForge 1.21.11 registration bridge for the in-world companion suite. */
public final class NumenGameTests {
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition>> ENVIRONMENT_TYPES =
            DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Constants.MOD_ID);

    static {
        TEST_INSTANCE_TYPES.register("java_function", () -> NumenGameTestInstance.CODEC);
        ENVIRONMENT_TYPES.register("java_batch", () -> NumenTestEnvironment.CODEC);
    }

    private NumenGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_INSTANCE_TYPES.register(modBus);
        ENVIRONMENT_TYPES.register(modBus);
        modBus.addListener(NumenGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        String structuresDirectory = System.getProperty("numen.gametest.structures", "").trim();
        if (!structuresDirectory.isEmpty()) {
            StructureUtils.testStructuresDir = java.nio.file.Path.of(structuresDirectory);
        }
        NumenGameTestInstance.clearBindings();
        NumenTestEnvironment.clearBindings();

        Method[] methods = CompanionGameTests.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        Map<String, Method> setups = new LinkedHashMap<>();
        for (Method method : methods) {
            BeforeBatch annotation = method.getAnnotation(BeforeBatch.class);
            if (annotation == null) {
                continue;
            }
            requireStaticSignature(method, ServerLevel.class);
            Method previous = setups.putIfAbsent(annotation.batch(), method);
            if (previous != null) {
                throw new IllegalStateException("Duplicate setup for game test batch " + annotation.batch());
            }
        }

        String selection = System.getProperty("numen.gametest.tests", "").trim();
        Set<String> testFilter = selection.equalsIgnoreCase("all") ? Set.of() :
                Arrays.stream(selection.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> matchedSelection = new HashSet<>();
        Map<String, Holder<TestEnvironmentDefinition>> environments = new LinkedHashMap<>();
        int registered = 0;
        int pendingFixtures = 0;

        for (Method method : methods) {
            GameTest annotation = method.getAnnotation(GameTest.class);
            if (annotation == null) {
                continue;
            }
            requireStaticSignature(method, GameTestHelper.class);
            String testId = method.getName().toLowerCase(Locale.ROOT);
            if (!annotation.migrated()) {
                pendingFixtures++;
                if (testFilter.contains(testId)) {
                    throw new IllegalStateException("Selected game test awaits a missing fixture: " + testId);
                }
                continue;
            }
            if (!testFilter.isEmpty() && !testFilter.contains(testId)) {
                continue;
            }
            matchedSelection.add(testId);

            Holder<TestEnvironmentDefinition> environment = environments.computeIfAbsent(
                    annotation.batch(), batch -> registerEnvironment(event, batch, setups.get(batch)));
            NumenGameTestInstance.bind(testId, helper -> invoke(method, helper));

            Identifier name = id("companion/" + testId);
            TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(
                    environment,
                    id(annotation.template()),
                    annotation.timeoutTicks(),
                    0,
                    true,
                    Rotation.NONE);
            event.registerTest(name, new NumenGameTestInstance(data, testId));
            registered++;
        }

        if (!testFilter.isEmpty() && !matchedSelection.equals(testFilter)) {
            Set<String> unknown = new java.util.TreeSet<>(testFilter);
            unknown.removeAll(matchedSelection);
            throw new IllegalArgumentException("Unknown Numen game test selection: " + unknown);
        }

        Constants.LOG.info("Registered {} NeoForge game tests{}; {} await missing terrain fixtures.",
                registered,
                testFilter.isEmpty() ? "" : " selected by numen.gametest.tests",
                pendingFixtures);
    }

    private static Holder<TestEnvironmentDefinition> registerEnvironment(
            RegisterGameTestsEvent event, String batch, Method setup) {
        if (setup != null) {
            NumenTestEnvironment.bind(batch, level -> invoke(setup, level));
        }
        return event.registerEnvironment(id("batch/" + batch), new NumenTestEnvironment(batch));
    }

    private static void requireStaticSignature(Method method, Class<?> parameterType) {
        if (!Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class
                || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{parameterType})) {
            throw new IllegalStateException("Invalid game test method signature: " + method);
        }
    }

    private static void invoke(Method method, Object argument) {
        try {
            method.invoke(null, argument);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access game test method " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException("Game test method failed: " + method, cause);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
