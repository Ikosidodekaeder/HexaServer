package de.ikosidodekaeder.server;


import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;



/**
 * Created by Sven on 19.02.2018.
 */

public class Main {

    public static final int TIME_OUT = 30_000;
    private ServerSocket serverSocket;

    private boolean running = true;

    private List<HostConnection>            hosts = new ArrayList<>();
    private Map<ClientConnection, UUID>     clientHost = new HashMap<>();

    /**
     * Depending of the content of payload, specificly the TypeId part the returned Map
     * contains different sets of keys.
     *
     * Following Keys are always contained in the Map:
     *
     * "Packet" -> Contains the orignal payload
     * "TypeId" -> PacketType
     * "SourceId" -> UUID
     * "isCancelled" -> boolean
     * -----------------------------------|
     * The next only for certain TypeId's |
     * TypeId == Register                 |
     *                                    |
     * "Room" -> String                   |
     * -----------------------------------|
     * TypeId == JOIN                     |
     *                                    |
     * "DestinationId" -> UUID            |
     * "Username" -> String               |
     * "Version" -> String                |
     *------------------------------------|
     * TypeId == KEEPALIVE                |
     *                                    |
     * "SessionId" -> Integer             |
     *------------------------------------|
     * @param payload
     * @return
     */
    public static Map<String,Object> inspectPayloadOf(String payload){
        String[] items = payload.split(";");
        Map<String,Object> tmp =new Hashtable<String,Object>(){{
            put("Packet", payload);                                         // Whole serialized packet
            put("TypeId", PacketType.valueOf(Byte.parseByte(items[0])));    // Type of the packet
            put("SourceId", UUID.fromString(items[1]));                     // Sender UUID
            put("isCancelled", items[2].equals("1"));                       // Tells the client if allowed or not
        }};

        switch ((PacketType)tmp.get("TypeId")){
            case KEEPALIVE:
                tmp.put("SessionId",(Integer.parseInt(items[3])));
            case REGISTER:
                tmp.put("Room",(String)items[3]);
                break;
            case JOIN:
                tmp.put("DestinationId",UUID.fromString(items[3]));
                tmp.put("Username",items[4]);
                tmp.put("Version",items[5]);
                break;
            case SERVER_LIST:
                break;
        }
        return tmp;
    }

    public Main() {
        try {
            System.out.println("Trying...");
            serverSocket = new ServerSocket(25565);
            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }

        start();
    }

    public static void main(String[] args) {
        new Main();
    }

    public void start() {
        System.out.println("Started listening...");
        while (true) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();

                removeInactive();

                Thread thread = new Thread(new ListenThread(this, socket));
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public HostConnection getHost(UUID uuid) {
        for (HostConnection hostConnection : hosts) {
            if (hostConnection.getUuid().equals(uuid)) {
                return hostConnection;
            }
        }
        return null;
    }

    public HostConnection getHostFromClient(UUID clientUuid) {
        ClientConnection clientConnection = getClient(clientUuid);
        if (clientConnection == null) {
            return null;
        }
        UUID hostUuid = clientHost.get(clientConnection);
        HostConnection hostConnection = getHost(hostUuid);
        return hostConnection;
    }

    public boolean containsHost(UUID uuid) {
        return getHost(uuid) != null;
    }

    public ClientConnection getClient(UUID uuid) {
        for (ClientConnection connection : clientHost.keySet()) {
            if (connection.getUuid().equals(uuid)) {
                return connection;
            }
        }
        return null;
    }

    public boolean containsClient(UUID uuid) {
        return getClient(uuid) != null;
    }

    void onRegister(Map<String,Object> PacketContent,Socket socket){
        UUID senderId = (UUID) PacketContent.get("SourceId");
        String roomName = (String) PacketContent.get("Room");
        PacketType Type = ((PacketType)PacketContent.get("TypeId"));

        if (containsHost(senderId)) {
            System.out.println("Host already connected");
            return;
        }
        hosts.add(new HostConnection(senderId, socket, roomName));
        System.out.println("New Host registered: " + senderId.toString());
        System.out.println("======= " + Type.name() + ", " + senderId.toString() + ", " + roomName);
    }

    void onKeepAlive(HostConnection hostConnection, Map<String,Object> packetContent, boolean fromHost){

        if (fromHost) {
            // If this packet was sent from the host
            // send keep alive to all clients
            broadcastToClients(hostConnection, (String) packetContent.get("Packet"));
            sendToHost(hostConnection, (String) packetContent.get("Packet"));
        } else {
            // If this packet was sent from a client
            // send to host
            UUID senderId = (UUID) packetContent.get("SourceId");
            hostConnection = getHostFromClient(senderId);
            if (hostConnection != null) {
                sendToHost(hostConnection, (String) packetContent.get("Packet"));
            } else {
                System.out.println("Found no host when returning client keep alive");
            }
        }
    }

    void onJoin(HostConnection hostConnection, Socket socket, Map<String,Object> PacketContent){
        if (hostConnection !=  null) {
            System.out.println("Join From Host");
            // If this packet was sent from the host
            // send join to all clients
            broadcastToClients(hostConnection,(String) PacketContent.get("Packet"));


            if ((Boolean) PacketContent.get("isCancelled")) {
                // Remove the socket if the host did not accept
                UUID clientId =((UUID)PacketContent.get("DestinationId"));
                ClientConnection client = getClient(clientId);
                try {
                    client.getSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientHost.remove(client);
            }
        } else {
            // If this packet was sent from a client
            // Store the socket
            if (containsClient(((UUID)PacketContent.get("SourceId")))) {
                System.out.println(((UUID)PacketContent.get("SourceId")) + " already connected");
                return;
            }

            UUID hostId = ((UUID)PacketContent.get("DestinationId"));
            UUID senderId = ((UUID)PacketContent.get("SourceId"));

            // Send it to the host
            HostConnection host = getHost(hostId);
            if (host != null) {
                ClientConnection clientConnection = new ClientConnection(senderId, socket);
                clientHost.put(clientConnection, senderId);
                System.out.println("Registered new client: " + senderId);

                sendToHost(host, ((String)PacketContent.get("Packet")));
                System.out.println("Sent to host");
            } else {
                System.out.println("Error, host null");
            }
        }
    }

    public void onServerList(UUID sender, Socket socket) {
        StringBuilder packetPayload = new StringBuilder();
        packetPayload
                .append(PacketType.SERVER_LIST.ID).append(";")
                .append(sender.toString()).append(";")
                .append("0;");
        for (HostConnection hostConnection : hosts) {
            packetPayload
                    .append(hostConnection.getUuid().toString()).append(",")
                    .append(hostConnection.getRoomName()).append(";");
        }
        sendPacketToClient(socket, packetPayload.toString());
    }

    public void handlePacket(String packet, Socket socket) {
        Map<String,Object> packetContent = inspectPayloadOf(packet);
        PacketType type = ((PacketType)packetContent.get("TypeId"));

        /*
            try to create a hostConnection anyway, if it succeds fine if not the appropiate end
            will know what to do
         */
        HostConnection hostConnection = getHost((UUID)packetContent.get("SourceId"));
        if (hostConnection != null) {
            hostConnection.setLastPacket(System.currentTimeMillis());
        } else {
            ClientConnection clientConnection = getClient((UUID)packetContent.get("SourceId"));
            if (clientConnection != null) {
                clientConnection.setLastPacket(System.currentTimeMillis());
            }
        }

        if (type == null)
           return;
        switch (type){
            case REGISTER:
                onRegister(packetContent,socket);
                break;
            case KEEPALIVE:
            {
                boolean fromHost = hostConnection != null;
                onKeepAlive(hostConnection, packetContent, fromHost);
                break;
            }
            case JOIN:
            {
                onJoin(hostConnection,socket,packetContent);
                break;
            }
            case SERVER_LIST:
                onServerList((UUID)packetContent.get("SourceId"), socket);
                break;
            default:
                return;
        }
    }

    public void broadcastToClients(HostConnection host, String packet) {
        for (ClientConnection client : clientHost.keySet()) {
            UUID hostUuid = clientHost.get(client);
            if (hostUuid.equals(host.getUuid())) {
                continue;
            }

            try {
                PrintWriter out = new PrintWriter(client.getSocket().getOutputStream(), true);
                out.println(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendPacketToClient(Socket socket, String packet) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendToHost(HostConnection host, String packet) {
        try {
            PrintWriter out = new PrintWriter(host.getSocket().getOutputStream(), true);
            out.println(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private List<ClientConnection>              toRemoveClients = new ArrayList<>();
    private List<HostConnection>                toRemoveHosts   = new ArrayList<>();

    /**
     * Removes clients and hosts with closed sockets
     */
    public void removeInactive() {
        // Check if any host sockets are closed
        for (ClientConnection connection : clientHost.keySet()) {
            if (System.currentTimeMillis() - connection.getLastPacket() >= TIME_OUT) {
                System.out.println("Removing Client " + connection.getUuid().toString());
                try {
                    connection.getSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                toRemoveClients.add(connection);
            }
        }
        // Check if any client sockets are closed
        for (HostConnection connection : hosts) {
            if (System.currentTimeMillis() - connection.getLastPacket() >= TIME_OUT) {
                System.out.println("Removing Host " + connection.getUuid().toString());
                try {
                    connection.getSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                toRemoveHosts.add(connection);
            }
        }

        // Remove all clients
        if (!toRemoveClients.isEmpty()) {
            for (ClientConnection clientConnection : toRemoveClients) {
                clientHost.remove(clientConnection);
            }
            toRemoveClients.clear();
        }

        // Remove all hosts
        if (!toRemoveHosts.isEmpty()) {
            hosts.removeAll(toRemoveHosts);
            toRemoveHosts.clear();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Map<ClientConnection, UUID> getClientHost() {
        return clientHost;
    }
}
