import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;


public class VirtualThreadCrasher {
    private List<Connection> leaks = new ArrayList<>();
    private static final AtomicBoolean isSlave1Crashed = new AtomicBoolean(false);

    // Speed trick: Force Slave 1 to only allow 5 connections
    public void prepDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_Config.SLAVE1_URL, DB_Config.USER, DB_Config.PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET GLOBAL max_connections = 5"); 
            System.out.println("⚡ Slave 1 capacity shrunk to 5 connections for instant crash.");
        } catch (SQLException e) {
            System.err.println("Note: Could not set max_connections (requires SUPER/SYSTEM_VARIABLES_ADMIN).");
        }
    }

  
public void startCrash() {
    // A latch to hold all threads until we are ready
    CountDownLatch startGate = new CountDownLatch(1);
    System.out.println("🚀 Synchronizing 20 threads for an instant strike...");

    for (int i = 0; i < 20; i++) {
        new Thread(() -> {
            try {
                // All threads wait here until startGate.countDown() is called
                startGate.await(); 
                
                Connection conn = DriverManager.getConnection(
                    DB_Config.SLAVE1_URL, DB_Config.USER, DB_Config.PASS);
                
                synchronized(leaks) { leaks.add(conn); }
                Thread.sleep(Long.MAX_VALUE); 
            } catch (SQLException e) {
                if (isSlave1Crashed.compareAndSet(false, true)) {
                    System.err.println("⚠️ Slave 1 IS FULL! (Crash detected)");
                }
            } catch (Exception e) {
                // Thread interrupted
            }
        }).start();
    }

    // BAM! This releases all 20 threads at once. 
    // The DB gets hit with 20 requests in the same millisecond.
    startGate.countDown(); 
}

    public boolean isCrashed() {
        return isSlave1Crashed.get();
    }
}