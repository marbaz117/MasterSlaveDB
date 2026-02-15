import java.sql.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DB_Router router = new DB_Router();
        VirtualThreadCrasher crasher = new VirtualThreadCrasher();

        // RESET: Drop slave2 if it exists from previous run to ensure a clean test
        try (Connection conn = DriverManager.getConnection(DB_Config.MASTER_URL, DB_Config.USER, DB_Config.PASS)) {
            conn.createStatement().executeUpdate("DROP DATABASE IF EXISTS slave2");
        } catch (SQLException e) {
            // It's okay if it doesn't exist yet
        }

        printHealthMatrix(DB_Router.healthStatus);

        // 1. WRITE to Master
        System.out.println("\n--- Performing WRITE Operation (Master) ---");
        try (Connection conn = router.getConnection(true);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO users (name) VALUES ('Arbaz_Failover_Test')");
            System.out.println("✅ WRITE SUCCESS to Master.");
        } catch (SQLException e) { 
            System.err.println("❌ Write Failed: " + e.getMessage()); 
        }

        // 2. CRASH Slave 1
        // This launches 2010 threads to hit your 2000 limit
        crasher.startCrash();
        
        // ... inside main ...
System.out.println("⏳ Waiting for Slave 1 to reach connection limit...");
while(!crasher.isCrashed()) {
    Thread.sleep(500);
    // Print progress so you know it's working
    System.out.print("\rConnections established: " + crasher.getLeakCount() + "/2000 ");
}

System.out.println("\n🔥 Done. Slave 1 is dead.");

// SOLUTION B: THE EMERGENCY EXIT
crasher.stopAttack(); 
DB_Router.healthStatus[1] = 0;
System.out.println("⏳ Waiting 3 seconds for MySQL to flush sockets...");
Thread.sleep(3000); // CRITICAL: Give the OS time to free the ports!

// 3. Now perform the migration
System.out.println("\n--- Performing READ Operation (Failover Test) ---");
// This will now find plenty of free slots
        try (Connection conn = router.getConnection(false);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id DESC LIMIT 1")) {
            
            if(rs.next()) {
                System.out.println("✅ FETCH SUCCESS from: " + conn.getMetaData().getURL());
                System.out.println("Fetched Data: " + +rs.getInt("id")+"       "+rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("❌ READ FAILED: " + e.getMessage());
        }

        printHealthMatrix(DB_Router.healthStatus);
        
        // IMPORTANT: Exit to ensure all background threads are closed
        System.exit(0); 
    }

    public static void printHealthMatrix(int[] status) {
        System.out.println("\n[ DATABASE CLUSTER HEALTH ]");
        System.out.println("┌──────────┬──────────┐");
        System.out.println("│  NODE    │  STATUS  │");
        System.out.println("├──────────┼──────────┤");
        System.out.printf("│  Master  │    %s    │\n", status[0] == 1 ? "✅" : "❌");
        System.out.printf("│  Slave 1 │    %s    │\n", status[1] == 1 ? "✅" : "❌");
        System.out.printf("│  Slave 2 │    %s    │\n", status[2] == 1 ? "✅" : "❌");
        System.out.println("└──────────┴──────────┘");
    }
}