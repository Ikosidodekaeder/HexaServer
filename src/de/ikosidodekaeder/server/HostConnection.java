package de.ikosidodekaeder.server;

import java.net.Socket;
import java.util.UUID;

/**
 * Created by Sven on 20.02.2018.
 */

public class HostConnection extends Connection {


    private long lastPacket = 0;
    private String roomName = "NullRoom";

    public HostConnection(UUID uuid, Socket socket, String roomName) {
        super(uuid, socket);
        lastPacket = System.currentTimeMillis();
        this.roomName = roomName;
    }

    public long getLastPacket() {
        return lastPacket;
    }

    public void setLastPacket(long lastPacket) {
        this.lastPacket = lastPacket;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }
}
