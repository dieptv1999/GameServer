package com.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.*;

import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class TaskThread extends Thread {

    private int id;
    BlockingQueue<String> IQueue;
    BlockingQueue<String> OQueue = new LinkedBlockingDeque<>(50);
    List<Player> players = new ArrayList<>();
    List<Socket> sockets = new ArrayList<>();
    List<Enemy> enemies = new ArrayList<>();
    List<Enemy> tmp = new ArrayList<>();
    List<String> items = new ArrayList<>();
    List<Position> positionDefault = new ArrayList<>();
    Integer ready = 0;
    Integer numPlayer = 0;
    Integer guards = 0;
    Integer numEnermy = 0;
    ObjectMapper mapper = new ObjectMapper();
    private Clock clock;
    ReceiveThread receiveThread;
    SendThread sendThread;

    public TaskThread(int id, BlockingQueue<String> IQueue, ReceiveThread receiveThread) {
        this.id = id;
        this.IQueue = IQueue;
        this.receiveThread = receiveThread;
        positionDefault.add(new Position(0, 0, 2.98f));
        positionDefault.add(new Position(-30, 0, 2.98f));
        positionDefault.add(new Position(30, 0, 2.98f));
        clock = Clock.systemDefaultZone();
        sendThread = new SendThread(sockets, OQueue);
        sendThread.start();
    }

    @Override
    public void run() {
        long cycle = clock.millis();
        long cycleEnemy = cycle;
        long time_begin = cycle;
        for (; ; ) {
            try {
                String data = IQueue.poll(1200, TimeUnit.MICROSECONDS);
                if (data != null) handle(data);
                long curr = clock.millis();
                if (curr - cycle >= 18) {
                    try {
                        sendData();
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    //empty queue
                    if (ready != 0 && (guards <= 0 || numPlayer <= 0)) {
                        ready = 0;
                        enemies.clear();
                        items.clear();
                        IQueue.clear();
                        OQueue.clear();
                        sockets.clear();
                        synchronized (LoginThread.isRunning) {
                            LoginThread.isRunning.replace(id, false);
                        }
                    }
                    cycle = curr;
                }
                if (curr - cycleEnemy >= 3995) {
                    createEnermy((int) (Math.random() * 5 + 1));
                    cycleEnemy = curr;
                }
                if (curr - time_begin >= 60000 && curr - cycleEnemy >= 25000) {
                    createEnermy((int) (Math.random() * 4 + 6));
                    cycleEnemy = curr;
                }
                if (curr - time_begin >= 120000 && curr - cycleEnemy >= 45000) {
                    createEnermy((int) (Math.random() * 4 + 10));
                    cycleEnemy = curr;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void addPlayer(Socket socket, String name, String plane) {
        Player player = new Player(name, true);
        player.setPosition(positionDefault.get(numPlayer));
        player.setPlane(Integer.parseInt(plane));
        player.setHealth(100);
        player.setShield("");
        players.add(player);
        synchronized (sockets) {
            sockets.add(socket);
        }
        numPlayer++;
    }

    //xử lí dữ liệu đầu vào
    public void handle(String message) {
        String[] data = message.split("\\|");
        if (data.length > 0) {
            if (data[0].equals("MOVE")) {
                Float x = Float.parseFloat(data[1]);
                Float y = Float.parseFloat(data[2]);
                Float z = Float.parseFloat(data[3]);
                String name = data[4];
                for (Player player : players)
                    if (player.getName().equals(name)) {
                        player.setPosition(new Position(x, y, z));
                        break;
                    }
            } else if (data[0].equals("READY")) {
                guards++;
            } else if (data[0].equals("ITEM")) {
                if (data[1].equals("SHIELD")) {
                    int index = items.indexOf(data[2]);
                    if (index != -1) {
                        items.remove(index);
                        String name = data[3];
                        for (Player player : players)
                            if (player.getName().equals(name)) {
                                player.setShield(data[2]);
                                break;
                            }
                        try {
                            OQueue.put(message);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (data[1].equals("HEALTH")) { //HEALTH
                    int index = items.indexOf(data[2]);
                    if (index != -1) {
                        items.remove(index);
                        String name = data[3];
                        for (Player player : players)
                            if (player.getName().equals(name)) {
                                player.setHealth(player.getHealth() + 50);
                                break;
                            }
                        try {
                            OQueue.put(message);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    //TODO
                    //add score player
                } else { //GUN
                    int index = items.indexOf(data[2]);
                    if (index != -1) {
                        items.remove(index);
                        try {
                            OQueue.put(message);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else if (data[0].equals("SHOT")) {//bắn đạn
                try {
                    OQueue.put(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (data[0].equals("SHOTED")) {
                try {
                    OQueue.put(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (data[0].equals("DESTROY")) {
                Long idE = Long.parseLong(data[1]);
                int index = 0;
                for (Enemy enemy : enemies) {
                    if (enemy.getId() == idE) {
                        break;
                    }
                    index++;
                }
                if (index < enemies.size()) {
                    if (data.length == 3) {
                        String name = data[2];
                        for (Player player : players)
                            if (player.getName().equals(name)) {
                                player.setScore(player.getScore() + 10);
                                break;
                            }
                        //TODO
                        //add điều kiện
                        items.add(String.valueOf(enemies.get(index).getId()));
                        try {
                            OQueue.put(message);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    enemies.remove(index);
                }
            } else if (data[0].equals("DESTROYITEM")) {
                items.remove(data[1]);
            } else if (data[0].equals("GETSHOTED")) {
                String name = data[1];
                for (Player player : players)
                    if (player.getName().equals(name)) {
                        player.setHealth(player.getHealth() - 20);
                        break;
                    }
            } else if (data[0].equals("QUITROOM")) {
                removePlayer(data[1], 0);
            } else if (data[0].equals("QUITGAME")) {
                removePlayer(data[1], 1);
                synchronized (LoginThread.users) {
                    LoginThread.users.remove(data[1]);
                }
            }
        }
    }

    //tạo mục tiêu
    public void createEnermy(int plane) {
        Position position;
        if (plane >= 1 && plane <= 5) {
            position = new Position((float) (Math.random() * 101 - 50), 0, 240);
        } else position = new Position((float) (Math.random() * 81 - 50), 0, 0);
        Enemy enemy = new Enemy(numEnermy++, plane, position);
        synchronized (tmp) {
            tmp.add(enemy);
        }
    }

    //gửi dữ liệu đi
    public void sendData() throws JsonProcessingException, InterruptedException {
        if (ready == 0 && guards == 3) {
            OQueue.put("START");
            ready = 1;
            synchronized (LoginThread.isRunning) {
                LoginThread.isRunning.replace(id, true);
            }
        }
        String jsonString = "STATE|";
        //execute
        for (Player player : players) jsonString += mapper.writeValueAsString(player) + "|";
        if (players.size() < 3) for (int i = 0; i < 3 - players.size(); ++i) jsonString += "null|";
        for (Enemy enemy : tmp) {
            enemies.add(enemy);
            jsonString += mapper.writeValueAsString(enemy) + "|";
        }
        tmp.clear();
        OQueue.put(jsonString);
    }

    private boolean removePlayer(String name, int mode) {
        numPlayer = numPlayer > 0 ? --numPlayer : 0;
        guards = guards > 0 ? --guards : 0;
        //remove player
        int index = 0;
        synchronized (players) {
            for (Player player : players) {
                if (player.getName().equals(name)) break;
                index++;
            }
            if (index < 3) {
                players.remove(index);
            }
        }
        if (index < 3) {
            //remove socket
            receiveThread.removePlayer(index);
            synchronized (sockets) {
                if (mode == 0) {
                    LoginThread loginThread = new LoginThread(sockets.get(index));
                    loginThread.start();
                } else if (mode == 1) {
                    try {
                        sockets.get(index).close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                sockets.remove(index);
            }
        }

        synchronized (LoginThread.numPlayers) {
            Integer value = LoginThread.numPlayers.get(id) - 1;
            LoginThread.numPlayers.replace(id, value > 0 ? value : 0);
        }
        return true;
    }
}
