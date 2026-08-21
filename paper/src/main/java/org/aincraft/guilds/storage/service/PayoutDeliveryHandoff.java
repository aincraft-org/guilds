package org.aincraft.guilds.storage.service;

import java.util.UUID;

/** Token returned when a payout obligation is claimed for player delivery. */
public record PayoutDeliveryHandoff(UUID deliveryToken) {}
