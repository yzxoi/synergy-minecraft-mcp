package com.dwinovo.numen.platform;

import com.dwinovo.numen.platform.services.IBlockCapabilityReader;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fabric implementation of {@link IBlockCapabilityReader}. Item and fluid
 * contents are read through Fabric Transfer API storages.
 *
 * <p>The unsided lookup and all six faces are queried because some machines
 * expose only a combined unsided storage while others expose only sided
 * storages. Returned storage objects are de-duplicated by identity.
 *
 * <p>Inspection is strictly read-only: it snapshots resource, amount, and
 * capacity from storage views without opening a transaction or invoking
 * insert/extract. Both the number of visited views and emitted lines are
 * bounded so a pathological dynamic storage cannot produce an unbounded reply.
 *
 * <p>Energy is intentionally not reported on Fabric. Fabric API does not
 * define an energy transfer capability and this project has no optional energy
 * API dependency; adding one just for inspection would impose a new runtime
 * compatibility requirement.
 */
public final class FabricBlockCapabilityReader implements IBlockCapabilityReader {

    /** Maximum non-empty item views or fluid views emitted for one storage. */
    static final int MAX_VIEW_LINES = 64;

    /** Hard scan bound for custom/dynamic storages whose iterators may be very large. */
    static final int MAX_VIEWS_SCANNED = 4096;

    @Override
    public String describe(Level level, BlockPos pos) {
        StringBuilder sb = new StringBuilder();
        appendItems(level, pos, sb);
        appendFluids(level, pos, sb);
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendItems(Level level, BlockPos pos, StringBuilder sb) {
        Map<Storage<ItemVariant>, List<String>> byStorage = new IdentityHashMap<>();
        collect(byStorage, ItemStorage.SIDED.find(level, pos, null), "all");
        for (Direction direction : Direction.values()) {
            collect(byStorage, ItemStorage.SIDED.find(level, pos, direction), direction.getName());
        }

        int index = 0;
        for (Map.Entry<Storage<ItemVariant>, List<String>> entry : byStorage.entrySet()) {
            Storage<ItemVariant> storage = entry.getKey();
            ItemSnapshot snapshot = snapshotItems(storage);
            sb.append("items").append(byStorage.size() > 1 ? " #" + index : "")
                    .append(" (sides: ").append(String.join(",", entry.getValue())).append("), ")
                    .append(snapshot.viewCountLabel()).append(":\n");

            for (ItemView item : snapshot.nonEmptyViews()) {
                sb.append("  ").append(snapshot.slotted() ? "slot " : "view ")
                        .append(item.index()).append(": ")
                        .append(BuiltInRegistries.ITEM.getKey(item.resource().getItem()))
                        .append(" x").append(item.amount()).append("\n");
            }
            if (snapshot.nonEmptyCount() == 0) {
                sb.append(snapshot.scanTruncated()
                        ? "  (no non-empty views in the inspected prefix)\n"
                        : "  (all inspected views empty)\n");
            } else if (snapshot.nonEmptyCount() > snapshot.nonEmptyViews().size()) {
                sb.append("  … and ").append(snapshot.nonEmptyCount() - snapshot.nonEmptyViews().size())
                        .append(" more non-empty views\n");
            }
            appendScanLimit(snapshot.scanTruncated(), snapshot.scannedViews(), sb);
            index++;
        }
    }

    private static void appendFluids(Level level, BlockPos pos, StringBuilder sb) {
        Map<Storage<FluidVariant>, List<String>> byStorage = new IdentityHashMap<>();
        collect(byStorage, FluidStorage.SIDED.find(level, pos, null), "all");
        for (Direction direction : Direction.values()) {
            collect(byStorage, FluidStorage.SIDED.find(level, pos, direction), direction.getName());
        }

        int index = 0;
        for (Map.Entry<Storage<FluidVariant>, List<String>> entry : byStorage.entrySet()) {
            FluidSnapshot snapshot = snapshotFluids(entry.getKey());
            sb.append("fluids").append(byStorage.size() > 1 ? " #" + index : "")
                    .append(" (sides: ").append(String.join(",", entry.getValue())).append("), ")
                    .append(snapshot.viewCountLabel()).append(":\n");

            for (FluidView fluid : snapshot.views()) {
                sb.append("  ").append(snapshot.slotted() ? "tank " : "view ")
                        .append(fluid.index()).append(": ");
                if (fluid.resource().isBlank() || fluid.amount() == 0) {
                    sb.append("empty");
                } else {
                    sb.append(BuiltInRegistries.FLUID.getKey(fluid.resource().getFluid()))
                            .append(" ").append(formatMilliBuckets(fluid.amount()));
                }
                sb.append("/").append(formatMilliBuckets(fluid.capacity())).append(" mB\n");
            }
            if (snapshot.totalViews() == 0 && !snapshot.scanTruncated()) {
                sb.append("  (no views)\n");
            } else if (snapshot.totalViews() > snapshot.views().size()) {
                sb.append("  … and ").append(snapshot.totalViews() - snapshot.views().size())
                        .append(" more views\n");
            }
            appendScanLimit(snapshot.scanTruncated(), snapshot.scannedViews(), sb);
            index++;
        }
    }

    private static ItemSnapshot snapshotItems(Storage<ItemVariant> storage) {
        List<ItemView> shown = new ArrayList<>();
        int nonEmpty = 0;
        Scan<ItemVariant> scan = scan(storage);
        for (IndexedView<ItemVariant> indexed : scan.views()) {
            StorageView<ItemVariant> view = indexed.view();
            long amount = view.getAmount();
            if (amount <= 0 || view.isResourceBlank()) continue;
            nonEmpty++;
            if (shown.size() < MAX_VIEW_LINES) {
                shown.add(new ItemView(indexed.index(), view.getResource(), amount));
            }
        }
        return new ItemSnapshot(shown, nonEmpty, scan.scannedViews(), scan.totalViews(),
                scan.slotted(), scan.truncated());
    }

    private static FluidSnapshot snapshotFluids(Storage<FluidVariant> storage) {
        List<FluidView> shown = new ArrayList<>();
        Scan<FluidVariant> scan = scan(storage);
        for (IndexedView<FluidVariant> indexed : scan.views()) {
            if (shown.size() >= MAX_VIEW_LINES) break;
            StorageView<FluidVariant> view = indexed.view();
            shown.add(new FluidView(indexed.index(), view.getResource(), view.getAmount(), view.getCapacity()));
        }
        return new FluidSnapshot(shown, scan.scannedViews(), scan.totalViews(), scan.slotted(), scan.truncated());
    }

    /**
     * Take a bounded, read-only snapshot of a storage's views. Slotted storages
     * expose their exact total up front; generic storages are counted only as
     * far as the scan bound.
     */
    @SuppressWarnings("unchecked")
    private static <T> Scan<T> scan(Storage<T> storage) {
        List<IndexedView<T>> views = new ArrayList<>();
        if (storage instanceof SlottedStorage<?> rawSlotted) {
            SlottedStorage<T> slotted = (SlottedStorage<T>) rawSlotted;
            int total = slotted.getSlotCount();
            int scanned = Math.min(total, MAX_VIEWS_SCANNED);
            for (int i = 0; i < scanned; i++) {
                views.add(new IndexedView<>(i, slotted.getSlot(i)));
            }
            return new Scan<>(views, scanned, total, true, total > scanned);
        }

        Iterator<StorageView<T>> iterator = storage.iterator();
        int scanned = 0;
        while (scanned < MAX_VIEWS_SCANNED && iterator.hasNext()) {
            views.add(new IndexedView<>(scanned, iterator.next()));
            scanned++;
        }
        boolean truncated = iterator.hasNext();
        return new Scan<>(views, scanned, scanned, false, truncated);
    }

    /** Record a non-null storage under a side, de-duplicating by object identity. */
    static <T> void collect(Map<T, List<String>> byStorage, T storage, String side) {
        if (storage == null) return;
        byStorage.computeIfAbsent(storage, ignored -> new ArrayList<>()).add(side);
    }

    /** Convert Fabric droplets (81,000 per bucket) to NeoForge-style millibuckets. */
    static String formatMilliBuckets(long droplets) {
        long dropletsPerMilliBucket = FluidConstants.BUCKET / 1_000;
        if (droplets % dropletsPerMilliBucket == 0) {
            return Long.toString(droplets / dropletsPerMilliBucket);
        }
        return BigDecimal.valueOf(droplets)
                .divide(BigDecimal.valueOf(dropletsPerMilliBucket), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static void appendScanLimit(boolean truncated, int scanned, StringBuilder sb) {
        if (truncated) {
            sb.append("  … scan capped after ").append(scanned).append(" views\n");
        }
    }

    private record IndexedView<T>(int index, StorageView<T> view) {}

    private record Scan<T>(List<IndexedView<T>> views, int scannedViews, int totalViews,
                           boolean slotted, boolean truncated) {}

    private record ItemView(int index, ItemVariant resource, long amount) {}

    private record ItemSnapshot(List<ItemView> nonEmptyViews, int nonEmptyCount, int scannedViews,
                                int totalViews, boolean slotted, boolean scanTruncated) {
        String viewCountLabel() {
            return slotted ? totalViews + " slots" : scannedViews + (scanTruncated ? "+ views" : " views");
        }
    }

    private record FluidView(int index, FluidVariant resource, long amount, long capacity) {}

    private record FluidSnapshot(List<FluidView> views, int scannedViews, int totalViews,
                                 boolean slotted, boolean scanTruncated) {
        String viewCountLabel() {
            return slotted ? totalViews + " tanks" : scannedViews + (scanTruncated ? "+ views" : " views");
        }
    }
}
