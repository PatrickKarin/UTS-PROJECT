import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChatClient {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusLabel;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private String clientName;
    private String serverIP = "localhost"; // Default value

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new ChatClient();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, 
                    "Failed to start client: " + e.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public ChatClient() {
        initializeGUI();
        setupConnectionPanel();
    }

    private void initializeGUI() {
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout());

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel(" Offline");
        statusLabel.setForeground(Color.RED);
        statusPanel.add(new JLabel("Status:"));
        statusPanel.add(statusLabel);
        frame.add(statusPanel, BorderLayout.NORTH);

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.setEnabled(false);
        inputField.addActionListener(e -> sendMessage());
        inputPanel.add(inputField, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        frame.add(inputPanel, BorderLayout.SOUTH);
    }

    private void setupConnectionPanel() {
        // Connection dialog
        JPanel connectionPanel = new JPanel(new GridLayout(3, 2));
        
        JTextField nameField = new JTextField("User" + (int)(Math.random()*1000));
        JTextField ipField = new JTextField(serverIP);
        
        connectionPanel.add(new JLabel("Your Name:"));
        connectionPanel.add(nameField);
        connectionPanel.add(new JLabel("Server IP:"));
        connectionPanel.add(ipField);
        
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> {
            clientName = nameField.getText().trim();
            serverIP = ipField.getText().trim();
            
            if (clientName.isEmpty()) {
                clientName = "User" + (int)(Math.random()*1000);
            }
            
            frame.setTitle("Chat Client - " + clientName);
            connectToServer();
        });
        
        connectionPanel.add(new JLabel(""));
        connectionPanel.add(connectButton);

        JOptionPane.showMessageDialog(
            frame, 
            connectionPanel, 
            "Connection Settings", 
            JOptionPane.PLAIN_MESSAGE
        );
        
        frame.setVisible(true);
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                if (socket != null) {
                    socket.close();
                }
                
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIP, 12345), 3000); // 3s timeout
                
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Send client name
                out.println(clientName);
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(" Online");
                    statusLabel.setForeground(new Color(0, 180, 0));
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    chatArea.append("Connected to server at " + serverIP + "\n");
                });
                
                // Start listening for messages
                listenForMessages();
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("Connection failed: " + e.getMessage() + "\n");
                    statusLabel.setText(" Offline");
                    statusLabel.setForeground(Color.RED);
                    inputField.setEnabled(false);
                    sendButton.setEnabled(false);
                });
                
                // Try to reconnect
                autoReconnect();
            }
        }).start();
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(finalMessage + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Disconnected from server: " + e.getMessage() + "\n");
                statusLabel.setText(" Offline");
                statusLabel.setForeground(Color.RED);
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
            });
            
            autoReconnect();
        }
    }

    private void autoReconnect() {
        SwingUtilities.invokeLater(() -> {
            int option = JOptionPane.showConfirmDialog(
                frame,
                "Connection lost. Try to reconnect?",
                "Reconnect",
                JOptionPane.YES_NO_OPTION
            );
            
            if (option == JOptionPane.YES_OPTION) {
                connectToServer();
            }
        });
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            SwingUtilities.invokeLater(() -> {
                chatArea.append("You: " + message + "\n");
                inputField.setText("");
            });
        }
    }
}