package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.territory.building.boat.BoatRouteResult;
import org.aincraft.guilds.territory.building.boat.BoatRouteService;
import org.aincraft.guilds.territory.building.boat.BoatWaterMask;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.permission.BlockProtection;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
/** Coordinates mode authorization, route checks, currency reservations, and warmup. */
public final class FastTravelService {
    private static final long FALLBACK_RESERVATION_EXPIRY_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final FacilityRegistry facilities;
    private final FacilityAnchorValidator anchors;
    private final FastTravelAccess access;
    private final SafeLandingResolver landings;
    private final BlockProtection protection;
    private final BuildingConfig config;
    private final TravelCurrencyService currency;
    private final FastTravelCostCalculator costs;
    private final BoatRouteService boatRoutes;
    private final TechTreeService techTree;
    private final ConcurrentMap<UUID, PendingTravel> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TravelAttempt> attempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, EnumMap<FastTravelMode, Long>> cooldowns = new ConcurrentHashMap<>();
    private final EnumMap<FastTravelMode, Long> modeCooldowns;
    private final long reservationDurationMillis;

    /**
     * Legacy wiring constructor. It deliberately has no currency rail, so it
     * reports reservation failure until the shared currency service is injected.
     */
    public FastTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                             FacilityAnchorValidator anchors, FastTravelAccess access,
                             SafeLandingResolver landings, BlockProtection protection,
                             BuildingConfig config) {
        this(plugin, facilities, anchors, access, landings, protection, config,
                null, null, null, null, null, null, null, defaultCooldowns(config));
    }

    public FastTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                             FacilityAnchorValidator anchors, FastTravelAccess access,
                             SafeLandingResolver landings, BlockProtection protection,
                             BuildingConfig config, TravelCurrencyService currency,
                             FastTravelCostCalculator costs, BoatRouteService boatRoutes) {
        this(plugin, facilities, anchors, access, landings, protection, config,
                currency, costs, boatRoutes, null, null, null, null, defaultCooldowns(config));
    }

    public FastTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                             FacilityAnchorValidator anchors, FastTravelAccess access,
                             SafeLandingResolver landings, BlockProtection protection,
                             BuildingConfig config, TravelCurrencyService currency,
                             FastTravelCostCalculator costs, BoatRouteService boatRoutes,
                             TechTreeService techTree, GuildService guilds,
                             ResidentService residents, AllianceService alliances) {
        this(plugin, facilities, anchors, access, landings, protection, config,
                currency, costs, boatRoutes, techTree, guilds, residents, alliances,
                defaultCooldowns(config));
    }

    public FastTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                             FacilityAnchorValidator anchors, FastTravelAccess access,
                             SafeLandingResolver landings, BlockProtection protection,
                             BuildingConfig config, TravelCurrencyService currency,
                             FastTravelCostCalculator costs, BoatRouteService boatRoutes,
                             TechTreeService techTree, GuildService guilds,
                             ResidentService residents, AllianceService alliances,
                             Map<FastTravelMode, Long> cooldowns) {
        this(plugin, facilities, anchors, access, landings, protection, config,
                currency, costs, boatRoutes, techTree, guilds, residents, alliances,
                cooldowns, FALLBACK_RESERVATION_EXPIRY_MILLIS);
    }

    public FastTravelService(JavaPlugin plugin, FacilityRegistry facilities,
                             FacilityAnchorValidator anchors, FastTravelAccess access,
                             SafeLandingResolver landings, BlockProtection protection,
                             BuildingConfig config, TravelCurrencyService currency,
                             FastTravelCostCalculator costs, BoatRouteService boatRoutes,
                             TechTreeService techTree, GuildService guilds,
                             ResidentService residents, AllianceService alliances,
                             Map<FastTravelMode, Long> cooldowns,
                             long reservationDurationMillis) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.access = Objects.requireNonNull(access, "access");
        this.landings = Objects.requireNonNull(landings, "landings");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.config = Objects.requireNonNull(config, "config");
        this.currency = currency;
        this.costs = costs;
        this.boatRoutes = boatRoutes;
        this.techTree = techTree;
        this.guilds = guilds;
        if (reservationDurationMillis <= 0L) {
            throw new IllegalArgumentException("reservation duration must be positive");
        }
        this.reservationDurationMillis = reservationDurationMillis;
        this.modeCooldowns = copyCooldowns(cooldowns);
    }

    /**
     * Starts a trip after all synchronous gates, route/landing checks, and a
     * durable reservation have succeeded. Database and route callbacks never
     * access Bukkit state until marshalled back through the server scheduler.
     */
    public CompletionStage<StartResult> start(Player player, SettlementFacility origin,
                                              String destinationId, long nowMillis) {
        if (stopped || player == null) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        UUID playerId = player.getUniqueId();
        TravelAttempt attempt = new TravelAttempt(playerId);
        if (attempts.putIfAbsent(playerId, attempt) != null) {
            return completed(StartResult.PENDING_TRIP);
        }
        SettlementFacility destination = destinationId == null
                ? null : facilities.get(destinationId).orElse(null);
        FastTravelAccess.AccessDecision decision = access.authorize(playerId, origin, destination);
        if (decision == null) {
            decision = legacyWaystoneDecision(playerId, origin, destination);
        }
        final FastTravelAccess.AccessDecision finalDecision = decision;
        if (!finalDecision.allowed()) {
            attempts.remove(playerId, attempt);
            return completed(map(finalDecision.result()));
        }
        FastTravelMode mode = finalDecision.mode();
        if (mode == null) {
            attempts.remove(playerId, attempt);
            return completed(StartResult.TYPE_MISMATCH);
        }
        if (remainingCooldownMillis(playerId, mode, nowMillis) > 0L) {
            attempts.remove(playerId, attempt);
            return completed(StartResult.COOLDOWN);
        }

        CompletableFuture<StartResult> outcome = new CompletableFuture<>();
        CompletionStage<BoatRouteResult> routeStage;
        try {
            routeStage = route(mode, origin, destination);
        } catch (RuntimeException exception) {
            attempts.remove(playerId, attempt);
            outcome.complete(StartResult.ROUTE_UNAVAILABLE);
            return outcome;
        }
        if (routeStage == null) {
            attempts.remove(playerId, attempt);
            outcome.complete(StartResult.ROUTE_UNAVAILABLE);
            return outcome;
        }
        try {
            routeStage.whenComplete((route, error) -> {
                try {
                    onMain(() -> {
                        try {
                            if (!isCurrent(attempt)) {
                                outcome.complete(StartResult.RESERVATION_FAILED);
                                return;
                            }
                            if (error != null || route == null) {
                                attempts.remove(playerId, attempt);
                                outcome.complete(StartResult.ROUTE_UNAVAILABLE);
                                return;
                            }
                            if (route.status() != BoatRouteResult.Status.CONNECTED
                                    && mode == FastTravelMode.BOAT) {
                                attempts.remove(playerId, attempt);
                                outcome.complete(mapRoute(route.status()));
                                return;
                            }
                            CompletionStage<StartResult> reservation = reserveAfterRoute(
                                    player, origin, destination, finalDecision, route, nowMillis, attempt);
                            if (reservation == null) {
                                attempts.remove(playerId, attempt);
                                outcome.complete(StartResult.RESERVATION_FAILED);
                                return;
                            }
                            reservation.whenComplete((result, reservationError) -> {
                                if (reservationError != null || result != StartResult.STARTED) {
                                    attempts.remove(playerId, attempt);
                                }
                                outcome.complete(reservationError == null && result != null
                                        ? result : StartResult.RESERVATION_FAILED);
                            });
                        } catch (RuntimeException exception) {
                            attempts.remove(playerId, attempt);
                            outcome.complete(StartResult.RESERVATION_FAILED);
                        }
                    });
                } catch (RuntimeException exception) {
                    attempts.remove(playerId, attempt);
                    outcome.complete(StartResult.RESERVATION_FAILED);
                }
            });
        } catch (RuntimeException exception) {
            attempts.remove(playerId, attempt);
            outcome.complete(StartResult.RESERVATION_FAILED);
        }
        return outcome;
    }

    private CompletionStage<StartResult> reserveAfterRoute(Player player,
                                                            SettlementFacility origin,
                                                            SettlementFacility destination,
                                                            FastTravelAccess.AccessDecision decision,
                                                            BoatRouteResult route,
                                                            long nowMillis,
                                                            TravelAttempt attempt) {
        if (!isCurrent(attempt)) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        Location landing = landings.find(destination).orElse(null);
        if (landing == null) {
            return completed(StartResult.NO_SAFE_LANDING);
        }
        if (!protection.canTeleportInto(destination.worldId(), landing.getBlockX(),
                landing.getBlockZ(), player.getUniqueId().toString())) {
            return completed(StartResult.PROTECTED_DESTINATION);
        }
        double distance = route.status() == BoatRouteResult.Status.CONNECTED
                ? route.scalarDistance() : endpointDistance(origin, destination);
        final long amount;
        try {
            if (costs == null) {
                return completed(StartResult.RESERVATION_FAILED);
            }
            amount = costs.calculate(decision.mode(), distance);
        } catch (RuntimeException exception) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        if (currency == null) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        String tripId = UUID.randomUUID().toString();
        CompletionStage<TravelCurrencyService.ReserveResult> reservation;
        try {
            reservation = currency.reserve(player.getUniqueId(), tripId, amount, nowMillis);
        } catch (RuntimeException exception) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        if (reservation == null) {
            return completed(StartResult.RESERVATION_FAILED);
        }
        final long expiry = safeExpiry(nowMillis);
        return reservation.handle((result, error) -> {
            if (error != null || result == null) {
                attempts.remove(player.getUniqueId(), attempt);
                return StartResult.RESERVATION_FAILED;
            }
            if (result.status() != TravelCurrencyService.ReserveStatus.RESERVED) {
                attempts.remove(player.getUniqueId(), attempt);
            }
            return switch (result.status()) {
                case RESERVED -> scheduleWarmup(player.getUniqueId(), origin.id(), destination.id(),
                        decision.mode(), decision.travelerGuildId(), amount, result.reservationId(),
                        expiry, route.scalarDistance(), attempt);
                case INSUFFICIENT -> StartResult.INSUFFICIENT_CURRENCY;
                case DUPLICATE_TRIP -> StartResult.DUPLICATE_TRIP;
                case INVALID_AMOUNT, FAILED -> StartResult.RESERVATION_FAILED;
            };
        });
    }

    private StartResult scheduleWarmup(UUID playerId, String originId, String destinationId,
                                       FastTravelMode mode, String travelerGuildId, long amount,
                                       String reservationId, long expiry, double routeDistance,
                                       TravelAttempt attempt) {
        if (!isCurrent(attempt) || reservationId == null || stopped) {
            releaseQuietly(reservationId, System.currentTimeMillis());
            attempts.remove(playerId, attempt);
            return StartResult.RESERVATION_FAILED;
        }
        final BukkitTask task;
        try {
            task = plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> complete(playerId), config.waystoneWarmupTicks());
        } catch (RuntimeException exception) {
            releaseQuietly(reservationId, System.currentTimeMillis());
            attempts.remove(playerId, attempt);
            return StartResult.RESERVATION_FAILED;
        }
        if (task == null) {
            releaseQuietly(reservationId, System.currentTimeMillis());
            attempts.remove(playerId, attempt);
            return StartResult.RESERVATION_FAILED;
        }
        PendingTravel next = new PendingTravel(playerId, originId, destinationId, mode,
                travelerGuildId, amount, reservationId, expiry, routeDistance, task);
        PendingTravel replaced;
        synchronized (attempt) {
            if (!isCurrent(attempt)) {
                task.cancel();
                releaseQuietly(reservationId, System.currentTimeMillis());
                attempts.remove(playerId, attempt);
                return StartResult.RESERVATION_FAILED;
            }
            attempt.trip = next;
            replaced = pending.putIfAbsent(playerId, next);
            if (replaced != null) {
                attempt.trip = null;
            }
        }
        if (replaced != null) {
            task.cancel();
            releaseQuietly(reservationId, System.currentTimeMillis());
            attempts.remove(playerId, attempt);
            return StartResult.PENDING_TRIP;
        }
        return StartResult.STARTED;
    }

    private void complete(UUID playerId) {
        TravelAttempt attempt = attempts.get(playerId);
        PendingTravel trip = pending.get(playerId);
        if (attempt == null || trip == null || !isCurrent(attempt)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (trip.expiresAtMillis() <= now) {
            failAndRelease(playerId, attempt, trip, now);
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        SettlementFacility origin = facilities.get(trip.originId()).orElse(null);
        SettlementFacility destination = facilities.get(trip.destinationId()).orElse(null);
        FastTravelAccess.AccessDecision decision = access.authorize(playerId, origin, destination);
        if (decision == null) {
            decision = legacyWaystoneDecision(playerId, origin, destination);
        }
        final FastTravelAccess.AccessDecision finalDecision = decision;
        if (player == null || !finalDecision.allowed() || finalDecision.mode() != trip.mode()) {
            failAndRelease(playerId, attempt, trip, now);
            return;
        }
        CompletionStage<BoatRouteResult> routeStage;
        try {
            routeStage = route(trip.mode(), origin, destination);
        } catch (RuntimeException exception) {
            failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            return;
        }
        if (routeStage == null) {
            failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            return;
        }
        routeStage.whenComplete((route, error) -> {
            try {
                onMain(() -> {
                    if (error != null || route == null
                            || (trip.mode() == FastTravelMode.BOAT
                            && route.status() != BoatRouteResult.Status.CONNECTED)) {
                        failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    Location landing = landings.find(destination).orElse(null);
                    if (landing == null || !protection.canTeleportInto(destination.worldId(),
                            landing.getBlockX(), landing.getBlockZ(), playerId.toString())) {
                        failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    double distance = route.status() == BoatRouteResult.Status.CONNECTED
                            ? route.scalarDistance() : endpointDistance(origin, destination);
                    final long recalculatedCost;
                    try {
                        if (costs == null) {
                            throw new IllegalStateException("travel cost calculator unavailable");
                        }
                        recalculatedCost = costs.calculate(trip.mode(), distance);
                    } catch (RuntimeException exception) {
                        failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    if (recalculatedCost != trip.amount()
                            || !attempt.state.compareAndSet(AttemptState.ACTIVE, AttemptState.COMMITTING)) {
                        failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    if (!player.teleport(landing)) {
                        finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    commit(playerId, attempt, trip, finalDecision);
                });
            } catch (RuntimeException exception) {
                failAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            }
        });
    }

    private void commit(UUID playerId, TravelAttempt attempt, PendingTravel trip,
                        FastTravelAccess.AccessDecision decision) {
        if (currency == null) {
            finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            return;
        }
        CompletionStage<TravelCurrencyService.ReservationResult> result;
        try {
            result = currency.commit(trip.reservationId(), System.currentTimeMillis());
        } catch (RuntimeException exception) {
            finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            return;
        }
        if (result == null) {
            finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            return;
        }
        result.whenComplete((commit, error) -> {
            try {
                onMain(() -> {
                    if (error != null || commit == null
                            || (commit.status() != TravelCurrencyService.ReservationStatus.COMMITTED
                            && commit.status()
                            != TravelCurrencyService.ReservationStatus.ALREADY_COMMITTED)) {
                        finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
                        return;
                    }
                    if (attempts.remove(playerId, attempt)) {
                        attempt.state.set(AttemptState.TERMINAL);
                        pending.remove(playerId, trip);
                        setCooldown(playerId, trip.mode(), trip.travelerGuildId(),
                                System.currentTimeMillis());
                    }
                });
            } catch (RuntimeException exception) {
                finishAndRelease(playerId, attempt, trip, System.currentTimeMillis());
            }
        });
    }

    public void cancel(UUID playerId, CancelReason reason) {
        if (playerId == null) {
            return;
        }
        TravelAttempt attempt = attempts.get(playerId);
        if (attempt == null || !attempt.state.compareAndSet(AttemptState.ACTIVE, AttemptState.TERMINAL)) {
            return;
        }
        attempts.remove(playerId, attempt);
        synchronized (attempt) {
            PendingTravel trip = attempt.trip;
            if (trip != null && pending.remove(playerId, trip)) {
                cancelTask(trip.task());
                releaseQuietly(trip.reservationId(), System.currentTimeMillis());
            }
        }
    }

    public long remainingCooldownMillis(UUID playerId, FastTravelMode mode, long nowMillis) {
        if (playerId == null || mode == null) {
            return 0L;
        }
        EnumMap<FastTravelMode, Long> playerCooldowns = cooldowns.get(playerId);
        long expiry = playerCooldowns == null ? 0L : playerCooldowns.getOrDefault(mode, 0L);
        return Math.max(0L, expiry - nowMillis);
    }

    /** Convenience overload for existing callers that only tracked waystones. */
    public long remainingCooldownMillis(UUID playerId, long nowMillis) {
        return remainingCooldownMillis(playerId, FastTravelMode.WAYSTONE, nowMillis);
    }

    public boolean isPending(UUID playerId) {
        return playerId != null && attempts.containsKey(playerId);
    }

    public void recover(long nowMillis) {
        if (currency != null) {
            try {
                currency.recoverExpired(nowMillis);
            } catch (RuntimeException ignored) {
                // In-memory expired trips are still cleared below.
            }
        }
        pending.forEach((playerId, trip) -> {
            if (trip.expiresAtMillis() <= nowMillis) {
                TravelAttempt attempt = attempts.get(playerId);
                if (attempt != null) {
                    failAndRelease(playerId, attempt, trip, nowMillis);
                }
            }
        });
    }

    public void stop() {
        stopped = true;
        attempts.forEach((playerId, attempt) -> {
            if (attempt.state.compareAndSet(AttemptState.ACTIVE, AttemptState.TERMINAL)) {
                attempts.remove(playerId, attempt);
                PendingTravel trip = attempt.trip;
                if (trip != null && pending.remove(playerId, trip)) {
                    cancelTask(trip.task());
                    releaseQuietly(trip.reservationId(), System.currentTimeMillis());
                }
            }
        });
    }

    private CompletionStage<BoatRouteResult> route(FastTravelMode mode,
                                                   SettlementFacility origin,
                                                   SettlementFacility destination) {
        if (mode != FastTravelMode.BOAT) {
            return CompletableFuture.completedFuture(BoatRouteResult.connected(
                    endpointDistance(origin, destination)));
        }
        if (boatRoutes == null || origin == null || destination == null) {
            return CompletableFuture.completedFuture(BoatRouteResult.unavailable());
        }
        World world = plugin.getServer().getWorld(origin.worldId());
        if (world == null || !origin.worldId().equals(destination.worldId())) {
            return CompletableFuture.completedFuture(BoatRouteResult.unavailable());
        }
        try {
            return boatRoutes.route(world.getUID(),
                    new BoatWaterMask.Cell(origin.x(), origin.y(), origin.z()),
                    new BoatWaterMask.Cell(destination.x(), destination.y(), destination.z()));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(BoatRouteResult.unavailable());
        }
    }

    private boolean isCurrent(TravelAttempt attempt) {
        return attempt != null && !stopped
                && attempts.get(attempt.playerId) == attempt
                && attempt.state.get() == AttemptState.ACTIVE;
    }

    private void failAndRelease(UUID playerId, TravelAttempt attempt,
                                PendingTravel trip, long nowMillis) {
        if (attempt.state.compareAndSet(AttemptState.ACTIVE, AttemptState.TERMINAL)) {
            attempts.remove(playerId, attempt);
            if (pending.remove(playerId, trip)) {
                cancelTask(trip.task());
                releaseQuietly(trip.reservationId(), nowMillis);
            }
        }
    }

    private void finishAndRelease(UUID playerId, TravelAttempt attempt,
                                  PendingTravel trip, long nowMillis) {
        if (attempts.remove(playerId, attempt)) {
            attempt.state.set(AttemptState.TERMINAL);
            pending.remove(playerId, trip);
            cancelTask(trip.task());
            releaseQuietly(trip.reservationId(), nowMillis);
        }
    }

    private static void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }

    }
    private void releaseQuietly(String reservationId, long nowMillis) {
        if (currency == null || reservationId == null) {
            return;
        }
        try {
            CompletionStage<TravelCurrencyService.ReservationResult> result = currency.release(
                    reservationId, nowMillis);
            if (result != null) {
                result.exceptionally(ignored -> null);
            }
        } catch (RuntimeException ignored) {
            // Durable recovery will release an orphaned reservation.
        }
    }

    private void setCooldown(UUID playerId, FastTravelMode mode, String guildId, long nowMillis) {
        long duration = modeCooldowns.getOrDefault(mode, 0L);
        if (mode == FastTravelMode.WAYSTONE && duration > 0L && techTree != null && guilds != null
                && guildId != null) {
            Guild guild = guilds.getGuildById(guildId).orElse(null);
            if (guild != null) {
                double reduction = techTree.cooldownReduction(guild, FastTravelMode.WAYSTONE);
                if (Double.isFinite(reduction)) {
                    reduction = Math.max(0.0, Math.min(1.0, reduction));
                    duration = Math.max(0L, Math.round(duration * (1.0 - reduction)));
                }
            }
        }
        try {
            long expiry = Math.addExact(nowMillis, duration);
            cooldowns.computeIfAbsent(playerId, ignored -> new EnumMap<>(FastTravelMode.class))
                    .put(mode, expiry);
        } catch (ArithmeticException ignored) {
            cooldowns.computeIfAbsent(playerId, ignored -> new EnumMap<>(FastTravelMode.class))
                    .put(mode, Long.MAX_VALUE);
        }
    }

    private FastTravelAccess.AccessDecision legacyWaystoneDecision(UUID playerId,
                                                                    SettlementFacility origin,
                                                                    SettlementFacility destination) {
        FastTravelMode originMode = origin == null
                ? null : FastTravelMode.fromFacilityType(origin.type()).orElse(null);
        FastTravelMode destinationMode = destination == null
                ? null : FastTravelMode.fromFacilityType(destination.type()).orElse(null);
        if (origin == null || destination == null || originMode != FastTravelMode.WAYSTONE
                || destinationMode != FastTravelMode.WAYSTONE
                || access.reachable(playerId, origin).stream()
                .noneMatch(facility -> facility.id().equals(destination.id()))) {
            return new FastTravelAccess.AccessDecision(
                    destination == null ? FastTravelAccess.AccessResult.INACTIVE_DESTINATION
                            : FastTravelAccess.AccessResult.NON_ALLIED_DESTINATION,
                    FastTravelMode.WAYSTONE, null, null, null, null, null, null);
        }
        return new FastTravelAccess.AccessDecision(FastTravelAccess.AccessResult.ALLOWED,
                FastTravelMode.WAYSTONE, null, null, null, null, null, null);
    }

    private static double endpointDistance(SettlementFacility origin, SettlementFacility destination) {
        if (origin == null || destination == null) {
            return Double.NaN;
        }
        double dx = (double) origin.x() - destination.x();
        double dy = (double) origin.y() - destination.y();
        double dz = (double) origin.z() - destination.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static StartResult map(FastTravelAccess.AccessResult result) {
        if (result == null) {
            return StartResult.RESERVATION_FAILED;
        }
        return switch (result) {
            case ALLOWED -> StartResult.RESERVATION_FAILED;
            case INACTIVE_ORIGIN -> StartResult.INACTIVE_ORIGIN;
            case INACTIVE_DESTINATION -> StartResult.INACTIVE_DESTINATION;
            case TYPE_MISMATCH -> StartResult.TYPE_MISMATCH;
            case MISSING_MEMBERSHIP -> StartResult.MISSING_MEMBERSHIP;
            case MISSING_CAPABILITY -> StartResult.MISSING_CAPABILITY;
            case NON_ALLIED_DESTINATION -> StartResult.NON_ALLIED_DESTINATION;
            case SAME_TERRITORY_REMOTE -> StartResult.SAME_TERRITORY_REMOTE;
            case POLICY_DENIED -> StartResult.POLICY_DENIED;
            case WORLD_MISMATCH -> StartResult.WORLD_MISMATCH;
        };
    }

    private static StartResult mapRoute(BoatRouteResult.Status status) {
        return switch (status) {
            case PENDING -> StartResult.ROUTE_PENDING;
            case UNAVAILABLE -> StartResult.ROUTE_UNAVAILABLE;
            case DISCONNECTED -> StartResult.ROUTE_DISCONNECTED;
            case CONNECTED -> StartResult.RESERVATION_FAILED;
        };
    }

    /** Marshals a caller's callback to the Paper primary thread. */
    public void executeOnMain(Runnable action) {
        Objects.requireNonNull(action, "action");
        onMain(action);
    }

    private void onMain(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) {
            action.run();
            return;
        }
        BukkitTask scheduled = plugin.getServer().getScheduler().runTask(plugin, action);
        if (scheduled == null) {
            throw new IllegalStateException("unable to marshal callback to Paper main thread");
        }
    }

    private static CompletionStage<StartResult> completed(StartResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private long safeExpiry(long nowMillis) {
        try {
            return Math.addExact(nowMillis, reservationDurationMillis);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static EnumMap<FastTravelMode, Long> defaultCooldowns(BuildingConfig config) {
        EnumMap<FastTravelMode, Long> values = new EnumMap<>(FastTravelMode.class);
        long waystone = config == null ? 0L : config.waystoneCooldownMillis();
        values.put(FastTravelMode.WAYSTONE, waystone);
        values.put(FastTravelMode.CRYSTAL, waystone);
        values.put(FastTravelMode.BOAT, waystone);
        values.put(FastTravelMode.AIRSHIP, waystone);
        return values;
    }

    private static EnumMap<FastTravelMode, Long> copyCooldowns(Map<FastTravelMode, Long> values) {
        Objects.requireNonNull(values, "cooldowns");
        EnumMap<FastTravelMode, Long> copy = new EnumMap<>(FastTravelMode.class);
        values.forEach((mode, duration) -> {
            Objects.requireNonNull(mode, "cooldown mode");
            Objects.requireNonNull(duration, "cooldown duration");
            if (duration < 0L) {
                throw new IllegalArgumentException("cooldown duration cannot be negative");
            }
            if (mode != FastTravelMode.LOCAL_TERMINAL) {
                copy.put(mode, duration);
            }
        });
        return copy;
    }

    public enum StartResult {
        STARTED,
        COOLDOWN,
        PENDING_TRIP,
        INVALID_ORIGIN,
        INACCESSIBLE_DESTINATION,
        TYPE_MISMATCH,
        MISSING_MEMBERSHIP,
        MISSING_CAPABILITY,
        NON_ALLIED_DESTINATION,
        SAME_TERRITORY_REMOTE,
        POLICY_DENIED,
        WORLD_MISMATCH,
        ROUTE_PENDING,
        ROUTE_UNAVAILABLE,
        ROUTE_DISCONNECTED,
        NO_SAFE_LANDING,
        PROTECTED_DESTINATION,
        INSUFFICIENT_CURRENCY,
        DUPLICATE_TRIP,
        RESERVATION_FAILED;

        public static final StartResult INACTIVE_ORIGIN = INVALID_ORIGIN;
        public static final StartResult INACTIVE_DESTINATION = INACCESSIBLE_DESTINATION;
        public static final StartResult UNSAFE_LANDING = NO_SAFE_LANDING;
    }

    public enum CancelReason {
        MOVED,
        DAMAGED,
        DIED,
        QUIT,
        REPLACED,
        SHUTDOWN,
        EXPIRED,
        INVALIDATED,
        FAILED
    }

    private enum AttemptState {
        ACTIVE,
        COMMITTING,
        TERMINAL
    }

    private static final class TravelAttempt {
        private final UUID playerId;
        private final AtomicReference<AttemptState> state =
                new AtomicReference<>(AttemptState.ACTIVE);
        private volatile PendingTravel trip;

        private TravelAttempt(UUID playerId) {
            this.playerId = playerId;
        }
    }

    private record PendingTravel(UUID playerId, String originId, String destinationId,
                                 FastTravelMode mode, String travelerGuildId, long amount,
                                 String reservationId, long expiresAtMillis, double routeDistance,
                                 BukkitTask task) {
    }
}
