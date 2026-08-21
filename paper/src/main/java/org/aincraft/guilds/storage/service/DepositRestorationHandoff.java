package org.aincraft.guilds.storage.service;

import java.util.UUID;

/** Token returned when a deposit restoration obligation is claimed for player delivery. */
public record DepositRestorationHandoff(UUID handoffToken) {}
