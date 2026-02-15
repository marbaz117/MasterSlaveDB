import java.sql.*;

public class DB_Router {
    public static int[] healthStatus = {1, 1, 0}; 

    public Connection getConnection(boolean isWrite) throws SQLException {
    if (isWrite) {
        return DriverManager.getConnection(DB_Config.MASTER_URL, DB_Config.USER, DB_Config.PASS);
    }

    // If Slave 1 is marked as 0 (Down)
    if (healthStatus[1] == 0) {
        // If Slave 2 hasn't been initialized yet, initialize it now!
        if (healthStatus[2] == 0) {
            createAndMigrateToSlave2();
        }
        return DriverManager.getConnection(DB_Config.SLAVE2_URL, DB_Config.USER, DB_Config.PASS);
    }

    try {
        return DriverManager.getConnection(DB_Config.SLAVE1_URL, DB_Config.USER, DB_Config.PASS);
    } catch (SQLException e) {
        healthStatus[1] = 0; // Mark Slave 1 as dead
        System.err.println("!!! SLAVE 1 DOWN !!!");
        
        createAndMigrateToSlave2(); // Create the database 'slave2'
        
        return DriverManager.getConnection(DB_Config.SLAVE2_URL, DB_Config.USER, DB_Config.PASS);
    }
}
    private void createAndMigrateToSlave2() {
    System.out.println("🛠 Emergency: Migrating Data via Master...");
    
    // We use the Master URL because it is currently the most 'stable' path
    try (Connection masterConn = DriverManager.getConnection(DB_Config.MASTER_URL, DB_Config.USER, DB_Config.PASS)) {
        Statement stmt = masterConn.createStatement();
        
        // Use the existing connection to run multiple statements
        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS slave2");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS slave2.users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50))");
        
        // This command happens ENTIRELY inside MySQL, making it instant
        stmt.executeUpdate("INSERT INTO slave2.users SELECT * FROM master.users");
        
        healthStatus[2] = 1;
        System.out.println("✅ Migration Successful. Slave 2 is active.");
    } catch (SQLException e) {
        System.err.println("❌ Migration Failed: " + e.getMessage());
    }
}
}