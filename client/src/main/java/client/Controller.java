package client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextArea textArea;
    @FXML
    public TextField textField;
    @FXML
    public HBox authPanel;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;

    Stage regStage;

    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    final String IP_ADDRESS = "localhost";
    final int PORT = 8189;

    private boolean authenticated;
    private String nick;
    private String login;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setManaged(authenticated);
        msgPanel.setVisible(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);
        if (!authenticated) {
            nick = "";
        }
        textArea.clear();
        setTitle(nick);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthenticated(false);

        regStage = createRegWindow();

        Platform.runLater(() -> {
            Stage stage = (Stage) textField.getScene().getWindow();
            stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent event) {
                    System.out.println("bue");
                    if (socket != null && !socket.isClosed()) {
                        try {
                            out.writeUTF("/end");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });
    }

    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        System.out.println(str);

                        if (str.equals("/end")) {
                            throw new RuntimeException();
                        }

                        if (str.startsWith("/authok ")) {
                            nick = str.split(" ")[1];
                            login = str.split(" ")[2];
                            setAuthenticated(true);

                            //создание локального файла с историей для данного пользователя
                            createFileHistory();

                            break;
                        }

                        textArea.appendText(str + "\n");
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                break;
                            }

                            if (str.startsWith("/clientlist ")) {
                                String[] token = str.split(" ");
                                Platform.runLater(() -> {
                                    clientList.getItems().clear();
                                    for (int i = 1; i < token.length; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            }

                            if (str.startsWith("/yournickis")) {
                                nick = str.split(" ")[1];
                                setTitle(nick);
                            }

                            if (str.startsWith("/history")) {
                                String strHistory = str.split(" ", 2)[1];
                                textArea.appendText(strHistory + "\n");
                            }

                        } else {
                            textArea.appendText(str + "\n");

                            addMessageToFile(str);
                        }
                    }
                }catch (RuntimeException e){
                    System.out.println("re");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("мы отключились");
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg() {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF("/auth " + loginField.getText().trim() + " " + passwordField.getText().trim());
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //создание локального файла с историей
    private void createFileHistory() throws IOException {
        String fileSeparator = System.getProperty("file.separator");
        String historyFile = "History" + fileSeparator + "history_" +login + ".txt";
        System.out.println(historyFile);

        File dir = new File("History");
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Директория для файлов с историей создана");
            } else {
                throw new RuntimeException("Не удалось создать директорию для файлов с историей");
            }
        }

        File file = new File(historyFile);

        if (!file.exists()) {
            if (file.createNewFile()) {
                System.out.println("Файл с историей для " + login + " создан");
            } else {
                throw new RuntimeException("Не удалось создать файл с историей");
            }
        }
    }

    //      метод добавления сообщений в локальный файл
    private void addMessageToFile(String str) {
        String fileSeparator = System.getProperty("file.separator");
        String historyFile = "History" + fileSeparator + "history_" + login + ".txt";

        try(FileWriter writer = new FileWriter(historyFile, true))
        {
            writer.append(str);
            writer.append('\n');
            writer.flush();
        }
        catch(IOException ex){
            System.out.println(ex.getMessage());
        }
    }

    private void setTitle(String nick) {
        Platform.runLater(() -> {
            ((Stage) textField.getScene().getWindow()).setTitle("Super chat " + nick);
        });

    }

    public void clickClientList(MouseEvent mouseEvent) {
        System.out.println(clientList.getSelectionModel().getSelectedItem());
        String receiver = clientList.getSelectionModel().getSelectedItem();
        textField.setText("/w " + receiver + " ");
    }

    private Stage createRegWindow() {
        Stage stage = null;

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();

            stage = new Stage();
            stage.setTitle("Registration ");
            stage.setScene(new Scene(root, 300, 200));
            stage.initStyle(StageStyle.UTILITY);
            stage.initModality(Modality.APPLICATION_MODAL);

            RegController regController = fxmlLoader.getController();
            regController.controller = this;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return stage;
    }

    public void showRegWindow(ActionEvent actionEvent) {
        regStage.show();
    }

    public void tryRegistration(String login, String password ,String nickname){
        String msg = String.format("/reg %s %s %s", login, password ,nickname);

        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
