import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class BikeShowroomApp extends JFrame {
    // Database Credentials - Update these to match your MySQL setup
    private final String URL = "jdbc:mysql://localhost:3306/BikeShowroom";
    private final String USER = "root";
    private final String PASS = "root";

    private DefaultTableModel tableModel;
    private JTextField txtModel, txtBrand, txtPrice, txtSearch;

    public BikeShowroomApp() {
        setupUI();
        loadData("SELECT * FROM Bikes");
    }

    private void setupUI() {
        setTitle("Bike Showroom Management System");
        setSize(1000, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        // --- TOP PANEL: REGISTRATION & SEARCH ---
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Form for Inventory Registration [cite: 12]
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Model:")); txtModel = new JTextField(10); form.add(txtModel);
        form.add(new JLabel("Brand:")); txtBrand = new JTextField(10); form.add(txtBrand);
        form.add(new JLabel("Price:")); txtPrice = new JTextField(8); form.add(txtPrice);
        JButton btnAdd = new JButton("Register Bike");
        btnAdd.addActionListener(e -> addBike());
        form.add(btnAdd);

        // Advanced Search Bar [cite: 14]
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchBar.add(new JLabel("Search (Model/Brand):"));
        txtSearch = new JTextField(20);
        txtSearch.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) {
                String val = txtSearch.getText();
                loadData("SELECT * FROM Bikes WHERE Model_Name LIKE '%"+val+"%' OR Company LIKE '%"+val+"%'");
            }
        });
        searchBar.add(txtSearch);

        topPanel.add(form);
        topPanel.add(searchBar);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: REAL-TIME STOCK VIEW --- [cite: 13]
        String[] cols = {"Bike_ID", "Model_Name", "Company", "Price", "Status"};
        tableModel = new DefaultTableModel(cols, 0);
        JTable table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- BOTTOM: SALES & MAINTENANCE --- [cite: 11, 15]
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        
        JButton btnSell = new JButton("Single-Click Sell");
        btnSell.setBackground(new Color(144, 238, 144));
        btnSell.addActionListener(e -> processSale(table));

        JButton btnDelete = new JButton("Remove Entry");
        btnDelete.setBackground(new Color(255, 182, 193));
        btnDelete.addActionListener(e -> deleteBike(table));

        JButton btnReport = new JButton("Sales History");
        btnReport.addActionListener(e -> showReport());

        actions.add(btnSell);
        actions.add(btnDelete);
        actions.add(btnReport);
        add(actions, BorderLayout.SOUTH);

        setVisible(true);
    }

    // --- DATABASE LOGIC ---

    private void loadData(String query) {
        tableModel.setRowCount(0);
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("Bike_ID"), rs.getString("Model_Name"),
                    rs.getString("Company"), rs.getDouble("Price"), rs.getString("Status")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addBike() {
        String sql = "INSERT INTO Bikes (Model_Name, Company, Price) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, txtModel.getText());
            pstmt.setString(2, txtBrand.getText());
            pstmt.setDouble(3, Double.parseDouble(txtPrice.getText()));
            pstmt.executeUpdate();
            loadData("SELECT * FROM Bikes");
            txtModel.setText(""); txtBrand.setText(""); txtPrice.setText("");
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Input Error: " + e.getMessage()); }
    }

    private void processSale(JTable table) {
        int row = table.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a bike first!"); return; }
        
        int id = Integer.parseInt(table.getValueAt(row, 0).toString());
        String customer = JOptionPane.showInputDialog("Enter Customer Name:");
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            conn.setAutoCommit(false); // Enable Transaction 
            
            // 1. Update Status [cite: 15]
            PreparedStatement p1 = conn.prepareStatement("UPDATE Bikes SET Status='Sold' WHERE Bike_ID=?");
            p1.setInt(1, id); p1.executeUpdate();
            
            // 2. Log Transaction [cite: 15]
            PreparedStatement p2 = conn.prepareStatement("INSERT INTO Sales (Bike_ID, Customer_Name) VALUES (?, ?)");
            p2.setInt(1, id); p2.setString(2, customer); p2.executeUpdate();
            
            conn.commit();
            loadData("SELECT * FROM Bikes");
            JOptionPane.showMessageDialog(this, "Sale Processed Successfully!");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteBike(JTable table) {
        int row = table.getSelectedRow();
        if (row == -1) return;
        
        int id = Integer.parseInt(table.getValueAt(row, 0).toString());
        int confirm = JOptionPane.showConfirmDialog(this, "Permanently delete Bike ID " + id + "?");
        
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
                 PreparedStatement pstmt = conn.prepareStatement("DELETE FROM Bikes WHERE Bike_ID=?")) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
                loadData("SELECT * FROM Bikes");
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private void showReport() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT s.Sale_ID, b.Model_Name, s.Customer_Name, s.Sale_Date " +
                                            "FROM Sales s JOIN Bikes b ON s.Bike_ID = b.Bike_ID")) {
            
            StringBuilder sb = new StringBuilder("--- SALES HISTORY REPORT ---\n\n");
            while (rs.next()) {
                sb.append("Sale #").append(rs.getInt(1))
                  .append(" | ").append(rs.getString(2))
                  .append(" | Sold to: ").append(rs.getString(3))
                  .append("\n");
            }
            JOptionPane.showMessageDialog(this, new JTextArea(sb.toString()));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void main(String[] args) { new BikeShowroomApp(); }
}