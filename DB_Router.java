import java.sql.*;

public class DB_Router {
    // index 0: Master, 1: Slave1, 2: Slave2
    public static int[] healthStatus = {1, 1, 1};

    public Connection getConnection(boolean isWrite) throws SQLException {
        if (isWrite) {
            return DriverManager.getConnection(DB_Config.MASTER_URL, DB_Config.USER, DB_Config.PASS);
        }

        // --- READ LOGIC ---
        try {
            // Primary Read Target: Slave 1
            Connection conn = DriverManager.getConnection(DB_Config.SLAVE1_URL, DB_Config.USER, DB_Config.PASS);
            healthStatus[1] = 1; 
            return conn;
        } catch (SQLException e) {
            // Slave 1 is crashed!
            healthStatus[1] = 0; 
            System.err.println("!!! SLAVE 1 DOWN: Fetching from SLAVE 2 (Replica) !!!");
            
            // Failover to Slave 2
            return DriverManager.getConnection(DB_Config.SLAVE2_URL, DB_Config.USER, DB_Config.PASS);
        }
    }
}