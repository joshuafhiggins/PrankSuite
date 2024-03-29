package me.toast.pranksuite;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.esotericsoftware.kryonet.Server;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PSServer extends Application {

    public static Server SERVER;

    public static Button refresh;
    public static Button send;

    public static ListView<String> thirdList;
    private String selectedWindow, selectedWallpaper, selectedSound, selectedHTML;

    public static ListView<String> clientList;
    private String selectedClient;

    public static ListView<Action> actions;
    private Action selectedAction;

    public static List<String> fetchedWindows = new ArrayList<>();
    public static HashMap<Integer, String> connectedClients = new HashMap<>();

    public static Label whatIsThirdList;

    public static Connection currentFileTransfer;
    public static FileTransferListener ftl;

    //public static TextFieldListCell<String> defaultCell;

    public static Listener LISTENER = new Listener() {
        public void connected(Connection connection) {
            connectedClients.put(connection.getID(), connection.getRemoteAddressTCP().getHostString());
             Platform.runLater(() -> clientList.getItems().add(connection.getRemoteAddressTCP().getHostString()));
        }
        public void disconnected(Connection connection) {
            Platform.runLater(() -> {
                clientList.getItems().remove(connectedClients.get(connection.getID()));
                connectedClients.remove(connection.getID());
            });
        }
        public void received(Connection connection, Object object) {
            if (object instanceof Packets.OpenWindowsStartResponse) {
                System.out.println("Starting to receive OpenWindowsResponse packets!");
                fetchedWindows.clear();
            }
            if (object instanceof Packets.OpenWindowsResponse response) {
                fetchedWindows.add(response.openWindow);
            }
            if (object instanceof Packets.OpenWindowsFinalResponse) {
                Platform.runLater(() -> {
                    thirdList.getItems().clear();
                    thirdList.getItems().addAll(fetchedWindows);
                });
            }
            if (object instanceof Packets.FileTransferFinish && currentFileTransfer != null) {
                currentFileTransfer.removeListener(ftl);
                currentFileTransfer = null;
            }
        }
    };

    public static void main(String[] args) throws IOException {
        SERVER = new Server();
        Packets.RegisterPackets(SERVER.getKryo());
        Thread thread = new Thread(SERVER); thread.start();
        SERVER.bind(54555);
        SERVER.addListener(LISTENER);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Prank Suite - Server");
        stage.setResizable(false);

        refresh = new Button("Refresh");
        refresh.setVisible(false);
        refresh.setOnAction(e -> {
            if (selectedAction == Action.WINDOW) {
                thirdList.getItems().clear();
                askForWindows(selectedClient);
            }
            if (selectedAction == Action.WALLPAPER) {
                thirdList.getItems().clear();
                File[] files = getFilesInDirectory("./assets/backgrounds", "jpg");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
            }
            if (selectedAction == Action.SOUND) {
                thirdList.getItems().clear();
                File[] files = getFilesInDirectory("./assets/sounds", "mp3");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
            }
            if (selectedAction == Action.POPUP_HTML) {
                thirdList.getItems().clear();
                File[] files = getFilesInDirectory("./assets/html", "html");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
            }
        });

        send = new Button("Send");
        send.setVisible(false);
        send.setOnAction(e -> {
            if (selectedAction == Action.WINDOW) {
                if (fetchedWindows.contains(selectedWindow)) {
                    Packets.CloseWindow request = new Packets.CloseWindow();
                    request.nameOfWindow = selectedWindow;
                    System.out.println("Sending Close Window Request...");
                    try {
                        Objects.requireNonNull(getConnectionFromIP(selectedClient)).sendTCP(request);
                    } catch (NullPointerException except) {
                        System.out.println("Client IP given was invalid!");
                    }
                    askForWindows(selectedClient);
                }
            }
            if (selectedAction == Action.WALLPAPER) {
                if (currentFileTransfer != null || selectedWallpaper == null)
                    return;

                currentFileTransfer = Objects.requireNonNull(getConnectionFromIP(selectedClient));

                File file = new File("./assets/backgrounds/" + selectedWallpaper);
                InputStream in;
                try {
                    in = new FileInputStream(file);
                } catch (FileNotFoundException ex) {
                    return;
                }
                ftl = new FileTransferListener(in, selectedWallpaper, Action.WALLPAPER, currentFileTransfer);
                currentFileTransfer.addListener(ftl);
            }
            if (selectedAction == Action.SOUND) {
                if (currentFileTransfer != null || selectedSound == null)
                    return;

                currentFileTransfer = Objects.requireNonNull(getConnectionFromIP(selectedClient));

                File file = new File("./assets/sounds/" + selectedSound);
                InputStream in;
                try {
                    in = new FileInputStream(file);
                } catch (FileNotFoundException ex) {
                    return;
                }
                ftl = new FileTransferListener(in, selectedSound, Action.SOUND, currentFileTransfer);
                currentFileTransfer.addListener(ftl);
            }
            if (selectedAction == Action.POPUP) {
                Packets.TriggerPopup request = new Packets.TriggerPopup();
                request.title = thirdList.getItems().get(0);
                request.message = thirdList.getItems().get(1);
                request.button = thirdList.getItems().get(2);
                System.out.println("Sending Popup Request...");
                try { Objects.requireNonNull(getConnectionFromIP(selectedClient)).sendTCP(request); } catch (NullPointerException except) {System.out.println("Client IP given was invalid!");}
            }
            if (selectedAction == Action.POPUP_HTML) {
                if (currentFileTransfer != null || selectedHTML == null)
                    return;

                currentFileTransfer = Objects.requireNonNull(getConnectionFromIP(selectedClient));

                File file = new File("./assets/html/" + selectedHTML);
                InputStream in;
                try {
                    in = new FileInputStream(file);
                } catch (FileNotFoundException ex) {
                    return;
                }
                ftl = new FileTransferListener(in, selectedHTML, Action.POPUP_HTML, currentFileTransfer);
                currentFileTransfer.addListener(ftl);
            }
        });

        thirdList = new ListView<>();
        thirdList.setPrefWidth(300);
        thirdList.setPrefHeight(600);
        thirdList.setEditable(false);
        thirdList.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (selectedAction == Action.WINDOW)
                selectedWindow = newValue;
            if (selectedAction == Action.WALLPAPER)
                selectedWallpaper = newValue;
            if (selectedAction == Action.SOUND)
                selectedSound = newValue;
            if (selectedAction == Action.POPUP_HTML)
                selectedHTML = newValue;
        });

        clientList = new ListView<>();
        clientList.setPrefWidth(300);
        clientList.setPrefHeight(600);
        clientList.setEditable(false);
        clientList.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> selectedClient = newValue);

        actions = new ListView<>();
        actions.setPrefWidth(300);
        actions.setPrefHeight(600);
        actions.setEditable(false);
        actions.getItems().addAll(Action.WINDOW, Action.WALLPAPER, Action.SOUND, Action.POPUP, Action.POPUP_HTML);
        actions.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            selectedAction = newValue;
            send.setVisible(true);
            thirdList.getItems().clear();
            if (newValue == Action.WINDOW) {
                whatIsThirdList.setText("Close Windows   ");
                askForWindows(selectedClient);
            }
            if (newValue == Action.WALLPAPER) {
                whatIsThirdList.setText("Set Wallpaper   ");
                File[] files = getFilesInDirectory("./assets/backgrounds", "jpg");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
            }
             if (newValue == Action.SOUND) {
                whatIsThirdList.setText("Play Sound   ");
                File[] files = getFilesInDirectory("./assets/sounds", "mp3");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
             }
             if (newValue == Action.POPUP) {
                whatIsThirdList.setText("Trigger Popup   ");
                refresh.setVisible(false);
                thirdList.setEditable(true);
                thirdList.setCellFactory(TextFieldListCell.forListView());
                thirdList.getItems().addAll("Title", "Message", "Button");
            }
             if (newValue == Action.POPUP_HTML) {
                whatIsThirdList.setText("Trigger HTML Popup   ");
                File[] files = getFilesInDirectory("./assets/html", "html");
                for (File file : files)
                    thirdList.getItems().add(file.getName());
             }
             if (newValue != Action.POPUP) {
                 refresh.setVisible(true);
                 thirdList.setEditable(false);
             }
        });

        whatIsThirdList = new Label("");

        VBox openHost = new VBox();
        openHost.setAlignment(Pos.BOTTOM_CENTER);
        HBox tmp = new HBox();
        tmp.setAlignment(Pos.BOTTOM_CENTER);
        tmp.getChildren().addAll(whatIsThirdList, send, refresh);
        openHost.getChildren().addAll(tmp, thirdList);

        VBox actionPanel = new VBox();
        actionPanel.setAlignment(Pos.BOTTOM_CENTER);
        actionPanel.getChildren().addAll(new Label("Actions"), actions);

        VBox clientPanel = new VBox();
        clientPanel.setAlignment(Pos.BOTTOM_CENTER);
        clientPanel.getChildren().addAll(new Label("Connected Clients"), clientList);

        BorderPane borderPane = new BorderPane();
        borderPane.setLeft(clientPanel);
        borderPane.setCenter(actionPanel);
        borderPane.setRight(openHost);

        Scene scene = new Scene(borderPane, 900, 650);
        stage.setScene(scene);
        stage.show();
    }

    public void askForWindows(String ip) {
        Packets.OpenWindowsRequest request = new Packets.OpenWindowsRequest();
        System.out.println("Sending Window Request...");
        try { Objects.requireNonNull(getConnectionFromIP(ip)).sendTCP(request); } catch (NullPointerException except) {System.out.println("IP given was invalid!");}
    }

    public File[] getFilesInDirectory(String directory, String fileExtension) {
        return new File(directory).listFiles((dir1, name) -> name.toLowerCase().endsWith("."+fileExtension));
    }

    public static Connection getConnectionFromIP(String ip) {
        for (int i = 0; i < SERVER.getConnections().length; i++)
            if(SERVER.getConnections()[i].getRemoteAddressTCP().getHostString().equals(ip))
                return SERVER.getConnections()[i];
        return null;
    }

    @Override
    public void stop() throws Exception {
        SERVER.stop();
        super.stop();
    }
}
