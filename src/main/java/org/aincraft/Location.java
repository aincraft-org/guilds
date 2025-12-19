package org.aincraft;

import com.google.common.base.Preconditions;
import java.util.Objects;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public final class Location {

  private final double x;
  private final double y;
  private final double z;
  private final String world;

  public Location(double x, double y, double z, String world) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.world = world;
  }

  @NotNull
  public static Location fromBukkit(@NotNull org.bukkit.Location location)
      throws NullPointerException, IllegalArgumentException {
    Preconditions.checkNotNull(location);
    World world = location.getWorld();
    if (world == null) {
      throw new IllegalArgumentException("world is null");
    }
    return new Location(location.x(), location.y(), location.z(), world.getName());
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public double z() {
    return z;
  }

  public String world() {
    return world;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (Location) obj;
    return Double.doubleToLongBits(this.x) == Double.doubleToLongBits(that.x) &&
        Double.doubleToLongBits(this.y) == Double.doubleToLongBits(that.y) &&
        Double.doubleToLongBits(this.z) == Double.doubleToLongBits(that.z) &&
        Objects.equals(this.world, that.world);
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y, z, world);
  }

  @Override
  public String toString() {
    return "Location[" +
        "x=" + x + ", " +
        "y=" + y + ", " +
        "z=" + z + ", " +
        "world=" + world + ']';
  }

}
