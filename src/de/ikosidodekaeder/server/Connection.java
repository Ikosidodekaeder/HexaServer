package de.ikosidodekaeder.server;

import java.net.Socket;
import java.util.UUID;

/**
 * Created by Sven on 20.02.2018.
 */

public class Connection {

    private UUID uuid;
    private long lastPacket = 0;
    private Socket socket;

    public Connection(UUID uuid, Socket socket) {
        this.uuid = uuid;
        lastPacket = System.currentTimeMillis();
        this.socket = socket;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public long getLastPacket() {
        return lastPacket;
    }

    public void setLastPacket(long lastPacket) {
        this.lastPacket = lastPacket;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
}