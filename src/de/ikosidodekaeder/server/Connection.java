package de.ikosidodekaeder.server;

import java.net.Socket;
import java.util.UUID;

/**
 * Created by Sven on 22.02.2018.
 */

public class Connection {

    private UUID uuid;
    private Socket socket;
    private long lastPacket = 0;

    public Connection(UUID uuid, Socket socket) {
        this.uuid = uuid;
        this.socket = socket;
        lastPacket = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public long getLastPacket() {
        return lastPacket;
    }

    public void setLastPacket(long lastPacket) {
        this.lastPacket = lastPacket;
    }
}
