import java.util.ResourceBundle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import java.util.Random;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JScrollPane;
import javax.swing.JCheckBox;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.UIManager;
import javax.swing.UIManager.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.EventQueue;

class Position {
    public int x;
    public int y;
    Position(int x, int y){
        this.x = x;
        this.y = y;
    }
    public boolean equals(Position p){
        return this.x == p.x && this.y == p.y;
    }
    public String toString(){
        return "[" + x + "," + y + "]";
    }
        
}

class Item {
    public Position pos;
    public char name;

    Item(char name,Position pos){
        this.pos = pos;
        this.name = name;
    }
    
    // アイテムによって振る舞いを変えたいときは
    // powerClassとかTamaClassとか作るべきなのかもしれないが
    void effect(Player p){
        if (this.name == '力') {
            p.power +=1;
        } else if (this.name == '弾') {
            p.setBombLimit +=1;
        }
    }
}

class Bomb {
    public static final int EXPLODE_TIMER = 10;
    public Position pos;
    public int timer;
    public int power;
    public transient Player owner;
    
    Bomb(Player owner) {
        this.pos = owner.pos; // pos ha immutable
        this.power = owner.power;
        this.timer = EXPLODE_TIMER;
        this.owner = owner;
    }
    public String toString(){
        return "[" + pos.x + "," + pos.y + "]";
    }
}

class Block {
    public Position pos;
    transient Item item;
    Block(Position pos){
        this.pos = pos;
    }
    public boolean equal(Block b){
        return this.pos.equals(b.pos);
    }
}

class Player {
    public static final int DEFAULT_POWER = 2;
    public static final int DEFAULT_BOMB_LIMIT = 2;
    public String name;
    public Position pos;
    public int power;
    public int setBombLimit;
    public char ch;
    public boolean isAlive;
    public int setBombCount;
    public int totalSetBombCount;
    public int id;
    
    Player(String name) {
        this.name = name;
        this.power = DEFAULT_POWER;
        this.setBombLimit = DEFAULT_BOMB_LIMIT;
        this.ch = name.charAt(0);
        this.isAlive = true;
        this.setBombCount = 0;
    }

    public boolean canSetBomb(){
        return setBombCount < setBombLimit;
    }

    public ActionData action(String mapdata) {
        return new ActionData(this,"STAY",false);
    }

    public void setID(int id){
        this.id = id;
    }

    public void dispose() {}
}


class You extends Player {
    public transient String direction;
    public transient boolean[] keyStates;
    public transient boolean putBomb;
    
    You(String name) {
        super(name);
        direction = "";
        keyStates =  new boolean[5];//４方向（上=0,下=1,左=2,右=3）＋Ｚキー=4
        putBomb = false;
    }
    public ActionData action(String mapData){
        String nextMove = "STAY";
        if (direction == "UP" || keyStates[0]) {
            nextMove = "UP";
        } else if (direction == "DOWN" || keyStates[1]) {
            nextMove = "DOWN";
        } else if (direction == "LEFT" || keyStates[2]) {
            nextMove = "LEFT";
        } else if (direction == "RIGHT" || keyStates[3]) {
            nextMove = "RIGHT";
        }
        ActionData result =
            new ActionData(this,nextMove,putBomb);
        direction = "";
        putBomb = false;
        return result;
    }
}

class ExAI extends Player {
    transient BufferedWriter writer;
    transient BufferedReader reader;
    transient BufferedReader errorReader;
    transient Process proc;

    ExAI(String command){
        super("未接続");
        try {
            proc = Runtime.getRuntime().exec(command);
            writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(),"UTF-8"));
            reader = new BufferedReader(new InputStreamReader(proc.getInputStream(),"UTF-8"));
            errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream(),"UTF-8"));
            
            // 標準エラー出力はサーバの標準出力に垂れ流す
            new Thread(new Runnable(){
                    public void run(){
                        try {
                            for (String line = errorReader.readLine();
                                 line != null;
                                 line = errorReader.readLine()) {
                               System.out.println(line);
                            }
                        }catch(Exception e){
                        }
                    }
                }).start();
            this.name = reader.readLine();
            this.ch = name.charAt(0);
        } catch (Exception e) {
            System.out.println(e);
            this.ch = '落';
        }
    }
    
    public ActionData action(String mapData){
        try {
            writer.write(mapData+"\n");
            writer.flush();
            String raw = reader.readLine();
            System.out.println("RAW: " + this.name + ": "+raw);
            String[] data = raw.split(",",3);
            if (data.length == 3) {
                return new ActionData(this,data[0],Boolean.valueOf(data[1]), data[2]);
            } else {
                return new ActionData(this,data[0],Boolean.valueOf(data[1]));
            }                
        } catch(Exception e) {
            System.out.println(e);
            this.ch = '落';
            return new ActionData(this,"STAY",false);
        }
    }

    public void setID(int id){
        super.setID(id);
        try {
            writer.write(id+"\n");
            writer.flush();
        } catch(Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void dispose(){
        try {
            if (writer != null) {
                System.out.println(this.name + "との接続を切断しています。");
                writer.close();
                writer = null;
            }
            if (proc != null) {
                System.out.println(this.name + "の終了を待っています。");
                proc.waitFor();
                System.out.println(this.name + "が終了しました。");
                proc = null;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

class AIPlayer extends Player {
    transient Random rand;
    AIPlayer(String name){
        super(name);
        rand = new Random();
    }
    
    public ActionData action(String mapData){
        String[] moves = {"UP","DOWN","LEFT","RIGHT"};
        return new ActionData(this,moves[rand.nextInt(moves.length)],false);
    }
}

class ActionData {
    public Player p;
    public String dir;
    public boolean putBomb;
    public String message = "";
    
    ActionData (Player p,String dir,boolean putBomb){ 
        this.p = p;
        this.dir = dir;
        this.putBomb = putBomb;
    }

    ActionData (Player p,String dir,boolean putBomb, String message){ 
        this.p = p;
        this.dir = dir;
        this.putBomb = putBomb;
        this.message = message;
    }
    
    public String toString(){
        return p.name +": " + dir +"," + putBomb;
    }
}

class MapData {
    int turn;
    List<int[]> walls;
    List<int[]> blocks;
    List<Player> players;
    List<Bomb> bombs;
    List<Item> items;
    List<int[]> fires;

    public MapData(int turn,
                   List<Position> walls,
                   List<Block> blocks,
                   List<Player> players,
                   List<Bomb> bombs,
                   List<Item> items,
                   List<Position> fires) {
        this.turn = turn;
        this.walls = walls.stream()
            .map(p -> new int[]{p.x,p.y})
            .collect(Collectors.toList());
        this.blocks =
            blocks.stream()
            .map(b-> new int[]{b.pos.x,b.pos.y})
            .collect(Collectors.toList());
        this.players = players;
        this.bombs = bombs;
        this.items = items;
        this.fires =
            fires.stream()
            .map(f-> new int[]{f.x,f.y})
            .collect(Collectors.toList());
    }
}


public class BombmanServer {
    public static final String VERSION = "0.4.6";
    public static final int INIT_FIRE_POWER = 2;
    public static final int INIT_BOMB_LIMIT = 2;
    public static final int[][] FALLING_WALL =
    {{1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1}, {11, 1}, {12, 1}, {13, 1}, {13, 2}, {13, 3}, {13, 4}, {13, 5}, {13, 6}, {13, 7}, {13, 8}, {13, 9}, {13, 10}, {13, 11}, {13, 12}, {13, 13}, {12, 13}, {11, 13}, {10, 13}, {9, 13}, {8, 13}, {7, 13}, {6, 13}, {5, 13}, {4, 13}, {3, 13}, {2, 13}, {1, 13}, {1, 12}, {1, 11}, {1, 10}, {1, 9}, {1, 8}, {1, 7}, {1, 6}, {1, 5}, {1, 4}, {1, 3}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2}, {7, 2}, {8, 2}, {9, 2}, {10, 2}, {11, 2}, {12, 2}, {12, 3}, {12, 4}, {12, 5}, {12, 6}, {12, 7}, {12, 8}, {12, 9}, {12, 10}, {12, 11}, {12, 12}, {11, 12}, {10, 12}, {9, 12}, {8, 12}, {7, 12}, {6, 12}, {5, 12}, {4, 12}, {3, 12}, {2, 12}, {2, 11}, {2, 10}, {2, 9}, {2, 8}, {2, 7}, {2, 6}, {2, 5}, {2, 4}, {2, 3}, {3, 3}, {4, 3}, {5, 3}, {6, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3}, {11, 3}, {11, 4}, {11, 5}, {11, 6}, {11, 7}, {11, 8}, {11, 9}, {11, 10}, {11, 11}, {10, 11}, {9, 11}, {8, 11}, {7, 11}, {6, 11}, {5, 11}, {4, 11}, {3, 11}, {3, 10}, {3, 9}, {3, 8}, {3, 7}, {3, 6}, {3, 5}, {3, 4}, {4, 4}, {5, 4}, {6, 4}, {7, 4}, {8, 4}, {9, 4}, {10, 4}, {10, 5}, {10, 6}, {10, 7}, {10, 8}, {10, 9}, {10, 10}, {9, 10}, {8, 10}, {7, 10}, {6, 10}, {5, 10}, {4, 10}, {4, 9}, {4, 8}, {4, 7}, {4, 6}, {4, 5}};
    
    public static final String[] DEFAULT_MAP =
    {"■■■■■■■■■■■■■■■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■　■　■　■　■　■　■　■", 
     "■　　　　　　　　　　　　　■", 
     "■■■■■■■■■■■■■■■"};
    public static final int HEIGHT = DEFAULT_MAP.length;
    public static final int WIDTH = DEFAULT_MAP[0].length();
    public static final int ITEM_COUNT = 20;

    // どう書けばいい？
    public static final char[][] MAP_ARRAY =
        Arrays.stream(DEFAULT_MAP)
        .map(f -> f.toCharArray())
        .toArray(c -> new char[c][WIDTH]);

    // もっと綺麗に書けそうだが書き方がわからない
    public static final Position[] NEAR_INIT_POSITIONS = 
    {new Position(1,1), new Position(1,2), new Position(2,1),
     new Position(1,HEIGHT-2),new Position(1,HEIGHT-3),new Position(2,HEIGHT-2),//左下
     new Position(WIDTH-2,1),new Position(WIDTH-2,2),new Position(WIDTH-3,1),//右上
     new Position(WIDTH-2,HEIGHT-2),new Position(WIDTH-2,HEIGHT-3),new Position(WIDTH-3,HEIGHT-2)};

    public static final Position[] INIT_POSITIONS =
    {new Position(1,1),
     new Position(1,HEIGHT-2),
     new Position(WIDTH-2,1),
     new Position(WIDTH-2,HEIGHT-2)};
    
    static final int DEFAULT_SLEEP_TIME = 500;
    
    ArrayList<Bomb> bombs;
    ArrayList<Player> players;
    ArrayList<Item> items;
    ArrayList<Block> blocks;
    ArrayList<Position> walls;
    ArrayList<Position> fires;
    ArrayList<String> history;
    MapData mapData;
    int turn;
    int showTurn = turn;
    int sleepTime = DEFAULT_SLEEP_TIME;
    Gson gson = new Gson();
    JTextPane field;
    JTextArea textArea;
    JTextArea infoArea;
    JScrollPane scrollpane;
    JCheckBox stopCheckBox;
    boolean putBomb = false;
    You you;
    String direction = "";
    Timer timer;
    TimerTask task;

    void disposePlayers(){
        players.forEach(p -> p.dispose());
    }

    void newGame() {
        turn = 0;
        you = new You("あなた");
        bombs = new ArrayList<Bomb>();
        if (players != null) {
            disposePlayers();
        }
        players = new ArrayList<Player>();
        items = new ArrayList<Item>();
        blocks = new ArrayList<Block>();
        walls = new ArrayList<Position>();
        fires = new ArrayList<Position>();
        history = new ArrayList<String>();
        textArea.setText("");

        ResourceBundle rb = ResourceBundle.getBundle("bombman");
        ArrayList<String> tmp = new ArrayList<String>();
        tmp.add(rb.getString("ai0"));
        tmp.add(rb.getString("ai1"));
        tmp.add(rb.getString("ai2"));
        tmp.add(rb.getString("ai3"));
        tmp.removeIf(s-> s.trim().equals(""));
        tmp.forEach(s -> players.add(new ExAI(s)));

        if (players.size() < 4){
            players.add(you);
        }

        while (players.size() < 4) {
            players.add(new AIPlayer("敵"));
        }
        
        // プレイヤーを初期位置に移動
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).pos = INIT_POSITIONS[i];
            players.get(i).setID(i);
        }
        
        
        for (int x = 0; x < WIDTH; x++){
            for (int y = 0; y < WIDTH; y++){
                if (MAP_ARRAY[y][x] == '■') {
                    walls.add(new Position(x,y));
                }
            }
        }
        
        while (blocks.size() < 90) {
            Block newBlock = new Block(randomPosition());
            if(!(isNearInitPosition(newBlock.pos))
               && !(isWall(newBlock.pos))
               && !(isBlock(newBlock.pos))){
                blocks.add(newBlock);
            }
        }
        
        int i = 0;
        for (; i < ITEM_COUNT/2; i++) {
            Block b = blocks.get(i);
            b.item = new Item('力', b.pos);
        }
        for (; i < ITEM_COUNT; i++) {
            Block b = blocks.get(i);
            b.item = new Item('弾', b.pos);
        }
        mapData = new MapData(turn, walls, blocks, players, bombs, items, fires);
        history.add(gson.toJson(mapData));
        showMap(mapData);
        textArea.append("TURN 0: ゲームが開始されました\n");
		timer = new Timer();
        task = new UpdateTask();
		timer.schedule(task, 1000, DEFAULT_SLEEP_TIME);
    }
    
    BombmanServer(){
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {}
          
        JFrame frame = new JFrame("ボムマン "+VERSION);
        frame.setBounds(100, 100, 500, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent winEvt) {
                    task.cancel();
                    disposePlayers();
                }
            });

        field = new JTextPane();
        infoArea = new JTextArea();
        infoArea.setColumns(10);
        //infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 1));
        infoArea.setEditable(false);
        textArea = new JTextArea();
        textArea.setRows(5);
        scrollpane = new JScrollPane(textArea, 
                                     JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, 
                                     JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a,Font.MONOSPACED);
        StyleConstants.setFontSize(a,18);
        StyleConstants.setLineSpacing(a, -0.15f);
        field.setParagraphAttributes(a, true);
        field.setEditable(false);

        field.addKeyListener(new KeyListener(){
                @Override
                public void keyPressed(KeyEvent e){
                    int key = e.getKeyCode();
                    //System.out.println(key + "pressed");
                    switch(key){
                    case KeyEvent.VK_UP:
                        if(you.keyStates[0] == false){
                            you.direction = "UP";
                            you.keyStates[0] = true;
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if(you.keyStates[1] == false){
                            you.direction = "DOWN";
                            you.keyStates[1] = true;
                        }
                        break;
                    case KeyEvent.VK_LEFT:
                        if(you.keyStates[2] == false){
                            you.direction = "LEFT";
                            you.keyStates[2] = true;
                        }
                        break;
                    case KeyEvent.VK_RIGHT:
                        if(you.keyStates[3] == false){
                            you.direction = "RIGHT";
                            you.keyStates[3] = true;
                        }
                        break;
                    case KeyEvent.VK_SPACE:
                        you.putBomb = true;
                        break;
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    int key = e.getKeyCode();
                    switch(key){
                    case KeyEvent.VK_UP:
                        you.keyStates[0] = false;
                        break;
                    case KeyEvent.VK_DOWN:
                        you.keyStates[1] = false;
                        break;
                    case KeyEvent.VK_LEFT:
                        you.keyStates[2] = false;
                        break;
                    case KeyEvent.VK_RIGHT:
                        you.keyStates[3] = false;
                        break;
                    }
                }
                @Override
                public void keyTyped(KeyEvent e) {
                }
            });
        
        JButton prev2 = new JButton("<<");
        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        JButton next2 = new JButton(">>");
        JButton stop = new JButton("停止");
        JButton play = new JButton("再生");
        JButton fast = new JButton("早送り");
        JButton superFast = new JButton("超早送り");
        JButton retry = new JButton("もう一戦");
        stopCheckBox = new JCheckBox("勝敗が決まったら止める",true);
        prev.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                showTurn -= 1;
                if (showTurn < 0) {
                    showTurn = 0;
                }
                showMap(gson.fromJson(history.get(showTurn), MapData.class));
			}
            });
        prev2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                showTurn -= 10;
                if (showTurn < 0) {
                    showTurn = 0;
                }
                showMap(gson.fromJson(history.get(showTurn), MapData.class));
			}
            });
        next.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                showTurn += 1;
                if (showTurn > turn) {
                    new UpdateTask().run();
                } else {
                    showMap(gson.fromJson(history.get(showTurn), MapData.class));
                }
			}
            });
        next2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                showTurn += 10;
                if (showTurn > turn) {
                    new UpdateTask().run();
                } else {
                    showMap(gson.fromJson(history.get(showTurn), MapData.class));
                }
			}
            });

        stop.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
			}
            });
        play.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                task = new UpdateTask();
                timer.schedule(task, 0, DEFAULT_SLEEP_TIME);
			}});
        fast.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                task = new UpdateTask();
                timer.schedule(task, 0, 100);
			}});
        superFast.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                task = new UpdateTask();
                timer.schedule(task, 0, 1);
			}});
        retry.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
                task.cancel();
                newGame();
			}});
        
        JPanel buttons1 = new JPanel();
        JPanel buttons2 = new JPanel();
        JPanel buttons = new JPanel();
        buttons1.setLayout(new FlowLayout());
        buttons1.add(prev2);
		buttons1.add(prev);
		buttons1.add(next);
		buttons1.add(next2);
        buttons1.add(stopCheckBox);
		buttons2.add(stop);
		buttons2.add(play);
		buttons2.add(fast);
		buttons2.add(superFast);
        buttons2.add(retry);
        buttons.setLayout(new BorderLayout());
        buttons.add(buttons1, BorderLayout.NORTH);
        buttons.add(buttons2, BorderLayout.SOUTH);
        
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(buttons, BorderLayout.NORTH);
        frame.getContentPane().add(field, BorderLayout.CENTER);
        frame.getContentPane().add(scrollpane, BorderLayout.SOUTH);
        frame.getContentPane().add(infoArea, BorderLayout.EAST);
        frame.setVisible(true);

        EventQueue.invokeLater(new Runnable() {
                @Override public void run() {
                    field.requestFocusInWindow();
                }
            });
    }

    static ArrayList<int[]> findFireIndex(String str){
        ArrayList<int[]> result = new ArrayList<int[]>();
        int len = str.length();
        boolean found = false;
        int start = 0;
        for(int i = 0; i < len; i++){
            if (str.charAt(i) == '火') {
                if (found) {
                } else {
                    found = true;
                    start = i;
                }
            } else {
                if (found) {
                    result.add(new int[]{start,i-start});
                }
                found = false;
            }
        }
        return result;
    }
    
    void showMap(MapData mapData){
        String mapString = mapToString(mapData);

        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, Color.RED);
        ArrayList<int[]> firePos = findFireIndex(mapString);
        field.setText(mapString);
        StyledDocument doc = (StyledDocument) field.getDocument();
        firePos.forEach(p -> {
                doc.setCharacterAttributes(p[0], p[1], attr, true);
            });
        

        StringBuffer result = new StringBuffer();  
        players.forEach(p-> {
                result.append(p.name + "\n"
                              + "力:" + p.power +" 弾:" + p.setBombLimit
                              + " 計:" + p.totalSetBombCount
                              + "\n\n");
            });
        infoArea.setText(result.toString());
        
        System.out.print(mapString);
        System.out.println(gson.toJson(mapData)+"\n");
    }

    class UpdateTask extends TimerTask {
        public void run(){
            // キャラクタの行動
            String jsonMapData = gson.toJson(mapData);
            List<ActionData> actions =
                players.parallelStream()
                .map(p->p.action(jsonMapData))
                .collect(Collectors.toList());
            actions.forEach(action -> evalPutBombAction(action));
            actions.forEach(action -> evalMoveAction(action));
            
            turn += 1;
            showTurn = turn;

            // 壁が落ちてくる
            if (turn >= 360) {
                int i = turn - 360;
                if (i < FALLING_WALL.length) {
                    Position p = new Position(FALLING_WALL[i][0], FALLING_WALL[i][1]);
                    walls.add(p);
                    blocks.removeIf(b -> b.pos.equals(p));
                    items.removeIf(item -> item.pos.equals(p));
                    bombs.removeIf(b -> {
                            if (b.pos.equals(p)) {
                                b.owner.setBombCount--;
                                return true;
                            } else {
                                return false;
                            }
                        });
                }
            }

            // if (turn == 600) {
            //     int max = 0;
            //     Player winner = players.get(0);
            //     for(Player p : players){
            //         if (max < p.totalSetBombCount) {
            //             max = p.totalSetBombCount;
            //             winner = p;
            //         }
            //     }
            //     final Player winner2 = winner;
            //     players.forEach(p->{
            //             if (!(p == winner2)){
            //                 p.ch = '墓';
            //                 p.isAlive = false;
            //             }
            //         });
            // }
            
            for (Bomb b: bombs) {
                b.timer -=1;
            }

            // get item
            ArrayList<Item> usedItems = new ArrayList<Item>();
            for(Player p: players) {
                for(Item i: items){
                    if (p.pos.equals(i.pos)) {
                        i.effect(p);
                        usedItems.add(i);
                    }
                }
            }
            items.removeAll(usedItems);


            // bomb explosion
            fires = new ArrayList<Position>();
            ArrayList<Bomb> explodeBombs = new ArrayList<Bomb>();
            for (Bomb b: bombs) {
                if(b.timer <= 0) explodeBombs.add(b);
            }
            // chaining
            while (explodeBombs.size() != 0) {
                explodeBombs.forEach(b-> b.owner.setBombCount -= 1);
                fires.addAll(explodes(explodeBombs));
                bombs.removeAll(explodeBombs);
                explodeBombs = new ArrayList<Bomb>();
                for (Bomb b: bombs) {
                    for (Position p: fires){
                        if (b.pos.equals(p)) {
                            explodeBombs.add(b);
                            break;
                        }
                    }
                }
            }
            fires = removeDuplicates(fires,(a,b)->a.equals(b));
            
            // item burning
            items.removeIf(i -> {
                    boolean found = false;
                    for(Position fire: fires){
                        if(i.pos.equals(fire)) {
                            return true;
                        }
                    }
                    return false;
                });

            // block burning
            blocks.removeIf(b -> {
                    for(Position fire: fires){
                        if(b.pos.equals(fire)) {
                            if (b.item != null) {
                                items.add(b.item);
                            }
                            return true;
                        }
                    }
                    return false;
                });

            players.forEach(p -> {
                    for(Position fire: fires){
                        if(p.pos.equals(fire)) {
                            p.ch = '墓';
                            p.isAlive = false;
                        }
                    }
                    for(Position fire: walls){
                        if(p.pos.equals(fire)) {
                            p.ch = '墓';
                            p.isAlive = false;
                        }
                    }
                });
            
            mapData = new MapData(turn, walls, blocks, players, bombs, items, fires);
            history.add(gson.toJson(mapData));
            showMap(mapData);
            
            List<Player> living = players.stream().filter(p->p.isAlive)
                .collect(Collectors.toList());
            if(living.size() == 1){
                textArea.append("TURN "+turn+ " "
                                + living.get(0).name
                                + "の勝ちです！\n");
                if (stopCheckBox.isSelected()){
                    this.cancel();
                }
                // try{
                //     Thread.sleep(5000);
                //     newGame();
                // }catch(InterruptedException e){}
            } else if (living.size() == 0){
                textArea.append("引き分けです！\n");
                if (stopCheckBox.isSelected()){
                    this.cancel();
                }
                // try{
                //     Thread.sleep(5000);
                //     newGame();
                // }catch(InterruptedException e){}
            }
        }
    }

    public static void fill2(char[][] ary,char a) {
        for(int i = 0; i < ary.length; i++){
            for(int j = 0; j < ary[0].length; j++){
                ary[i][j] = a;
            }
        }
    }

    public static <T> void fill2(T[][] ary,T a) {
        for(int i = 0; i < ary.length; i++){
            for(int j = 0; j < ary[0].length; j++){
                ary[i][j] = a;
            }
        }
    }

    public static String mapToString(MapData map){
        char[][] mapArray = new char[HEIGHT][WIDTH];
        
        fill2(mapArray, '　');

        for (int[] b: map.blocks) {
            mapArray[b[1]][b[0]] = '□';
        }

        for (Bomb b: map.bombs) {
            mapArray[b.pos.y][b.pos.x] = '●';
        }
        
        for (Item i: map.items) {
            mapArray[i.pos.y][i.pos.x] = i.name;
        }

        for (int[] f: map.fires) {
            mapArray[f[1]][f[0]] = '火';
        }
        
        for (int[] p: map.walls) {
            mapArray[p[1]][p[0]] = '■';
        }

        for (Player p: map.players) {
            mapArray[p.pos.y][p.pos.x] = p.ch;
        }

        StringBuffer result = new StringBuffer();  
        for(int y = 0; y < HEIGHT; y++){
            for(int x = 0; x < WIDTH; x++){
                result.append(mapArray[y][x]);
            }
            result.append('\n');
        }
        return "Turn " + map.turn + "\n" + result.toString();
    }

    public void evalPutBombAction(ActionData action){
        try {
            System.out.println(action.toString());
            Player p = action.p;
            if (!action.message.equals("")) {
                textArea.append(action.p.name + "「" + action.message + "」\n");
                textArea.setCaretPosition(textArea.getText().length());
            }

            if (action.putBomb) {
                Bomb bomb = new Bomb(p);
                boolean existingBomb = false;
                for (Bomb b: bombs) {
                    if (b.pos.equals(bomb.pos)) {
                        existingBomb = true;
                        break;
                    }
                }
                if (p.isAlive
                    && existingBomb == false
                    && p.canSetBomb()) {
                    p.setBombCount += 1;
                    p.totalSetBombCount += 1;
                    bombs.add(bomb);
                }
            }
        } catch(Exception e){
            System.out.println(action.p.name + ": Invalid Action");
        }
    }
    
    public void evalMoveAction(ActionData action){
        try {
            Player p = action.p;

            Position nextPos = null;
            if (action.dir.equals("UP")) {
                nextPos = new Position(p.pos.x,p.pos.y-1);
            } else if (action.dir.equals("DOWN")) {
                nextPos = new Position(p.pos.x,p.pos.y+1);
            } else if (action.dir.equals("LEFT")) {
                nextPos = new Position(p.pos.x-1,p.pos.y);
            } else if (action.dir.equals("RIGHT")) {
                nextPos = new Position(p.pos.x+1,p.pos.y);
            }

            if (p.isAlive
                && nextPos != null
                && !(isWall(nextPos))
                && !(isBlock(nextPos))
                && !(isBomb(nextPos))) {
                p.pos = nextPos;
            }
        } catch(Exception e){
            System.out.println(action.p.name + ": Invalid Action");
        }
    }
    
    public static boolean isNearInitPosition(Position pos){
        for (Position p: NEAR_INIT_POSITIONS) {
            if (p.equals(pos)) return true;
        }
        return false;
    }

    public static Position randomPosition(){
        Random rnd = new Random();
        return new Position(rnd.nextInt(WIDTH),rnd.nextInt(HEIGHT));
    }

    public boolean isWall(Position pos) {
        for (Position w: walls) {
            if (w.equals(pos)) return true;
        }
        return false;
    }
    
    public boolean isBlock(Position pos) {
        for (Block b: blocks) {
            if (b.pos.equals(pos)) return true;
        }
        return false;
    }
    
    public boolean isItem(Position pos) {
        for (Item i: items) {
            if (i.pos.equals(pos)) return true;
        }
        return false;
    }
    
    public boolean isBomb(Position pos) {
        for (Bomb b: bombs) {
            if (b.pos.equals(pos)) return true;
        }
        return false;
    }

    public ArrayList<Position> explodes(ArrayList<Bomb> bombs) {
        ArrayList<Position> result = new ArrayList<Position>();
        for(Bomb b: bombs){
            result.addAll(explode(b));
        }
        return result;
    }

    ArrayList<Position> rec(String dir, int p, int power, Bomb bom){
        ArrayList<Position> result = new ArrayList<Position>();
        while (p <= power) {
            Position tmp = (dir == "up")? new Position(bom.pos.x,bom.pos.y-p):
                (dir == "down")? new Position(bom.pos.x,bom.pos.y+p):
                (dir == "left")? new Position(bom.pos.x-p,bom.pos.y):
                new Position(bom.pos.x+p,bom.pos.y);
            if (isWall(tmp)) {
                break;
            } else if (isBlock(tmp) || isItem(tmp)) {
                result.add(tmp);
                break;
            } else {
                result.add(tmp);
                p += 1;
            }
        }
        return result;
    }
    
    public ArrayList<Position> explode(Bomb bomb) {
        ArrayList<Position> result = new ArrayList<Position>();
        result.add(bomb.pos);
        result.addAll(rec("up",1,bomb.power,bomb));
        result.addAll(rec("down",1,bomb.power,bomb));
        result.addAll(rec("left",1,bomb.power,bomb));
        result.addAll(rec("right",1,bomb.power,bomb));
        return result;
    }
    
    public static <T> ArrayList<T> removeDuplicates(ArrayList<T> list,
                                                    BiFunction<T,T,Boolean> equalFn){
        ArrayList<T> result = new ArrayList<T>();
        for(T a : list) {
            boolean found = false;
            for(T b : result) {
                if (equalFn.apply(a,b)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.add(a);
            }
        }
        return result;
    }
    public static void main(String[] args){
        //System.out.println("Hello Java World!!");
        BombmanServer bs = new BombmanServer();
        bs.newGame();
    }
}


