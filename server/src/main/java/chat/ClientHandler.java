package chat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {

    private final Socket socket;
    private final ChatServer server;
    private final DataInputStream in;
    private final DataOutputStream out;

    private String name;

    public ClientHandler(Socket socket, ChatServer server) {
        try {
            this.name = "";
            this.socket = socket;
            this.server = server;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> {
                while (true) {
                    try {
                        authenticate();
                        readMessages();
                    } finally {
                        closeConnection();
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException("Не могу содать обработчик для клиента", e);
        }
    }

    private void authenticate() {
        while (true) {
            try {
                final String str = in.readUTF();
                if (str.startsWith("/auth")) { // auth login1 pass1
                    final String[] split = str.split("\\s");
                    final String login = split[1];
                    final String pass = split[2];
                    final String nickname = server.getAuthService().getNicknameByLoginAndPassword(login, pass);
                    if (nickname != null) {
                        if (!server.isNicknameBusy(nickname)) {
                            sendMessage("/authOk " + nickname);
                            this.name = nickname;
                            server.broadcast("Пользователь " + nickname + " зашел в чат");
                            server.subscribe(this);
                            break;
                        } else {
                            sendMessage("Уже произведен вход в учетную запись");
                        }
                    } else {
                        sendMessage("Неверные логин или пароль");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public void closeConnection() {
        try {
            server.unsubscribe(this);
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String msg) {
        try {
            System.out.println("SERVER: Send message to " + name + ": " + msg);
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void readMessages() {
        try {
            while (true) {
                final String strFromClient = in.readUTF();
                if (strFromClient.startsWith("/")) {
                    if (strFromClient.equals("/end")) {
                        break;
                    }
                    if (strFromClient.startsWith("/w ")) {
                        String[] tokens = strFromClient.split("\\s");
                        String nick = tokens[1];
                        String msg = strFromClient.substring(4 + nick.length());
                        server.sendMsgToClient(this, nick, msg);
                    }
                    continue;
                }
                System.out.println("Server: Получено сообщение от " + name + ": " + strFromClient);
                sendMessage(name + ": " + strFromClient);
                server.broadcast(name + ": " + strFromClient);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }
}
