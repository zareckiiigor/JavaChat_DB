package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String nick;
    private String login;

    public ClientHandler(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);

                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/reg ")) {
                            String[] token = str.split(" ");

                            if (token.length < 4) {
                                continue;
                            }

                            boolean succeed = server
                                    .getAuthService()
                                    .registration(token[1], token[2], token[3]);
                            if (succeed) {
                                sendMsg("Регистрация прошла успешно");
                            } else {
                                sendMsg("Регистрация  не удалась. \n" +
                                        "Возможно логин уже занят, или данные содержат пробел");
                            }
                        }

                        if (str.startsWith("/auth ")) {
                            String[] token = str.split(" ");

                            if (token.length < 3) {
                                continue;
                            }

                            String newNick = server.getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]);

                            login = token[1];

                            if (newNick != null) {
                                if (!server.isLoginAuthorized(login)) {
                                    sendMsg("/authok " + newNick + " " + login);
                                    nick = newNick;
                                    server.subscribe(this);
                                    System.out.println("Клиент: " + nick + " подключился"+ socket.getRemoteSocketAddress());
                                    socket.setSoTimeout(0);

//                                    получение истории переписки - с файла, а не из БД
//                                    sendMsg(SQLHandler.getMessageForNick(nick));

//                                    получений истории из локального файла
                                    historyMessagesInFile();

                                    break;
                                } else {
                                    sendMsg("С этим логином уже прошли аутентификацию");
                                }
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }
                        }
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                sendMsg("/end");
                                break;
                            }
                            if (str.startsWith("/w ")) {
                                String[] token = str.split(" ", 3);

                                if (token.length < 3) {
                                    continue;
                                }

                                server.privateMsg(this, token[1], token[2]);
                            }

                            if (str.startsWith("/chnick")) {
                                String[] token = str.split(" ", 2);
                                if (token.length < 2) {
                                    continue;
                                }
                                if (token[1].contains(" ")) {
                                    sendMsg("Ник не может содержать пробелов");
                                    continue;
                                }
                                if (server.getAuthService().changeNick(this.nick, token[1])) {
                                    sendMsg("/yournickis " + token[1]);
                                    this.nick = token[1];
                                    server.broadcastClientList();
                                } else {
                                    sendMsg("Не удалось сменить ник. Ник " + token[1] + " уже существует!");
                                }
                            }
                        } else {
                            server.broadcastMsg(nick, str);
                        }
                    }
                }catch (SocketTimeoutException e){
                    sendMsg("/end");
                }
                ///////
                catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Клиент отключился");
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

//    получений истории из локального файла
    private void historyMessagesInFile() throws IOException {
        String fileSeparator = System.getProperty("file.separator");
        String historyFile = "History" + fileSeparator + "history_" + login + ".txt";

        File file = new File(historyFile);

//        если файл существует, то прочитать историю
        if (file.exists()) {

            FileReader fr = new FileReader(historyFile);
            BufferedReader br = new BufferedReader(fr);
            String tempStr;

            List<String> tmp = new ArrayList<>();

            do {
                tempStr = br.readLine();
                tmp.add(tempStr);
            } while (tempStr != null);

            int start = tmp.size() - 101;
            if (start < 0) {
                start = 0;
            }

            for (int i = start; i < tmp.size() - 1; i++) {
                sendMsg("/history " + tmp.get(i));
            }

            br.close();
            fr.close();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNick() {
        return nick;
    }

    public String getLogin() {
        return login;
    }
}
