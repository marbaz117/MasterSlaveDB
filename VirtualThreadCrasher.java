import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VirtualThreadCrasher {
    private List<Connection> leaks = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicBoolean isSlave1Crashed = new AtomicBoolean(false);

    public void startCrash() {
        int attackPower = 2010; 
        System.out.println("🚀 Launching " + attackPower + " threads...");

        for (int i = 0; i < attackPower; i++) {
            new Thread(() -> {
                try {
                    Connection conn = DriverManager.getConnection(DB_Config.SLAVE1_URL, DB_Config.USER, DB_Config.PASS);
                    leaks.add(conn); 

                    // NEW LOGIC: Trigger 'crash' when we hit 1990 connections
                    // This prevents getting stuck at 1999/2000
                    if (leaks.size() >= 1990) {
                        isSlave1Crashed.set(true);
                    }

                    Thread.sleep(Long.MAX_VALUE); 
                } catch (SQLException e) {
                    // This will also trigger if a real error occurs
                    if (leaks.size() >= 1950) { 
                        isSlave1Crashed.set(true);
                    }
                } catch (InterruptedException e) {}
            }).start();
        }
    }

    public void stopAttack() {
        System.out.println("\n🛑 Stopping attack: Closing " + leaks.size() + " connections...");
        synchronized(leaks) {
            // Use an iterator to avoid potential concurrent modification issues
            Iterator<Connection> it = leaks.iterator();
            while(it.hasNext()){
                Connection conn = it.next();
                try {
                    if (conn != null && !conn.isClosed()) conn.close();
                } catch (SQLException e) {}
            }
            leaks.clear();
        }
        System.out.println("✅ Database slots are now free for migration.");
    }

    public boolean isCrashed() {
        return isSlave1Crashed.get();
    }

    public int getLeakCount() {
        return leaks.size();
    }
}