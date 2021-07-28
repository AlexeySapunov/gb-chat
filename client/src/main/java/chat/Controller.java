package chat;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nick;

    @FXML
    public HBox clientPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public Button btnSend;
    @FXML
    public TextField textField;
    @FXML
    private TextArea textArea;
    @FXML
    private ListView<String> clientList;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField loginField;
    @FXML
    private HBox authPanel;

    private void connect() {
        try {
            socket = new Socket("localhost", 8189);
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            setAuth(false);

            new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);
                    while (true) { // Ждем сообщения об успешной авторизации ("/authOk")
                        final String msgAuth = in.readUTF();
                        System.out.println("CLIENT: Received message: " + msgAuth);
                        if (msgAuth.startsWith("/authOk")) {
                            setAuth(true);
                            nick = msgAuth.split("\\s")[1];
                            textArea.appendText("Успешная авторизация под ником " + nick + "\n");
                            break;
                        }
                        //noinspection ConstantConditions
                        if (setAuth(false)) {
                            socket.setSoTimeout(0);
                            return;
                        }
                        textArea.appendText(msgAuth + "\n");
                    }
                    while (true) {
                        String msgFromServer = in.readUTF();
                        System.out.println("CLIENT: Received message: " + msgFromServer);
                        if (msgFromServer.startsWith(nick)) {
                            msgFromServer = "[You] " + msgFromServer;
                        }
                        if ("/end".equalsIgnoreCase(msgFromServer)) {
                            break;
                        }
                        if (msgFromServer.startsWith("/clients")) {
                            final List<String> clients = new ArrayList<>(Arrays.asList(msgFromServer.split("\\s")));
                            clients.remove(0);
                            clientList.getItems().clear();
                            clientList.getItems().addAll(clients);
                            continue;
                        }
                        textArea.appendText(msgFromServer + "\n");
                    }
                } catch (SocketTimeoutException e) {
                    try {
                        throw e;
                    } catch (SocketTimeoutException s) {
                        s.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    try {
                        setAuth(false);
                        socket.close();
                        nick = "";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean setAuth(boolean isAuthSuccess) {
        authPanel.setVisible(!isAuthSuccess);
        authPanel.setManaged(!isAuthSuccess);

        msgPanel.setVisible(isAuthSuccess);
        msgPanel.setManaged(isAuthSuccess);

        clientPanel.setVisible(isAuthSuccess);
        clientPanel.setManaged(isAuthSuccess);
        return isAuthSuccess;
    }

    public void sendAuth() {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            System.out.println("CLIENT: Send auth message");
            out.writeUTF("/auth " + loginField.getText() + " " + passwordField.getText());
            loginField.clear();
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg() {
        try {
            final String msg = textField.getText();
            System.out.println("CLIENT: Send message to server: " + msg);
            out.writeUTF(msg);
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        connect();
    }

    public void selectClient(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            final String msg = textField.getText();
            String nickname = clientList.getSelectionModel().getSelectedItem();
            textField.setText("/w " + nickname + " " + msg);
            textField.requestFocus();
            textField.selectEnd();
        }
    }
}
