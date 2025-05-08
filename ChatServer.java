import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();
    private static DefaultListModel<String> clientListModel = new DefaultListModel<>();
    private static JTextArea logArea;
    private static JFrame serverFrame;
    private static JLabel ipLabel;

    public static void main(String[] args) {
        initializeServerGUI();
        showNetworkIPs();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            appendToLog("Server started on port " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            appendToLog("Server exception: " + e.getMessage());
        }
    }

    private static void initializeServerGUI() {
        serverFrame = new JFrame("Chat Server Control Panel");
        serverFrame.setSize(600, 500);
        serverFrame.setLayout(new BorderLayout());

        // Server info panel
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ipLabel = new JLabel("Server IP: Detecting...");
        infoPanel.add(ipLabel);
        serverFrame.add(infoPanel, BorderLayout.NORTH);

        // Client list panel
        JPanel clientPanel = new JPanel(new BorderLayout());
        clientPanel.add(new JLabel("Connected Clients:"), BorderLayout.NORTH);
        
        JList<String> clientList = new JList<>(clientListModel);
        clientList.setCellRenderer(new ClientListRenderer());
        clientPanel.add(new JScrollPane(clientList), BorderLayout.CENTER);
        serverFrame.add(clientPanel, BorderLayout.WEST);

        // Log panel
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(new JLabel("Chat Log:"), BorderLayout.NORTH);
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        serverFrame.add(logPanel, BorderLayout.CENTER);

        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverFrame.setVisible(true);
    }

    private static void showNetworkIPs() {
        try {
            StringBuilder ips = new StringBuilder("Server IPs: ");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        ips.append(addr.getHostAddress()).append(", ");
                    }
                }
            }
            
            ipLabel.setText(ips.toString().replaceAll(", $", ""));
            appendToLog(ips.toString());
        } catch (SocketException e) {
            appendToLog("Error detecting IPs: " + e.getMessage());
        }
    }

    public static void appendToLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void updateClientList() {
        SwingUtilities.invokeLater(() -> {
            clientListModel.clear();
            clientHandlers.forEach(client -> 
                clientListModel.addElement(client.getClientName() + " | " + client.getClientIP()));
        });
    }

    public static void broadcast(String message, ClientHandler excludeClient) {
        clientHandlers.forEach(client -> {
            if (client != excludeClient && client.isConnected()) {
                client.sendMessage(message);
            }
        });
    }

    private static class ClientListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                     boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value.toString().contains("|")) {
                c.setForeground(new Color(0, 100, 0)); // Dark green for online clients
            }
            return c;
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;
        private String clientIP;
        private boolean connected = true;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientIP = socket.getInetAddress().getHostAddress();
        }

        public String getClientName() {
            return clientName;
        }

        public String getClientIP() {
            return clientIP;
        }

        public boolean isConnected() {
            return connected;
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // First message is client name
                clientName = in.readLine();
                appendToLog(clientName + " connected from " + clientIP);
                updateClientList();
                
                broadcast(clientName + " has joined the chat", this);
                out.println("Welcome to the chat! You are: " + clientName);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String formattedMessage = clientName + ": " + inputLine;
                    appendToLog(formattedMessage);
                    broadcast(formattedMessage, this);
                }
            } catch (IOException e) {
                appendToLog(clientName + " connection error: " + e.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    appendToLog("Error closing connection: " + e.getMessage());
                }
                connected = false;
                appendToLog(clientName + " disconnected");
                updateClientList();
                broadcast(clientName + " has left the chat", this);
                clientHandlers.remove(this);
            }
        }
    }
}