import java.sql.*;

public class Main {
public static void main(String[] args) throws InterruptedException {
    DB_Router router = new DB_Router();
    VirtualThreadCrasher crasher = new VirtualThreadCrasher();

    printHealthMatrix(DB_Router.healthStatus);

    // 1. WRITE to Master
    System.out.println("\n--- Performing WRITE Operation (Master) ---");
    try (Connection conn = router.getConnection(true);
         Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("INSERT INTO users (name) VALUES ('Arbaz_Failover_Test')");
        System.out.println("✅ WRITE SUCCESS to Master.");
    } catch (SQLException e) { System.err.println("Write Failed: " + e.getMessage()); }

    // 2. PREP & CRASH Slave 1
    crasher.prepDatabase(); // Set limit to 5
    crasher.startCrash();   // Fill the 5 slots
    
    // Quick polling for the crash signal
    while(!crasher.isCrashed()) {
        Thread.sleep(100); 
    }
    System.out.println("Done. Slave 1 is dead.");

    // 3. READ (Auto-failover to Slave 2)
    System.out.println("\n--- Performing READ Operation (Failover Test) ---");
    try (Connection conn = router.getConnection(false);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id DESC LIMIT 1")) {
        
        if(rs.next()) {
            System.out.println("✅ FETCH SUCCESS from: " + conn.getMetaData().getURL());
            System.out.println("Fetched Data: " + rs.getString("name"));
        }
    } catch (SQLException e) {
        System.err.println("❌ READ FAILED: " + e.getMessage());
    }

    printHealthMatrix(DB_Router.healthStatus);
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