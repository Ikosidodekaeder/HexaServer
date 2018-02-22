package de.ikosidodekaeder.server;

import java.net.Socket;
import java.util.UUID;

/**
 * Created by Sven on 20.02.2018.
 */

public class ClientConnection extends Connection {


    public ClientConnection(UUID uuid, Socket socket) {
        super(uuid, socket);
    }
}
