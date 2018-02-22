package de.ikosidodekaeder.server;

import java.net.Socket;
import java.util.UUID;

/**
 * Created by Sven on 20.02.2018.
 */

public class HostConnection extends Connection {


    private String roomName = "NullRoom";

    public HostConnection(UUID uuid, Socket socket, String roomName) {
        super(uuid, socket);
        this.roomName = roomName;
    }


    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }
}
