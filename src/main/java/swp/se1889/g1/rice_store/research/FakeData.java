package swp.se1889.g1.rice_store.research;

/*
 FakeData.java
 Creates synthetic data for rice_store schema, including debt_records aggregated by month.

 Usage:
 1) Put Microsoft JDBC driver (mssql-jdbc-*.jar) in classpath.
 2) javac -cp "path/to/mssql-jdbc.jar" FakeRiceStoreData.java
 3) java -cp ".;path/to/mssql-jdbc.jar" FakeRiceStoreData

 Notes:
 - Seed is fixed for reproducibility.
 - Adjust DB_URL, DB_USER, DB_PASSWORD before running.
 - Batch inserts used for performance.
 /*
 Dataset configuration:
 - Users: 100
 - Stores: 50
 - Customers: 50,000
 - Products: 2,000
 - Invoices: 1,000,000
 - Time span: 12 months
 - Random seed: 12345
*/


import java.sql.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class FakeData {
    // === CONFIG ===
    private static final String DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=rice_store;encrypt=false";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "StrongPassword!123";

    // sizes (adjust as needed)
    // sizes (Scaled up for Research Paper Benchmark)
    private static final int NUM_USERS = 100;
    private static final int NUM_STORES = 50;
    private static final int NUM_PRODUCTS = 2000;      // Tăng để tránh query nào cũng hit cache
    private static final int NUM_CUSTOMERS = 50000;    // Tăng khách hàng để phân tán dữ liệu
    private static final int NUM_ZONES = 500;
    private static final int NUM_INVOICES = 1000000;   // 1 triệu hóa đơn (Mục tiêu quan trọng nhất)
    private static final int BATCH_SIZE = 2000;        // Tăng batch size lên để insert nhanh hơn
    private static final int EXTRA_DEBT_RECORDS = 500000; // Tăng lượng record nợ để test query tính toán phức tạp

    // reproducibility
    private static final long SEED = 12345L;

    private static final Random rnd = new Random(SEED);

    // helper arrays for random attribute generation
    private static final String[] FIRST_NAMES = {
            "An", "Binh", "Chi", "Duc", "Dung", "Giang", "Hanh", "Hoa", "Hung", "Khanh",
            "Lan", "Linh", "Mai", "Minh", "Nga", "Ngoc", "Phuong", "Quang", "Son", "Tuan"
    };
    private static final String[] LAST_NAMES = {
            "Nguyen", "Tran", "Le", "Pham", "Ho", "Vu", "Vo", "Dang", "Bui", "Do"
    };
    private static final String[] STREETS = {
            "Le Loi", "Tran Hung Dao", "Hai Ba Trung", "Nguyen Trai", "Tran Phu", "Ly Thuong Kiet", "Pham Van Dong"
    };
    private static final String[] NOTES = {
            "", "VIP customer", "Frequent buyer", "Late payment history", "Prefers 5kg bags", "Seasonal buyer"
    };

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            System.out.println("Connected to DB: " + DB_URL);

            // Clear tables
            clearTables(conn);

            Map<Integer, String> userIdToUsername = createUsers(conn);
            List<Integer> userIds = new ArrayList<>(userIdToUsername.keySet());
            Map<Integer, Long> productPrices = createProducts(conn, userIds);
            createStores(conn, userIdToUsername);
            createCustomers(conn, userIds);
            createZones(conn, productPrices, userIds);
            createInvoicesAndDetailsWithDebts(conn, userIds);
            createExtraDebtRecords(conn, userIds);

            System.out.println("All data generated.");
        }
    }

    // ------------------ helpers ------------------

    private static void clearTables(Connection conn) throws SQLException {
        String[] tables = {"invoice_details", "invoices", "debt_records", "zones", "customers", "products", "stores", "users"};
        try (Statement s = conn.createStatement()) {
            for (String t : tables) {
                s.executeUpdate("DELETE FROM " + t);
                try {
                    s.executeUpdate("DBCC CHECKIDENT ('" + t + "', RESEED, 0)");
                } catch (SQLException ex) {
                    // Ignore if no identity
                }
            }
            conn.commit();
        }
    }

    // 1. users (id, username, password, email, role, name, address, phone, note, timestamps)
    private static Map<Integer, String> createUsers(Connection conn) throws SQLException {
        String sql = "INSERT INTO users (username, password, email, role, name, address, phone, note , created_at, updated_at,created_by,updated_by, is_deleted) VALUES (?,?,?,?,?,?,?,?,GETDATE(),GETDATE(),?,?,?)";
        Map<Integer, String> idToUsername = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int count = 0;
            for (int i = 1; i <= NUM_USERS; i++) {
                String username = "user" + i;
                ps.setString(1, username);
                ps.setString(2, "pass" + (1000 + i)); // hashed in real app; plain here for test
                ps.setString(3, username + "@example.com");
                String role = (i % 5 == 0) ? "ROLE_OWNER" : ((i % 2 == 0) ? "ROLE_EMPLOYEE" : "ROLE_ADMIN");
                ps.setString(4, role);
                ps.setString(5, randomFullName());
                ps.setString(6, randomAddress(i));
                ps.setString(7, randomPhone(i));
                ps.setString(8, NOTES[rnd.nextInt(NOTES.length)]);
                ps.setInt(9, 1);
                ps.setInt(10, 1);
                ps.setBoolean(11, false);
                ps.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();

            // read generated keys to map id->username
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, username FROM users")) {
                while (rs.next()) {
                    idToUsername.put(rs.getInt("id"), rs.getString("username"));
                }
            }
        }
        System.out.println("Inserted users: " + idToUsername.size());
        return idToUsername;
    }

    // 2. stores (created_by references users.username per your schema)
    private static void createStores(Connection conn, Map<Integer, String> idToUsername) throws SQLException {
        String sql = "INSERT INTO stores (name, address, phone, email, note, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,?,GETDATE(),GETDATE(),?,?)";
        List<String> usernames = new ArrayList<>(idToUsername.values());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= NUM_STORES; i++) {
                ps.setString(1, "Store " + i);
                ps.setString(2, randomAddress(i));
                ps.setString(3, randomPhone(1000 + i));
                ps.setString(4, "store" + i + "@example.com");
                ps.setString(5, NOTES[rnd.nextInt(NOTES.length)]);
                // assign random existing username as created_by
                String createdBy = usernames.get(rnd.nextInt(usernames.size()));
                ps.setString(6, createdBy);
                ps.setBoolean(7, false);
                ps.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        System.out.println("Inserted stores: " + NUM_STORES);
    }

    // 3. products
    // returns map productId -> price (approx) after insert (we will query prices later)
    // 3. products
// corrected version: GETDATE() for created_at and updated_at, then placeholders for created_by and is_deleted
    private static Map<Integer, Long> createProducts(Connection conn, List<Integer> userIds) throws SQLException {
        String sql = "INSERT INTO products (name, description, price, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,GETDATE(),GETDATE(),?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= NUM_PRODUCTS; i++) {
                ps.setString(1, "RiceProduct_" + i);
                ps.setString(2, "High quality rice variant " + i);
                long price = 10000 + (i % 50) * 500; // in local currency
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(price));
                // now placeholder 4 -> created_by (user id)
                ps.setLong(4, userIds.get(i % userIds.size()));
                // placeholder 5 -> is_deleted
                ps.setBoolean(5, false);
                ps.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        // read product prices
        Map<Integer, Long> productPrices = new HashMap<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, price FROM products")) {
            while (rs.next()) {
                productPrices.put(rs.getInt("id"), rs.getLong("price"));
            }
        }
        System.out.println("Inserted products: " + productPrices.size());
        return productPrices;
    }

    // 4. customers
    private static void createCustomers(Connection conn, List<Integer> userIds) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, address, email, debt_balance, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,0,GETDATE(),GETDATE(),?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= NUM_CUSTOMERS; i++) {
                ps.setString(1, randomFullName());
                ps.setString(2, randomPhone(2000 + i));
                ps.setString(3, randomAddress(i));
                ps.setString(4, "customer" + i + "@example.com");
                ps.setLong(5, userIds.get(i % userIds.size()));
                ps.setBoolean(6, false);
                ps.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        System.out.println("Inserted customers: " + NUM_CUSTOMERS);
    }

    // 5. zones
    private static void createZones(Connection conn, Map<Integer, Long> productPrices, List<Integer> userIds) throws SQLException {
        String sql = "INSERT INTO zones (name, store_id, address, product_id, quantity, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,?,GETDATE(),GETDATE(),?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            int productCount = productPrices.size();
            for (int i = 1; i <= NUM_ZONES; i++) {
                ps.setString(1, "Zone " + i);
                ps.setLong(2, 1 + (i % NUM_STORES));
                ps.setString(3, "Zone address " + i);
                ps.setLong(4, 1 + (i % productCount));
                ps.setInt(5, 50 + rnd.nextInt(200));
                ps.setLong(6, userIds.get(i % userIds.size()));
                ps.setBoolean(7, false);
                ps.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        System.out.println("Inserted zones: " + NUM_ZONES);
    }

    // 6. invoices + invoice_details + debt_records
    private static void createInvoicesAndDetailsWithDebts(Connection conn, List<Integer> userIds) throws SQLException {
        String invoiceSql = "INSERT INTO invoices (store_id, customer_id, total_price, discount, quantity, final_amount, payment_status, note, type, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String detailSql = "INSERT INTO invoice_details (invoice_id, product_id, quantity, unit_price, total_price, zone_id, customer_id, created_at, updated_at, created_by, updated_by, is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        String debtSql = "INSERT INTO debt_records (customer_id, type, amount, note, create_on, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,?,?,GETDATE(),?,?)";

        try (PreparedStatement invoicePs = conn.prepareStatement(invoiceSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement detailPs = conn.prepareStatement(detailSql);
             PreparedStatement debtPs = conn.prepareStatement(debtSql)) {

            int invoiceCount = 0;

            // date range: last 12 months
            LocalDate now = LocalDate.now();
            LocalDate startDate = now.minusMonths(11).withDayOfMonth(1);

            for (int i = 1; i <= NUM_INVOICES; i++) {
                int storeId = 1 + rnd.nextInt(NUM_STORES);
                int customerId = 1 + rnd.nextInt(NUM_CUSTOMERS);
                int itemsCount = 1 + rnd.nextInt(3); // items per invoice
                double invoiceTotal = 0.0;
                int totalQty = 0;

                // build items first to compute total
                List<Item> items = new ArrayList<>();
                for (int it = 0; it < itemsCount; it++) {
                    int prodId = 1 + rnd.nextInt(NUM_PRODUCTS);
                    double unitPrice = 10000 + (prodId % 50) * 500;
                    int qty = 1 + rnd.nextInt(10);
                    items.add(new Item(prodId, qty, unitPrice));
                    invoiceTotal += unitPrice * qty;
                    totalQty += qty;
                }

                // small discount sometimes
                double discount = (rnd.nextDouble() < 0.05) ? (invoiceTotal * 0.1) : 0.0;
                double finalAmount = invoiceTotal - discount;

                // payment status: mostly Paid, some Unpaid, some In_debt
                String paymentStatus;
                double p = rnd.nextDouble();
                if (p < 0.80) paymentStatus = "Paid";
                else if (p < 0.93) paymentStatus = "Unpaid";
                else paymentStatus = "In_debt";

                String note = (rnd.nextDouble() < 0.05) ? "promo applied" : "";

                // created_by user id
                int createdBy = userIds.get(rnd.nextInt(userIds.size()));

                // random created_at for invoice (spread over last 12 months)
                LocalDate randomDate = startDate.plusDays(rnd.nextInt((int) ChronoUnit.DAYS.between(startDate, now) + 1));
                Timestamp createdAt = Timestamp.valueOf(randomDate.atTime(rnd.nextInt(23), rnd.nextInt(59), rnd.nextInt(59)));

                // insert invoice
                invoicePs.setInt(1, storeId);
                invoicePs.setInt(2, customerId);
                invoicePs.setDouble(3, invoiceTotal);
                invoicePs.setDouble(4, discount);
                invoicePs.setInt(5, totalQty);
                invoicePs.setDouble(6, finalAmount);
                invoicePs.setString(7, paymentStatus);
                invoicePs.setString(8, note);
                invoicePs.setString(9, "Sale");
                invoicePs.setTimestamp(10, createdAt);
                invoicePs.setTimestamp(11, createdAt);
                invoicePs.setInt(12, createdBy);   // created_by
                invoicePs.setBoolean(13, false);   // is_deleted
                invoicePs.executeUpdate();

                ResultSet rs = invoicePs.getGeneratedKeys();
                long invoiceId = -1;
                if (rs.next()) invoiceId = rs.getLong(1);

                // insert details
                for (Item it : items) {
                    int zoneId = 1 + rnd.nextInt(NUM_ZONES);
                    detailPs.setLong(1, invoiceId);
                    detailPs.setLong(2, it.productId);
                    detailPs.setInt(3, it.quantity);
                    detailPs.setDouble(4, it.unitPrice);
                    detailPs.setDouble(5, it.unitPrice * it.quantity);
                    detailPs.setLong(6, zoneId);
                    detailPs.setLong(7, customerId);
                    detailPs.setTimestamp(8, createdAt);
                    detailPs.setTimestamp(9, createdAt);
                    detailPs.setLong(10, createdBy);
                    detailPs.setLong(11, createdBy);
                    detailPs.setBoolean(12, false);
                    detailPs.addBatch();
                }

                // if invoice is In_debt -> create debt_records
                if ("In_debt".equals(paymentStatus)) {
                    double unpaid = finalAmount * (0.3 + rnd.nextDouble() * 0.7);
                    LocalDate debtDate = randomDate.plusDays(rnd.nextInt(30));
                    Timestamp debtTs = Timestamp.valueOf(debtDate.atTime(rnd.nextInt(23), rnd.nextInt(59), rnd.nextInt(59)));

                    debtPs.setLong(1, customerId);
                    debtPs.setString(2, "Customer_debt_shop");
                    debtPs.setBigDecimal(3, java.math.BigDecimal.valueOf(unpaid));
                    debtPs.setString(4, "Debt from invoice " + invoiceId);
                    debtPs.setTimestamp(5, debtTs);
                    debtPs.setTimestamp(6, debtTs);
                    debtPs.setLong(7, createdBy);
                    debtPs.setBoolean(8, false);
                    debtPs.addBatch();
                }

                invoiceCount++;
                if (invoiceCount % BATCH_SIZE == 0) {
                    detailPs.executeBatch();
                    debtPs.executeBatch();
                    conn.commit();
                }
            } // end invoices loop

            // flush remaining batches
            detailPs.executeBatch();
            debtPs.executeBatch();
            conn.commit();
            System.out.println("Inserted invoices and details and debt records from invoices.");
        }
    }


    // 7. extra independent debt records (random customers / months) to reach target
    private static void createExtraDebtRecords(Connection conn, List<Integer> userIds) throws SQLException {
        String sql = "INSERT INTO debt_records (customer_id, type, amount, note, create_on, created_at, updated_at, created_by, is_deleted) VALUES (?,?,?,?,?,?,GETDATE(),?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batch = 0;
            LocalDate now = LocalDate.now();
            LocalDate startDate = now.minusMonths(11).withDayOfMonth(1);
            for (int i = 0; i < EXTRA_DEBT_RECORDS; i++) {
                int customerId = 1 + rnd.nextInt(NUM_CUSTOMERS);
                String type = rnd.nextDouble() < 0.9 ? "Customer_debt_shop" : "Customer_return_shop";
                double amount = 500 + rnd.nextInt(50000);
                String note = (rnd.nextDouble() < 0.1) ? "manual adjustment" : "";

                LocalDate randomDate = startDate.plusDays(rnd.nextInt((int) ChronoUnit.DAYS.between(startDate, now) + 1));
                Timestamp ts = Timestamp.valueOf(randomDate.atTime(rnd.nextInt(23), rnd.nextInt(59), rnd.nextInt(59)));

                ps.setInt(1, customerId);
                ps.setString(2, type);
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(amount));
                ps.setString(4, note);
                ps.setTimestamp(5, ts);
                ps.setTimestamp(6, ts);
                ps.setLong(7, userIds.get(rnd.nextInt(userIds.size())));
                ps.setBoolean(8, false);
                ps.addBatch();
                batch++;
                if (batch % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Inserted extra debt records: " + EXTRA_DEBT_RECORDS);
        }
    }

    // ------------------ small helpers ------------------
    private static String randomFullName() {
        String fn = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
        String ln = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
        return fn + " " + ln;
    }

    private static String randomAddress(int seed) {
        String street = STREETS[rnd.nextInt(STREETS.length)];
        return (10 + rnd.nextInt(200)) + " " + street + " St., District " + (1 + rnd.nextInt(12));
    }

    private static String randomPhone(int seed) {
        int p = 10000000 + rnd.nextInt(89999999);
        return "0" + (9 + rnd.nextInt(1)) + String.format("%08d", p % 100000000);
    }

    private static class Item {
        int productId;
        int quantity;
        double unitPrice;

        Item(int p, int q, double u) {
            productId = p;
            quantity = q;
            unitPrice = u;
        }
    }
}

