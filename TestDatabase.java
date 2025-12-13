import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TestDatabase {
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlite:towny.db";

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            System.out.println("Connected to database");

            // Check if town blocks table exists and has data
            String sql = "SELECT x, z, world, town_id FROM town_blocks LIMIT 10";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("Town blocks in database:");
                while (rs.next()) {
                    System.out.println("  Block at x=" + rs.getInt("x") +
                                     ", z=" + rs.getInt("z") +
                                     ", world=" + rs.getString("world") +
                                     ", town_id=" + rs.getString("town_id"));
                }

                if (!rs.isBeforeFirst()) {
                    System.out.println("  No town blocks found in database!");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }
}