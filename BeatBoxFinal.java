package BeatBox;
import javax.swing.*;
import javax.swing.event.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 * Проектируем GUI, который будет иметь 256 снятых флажков(JCheckBox),
 * 16 меток(JLabel) для названий инструментов и 4 кнопки
 *
 * Связываем ActionListener с каждой из 4х кнопок
 * Слушатели для флажков не нужны, так как схема звучания не меняется динамически:
 * выставляем флажки и ждем пока будет нажат Старт, затем пробегаем 256 флажков для 
 * получения их состояния и, основываясь на этом, создаем MIDI-дорожку
 *
 * Устанавливаем систему MIDI, получая доступ к синтезатору, создаем объект Sequencer
 * и дорожку для него# Будем использовать метод интерфейса Sequencer setLoopCount(),
 * который позволит определять желаемое количество циклов последовательности#
 * Также используется коэффициент темпа последовательности для настройки уровня темпа,
 * который будет сохраняться от одной итерации цикла к другой
 *
 * При нажатии кнопки Старт обработчик событий запускает метод build - TrackAndStart()
 * В нем пробегаем по 256 флажкам(по 1 ряду за раз, один инструмент на все 16 тактов),
 * чтобы получить их состояния, а затем, используя эту информацию, создаем MIDI-дорожку(
 * с помощью метода makeEvent()
 * Как дорожка будет построена - запустим секвенсор, который будет играть, так как он будет зациклен,
 * пока не будет нажат Стоп
 *
 * На панель добавлены два компонента: один отображает входящие сообщения(прокручиваемый список),
 * а другой представялет собой поле для ввода текста
 *
 * BeatBox соединятеся с сервером, работая с входящими и исходящими потоками
 * Для потоков создается класс для чтения сообщений, отправляемых сервером, которые состоят из двух объектов:
 * строковой записи и сериализованного ArrayList(хранит состояния всех флажков)
 */

public class BeatBoxFinal{
  /**
   * объявляем переменные класса
   */
  JFrame theFrame;
  JPanel mainPanel;

  /**
   * переменные для сообщений
   */
  JList incomingList;
  JTextField userMessage;
  int nextNum;
  Vector<String> listVector = new Vector<String>();
  String userName;
  ObjectOutputStream out;
  ObjectInputStream in;
  HashMap<String, boolean[]> otherSeqMap = new HashMap<String, boolean[]>();
  /**
   * в ArrayList будем хранить флажки
   */
  ArrayList<JCheckBox> checkboxList;
  Sequencer sequencer;
  Sequence sequence;
  Sequence mySequence = null;
  Track track;
  /**
   * названия инструментов в виде строкового массива,
   * которые предназначены для создания меток в GUI 
   */
  String[] instrumentNames = {
    "Bass Drum", "Closed Hi-Hat", "Open Hi-Hat", "Acoustic Snare",
    "Crash Cymbal", "Hand Clap", "High Tom", "Hi Bongo",
    "Maracas", "Whistle", "Low Conga", "Cowbell",
    "Vibraslap", "Low-mid Tom", "High Agogo", "Open Hi Conga"
  };
  /**
   * объявление массива с числами, обозначающими барабанные клавиши
   * канал барабана - что-то вроде фортепиано, только каждая клавиша на нем - отдельный барабан
   */
  int[] instruments = {
    35, 42, 46, 38, 
    49, 39, 50, 60,
    70, 72, 64, 56,
    58, 47, 67, 63
  };

  public static void main(String[] args){
    //используем аргумент коммандной строки в качестве имени, которое будет выводиться на экран
    //пример: %java BeatBoxFinal theFlash
    new BeatBoxFinal().startUp(args[0]);
  }

  public void startUp(String name){
    userName = name;
    //открываем соединение с сервером
    try{
      Socket sock = new Socket("127.0.0.1", 4242);
      out = new ObjectOutputStream(sock.getOutputStream());
      in = new ObjectInputStream(sock.getInputStream());

      Thread remote = new Thread(new RemoteReader());
      remote.start();
    }catch(Exception ex){
      System.out.println("Could`n connect to server - you play alone");
      ex.printStackTrace();
    }
    setUpMidi();
    buildGUI();
  }

  public void buildGUI(){
    theFrame = new JFrame("Cyber BeatBox");
    theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    BorderLayout layout = new BorderLayout();
    JPanel background = new JPanel(layout);
    /**
     * пустая граница позволяет создать поля между краями панели и местом размещения компонентов
     */
    background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    
    checkboxList = new ArrayList<JCheckBox>();
    Box buttonBox = new Box(BoxLayout.Y_AXIS);

    JButton start = new JButton("Start");
    start.addActionListener(new MyStartListener());
    buttonBox.add(start);

    JButton stop = new JButton("Stop");
    stop.addActionListener(new MyStopListener());
    buttonBox.add(stop);
    
    JButton upTempo = new JButton("Tempo Up");
    upTempo.addActionListener(new MyUpTempoListener());
    buttonBox.add(upTempo);

    JButton downTempo = new JButton("Tempo Down");
    downTempo.addActionListener(new MyDownTempoListener());
    buttonBox.add(downTempo);
    
    JButton sendIt = new JButton("sendIt");
    sendIt.addActionListener(new MySendListener());
    buttonBox.add(sendIt);
    
    userMessage = new JTextField();
    buttonBox.add(userMessage);

    //в JList отображаются входящие сообщения, которые можно выбирать из списка,
    //а не только просматривать
    //Благодаря этому можно загружать и воспроизводить прикремпляемые к сообщения музыкальные шаблоны
    incomingList = new JList();
    incomingList.addListSelectionListener(new MyListSelectionListener());
    incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane theList = new JScrollPane(incomingList);
    buttonBox.add(theList);
    incomingList.setListData(listVector);//нет начальных данных

    Box nameBox = new Box(BoxLayout.Y_AXIS);
    for(int i = 0; i < 16; i++){
    nameBox.add(new Label(instrumentNames[i]));
    }

    background.add(BorderLayout.EAST, buttonBox);
    background.add(BorderLayout.WEST, nameBox);

    theFrame.getContentPane().add(background);

    GridLayout grid = new GridLayout(16, 16);
    grid.setVgap(1);
    grid.setHgap(2);
    mainPanel = new JPanel(grid);
    background.add(BorderLayout.CENTER, mainPanel);

    /**
     * создаем флажки и присваиваем им значения false, добавляем их в ArrayList и на панель
     */
    for(int i = 0; i < 256; i++){
      JCheckBox c = new JCheckBox();
      c.setSelected(false);
      checkboxList.add(c);
      mainPanel.add(c);
    }

    theFrame.setBounds(50, 50, 300, 300);
    theFrame.pack();
    theFrame.setVisible(true);
  }

  public void setUpMidi(){
    try{
      sequencer = MidiSystem.getSequencer();
      sequencer.open();
      sequence = new Sequence(Sequence.PPQ, 4);
      track = sequence.createTrack();
      sequencer.setTempoInBPM(120);
    }catch(Exception ex){
      ex.printStackTrace();
    }
  }
  /**
   * метод преобразования состояния флажков в MIDI-события и добавления их на дорожку
   */
  public void buildTrackAndStart(){
    /*создаем массив на 16 элементов, чтобы хранить значания для каждого инструмента на 16 тактов */
    ArrayList<Integer> trackList = null;
    /*избавляемся от старой дорожки и создаем новую*/
    sequence.deleteTrack(track);
    track = sequence.createTrack();
    /* Создаем трек, проверяя состояния всех флажков и связывая их с инструментом(для которого создается MidiEvent)*/
    for(int i = 0; i < 16; i++){
      trackList = new ArrayList<Integer>();

      /*делаем для каждого такта текущего ряда*/
      for(int j = 0; j < 16; j++){
        JCheckBox jc = (JCheckBox) checkboxList.get(j + (16 * i));

        /*проверка установки флажка
         * если установлен, то помещаем значение клавиши в текущую ячейку массива, представляющую такт
         * если нет, то инструмент не должен играть в этом такте, поэтому ему присваивается 0
         */
        if(jc.isSelected()){
          int key = instruments[i];
          trackList.add(new Integer(key));
        }else{
          trackList.add(null);
        }
      }
      /* для этого инструмента и для всех 16 тактов создаем события и добавляем их на дорожку
       */
      makeTracks(trackList);
    }

    /* всегда дожны быть уверены, что событие на такте 16 существует
     * иначе BeatBox может не пройти все 16 тактов перед тем, как заново начнет последовательность*/
    track.add(makeEvent(192, 9, 1, 0, 15));
    try{
      sequencer.setSequence(sequence);
      /*позволяет задать кол-во повторений цикла или сделать цикл непрерывным*/
      sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
      sequencer.start();
      sequencer.setTempoInBPM(120);
    }catch(Exception ex){
    ex.printStackTrace();
    }
  }
  
  /**
   * метод создает события для одного инструмента за каждый проход цикла для всех 16 тактов
   * можно получить int[] для Bass Drum и каждый элемент массива будет содержать либо клавишу этого инструмента либо 0
   * если это 0, то инструмент не должен играть на текущем такте
   * иначе нужно создать событие и добавить его в дорожку
   */
  public void makeTracks(int[] list){
    for(int i = 0; i < 16; i++){
      int key = list[i];

      if(key != 0){
        /*создаем события вкл и выкл и добавляем их в дорожку*/
        track.add(makeEvent(144, 9, key, 100, i));
        track.add(makeEvent(128, 9, key, 100, i + 1));
      }
    }
  }
  public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick){
    MidiEvent event = null;
    try{
      ShortMessage a = new ShortMessage();
      a.setMessage(comd, chan, one, two);
      event = new MidiEvent(a, tick);
    }catch(Exception ex){
      ex.printStackTrace();
    }
    return event;
  }

  /**
   * ВНУТРЕННИЕ КЛАССЫ
   */

  public class MyStartListener implements ActionListener{
    public void actionPerformed(ActionEvent a){
      buildTrackAndStart();
    }
  }


  public class MyStopListener implements ActionListener{
    public void actionPerformed(ActionEvent a){
      sequencer.stop();
    }
  }

  public class MyUpTempoListener implements ActionListener{
    public void actionPerformed(ActionEvent a){
    float tempoFactor = sequencer.getTempoFactor();
    sequencer.setTempoFactor((float) (tempoFactor * 1.03));
    }
  }

  public class MyDownTempoListener implements ActionListener{
    public void actionPerformed(ActionEvent a){
    float tempoFactor = sequencer.getTempoFactor();
    sequencer.setTempoFactor((float) (tempoFactor * .97));
    }
  }
  
  /*
 * внутренний класс внутри кода BeatBox для кнопки serializeIt, сериализующей состояния флажков
 */
  public class MySendListener implements ActionListener{
    public void actionPerformed(ActionEvent a){
      //создаем булев массив для хранения состояния флажков
      boolean[] checkboxState = new boolean[256];
      //пробегаем checkboxList,содержащий состояние флажков, считываем состояния и добавляем полученные состояния в булев массив
      for(int i = 0; i < 256; i++){
        JCheckBox check = (JCheckBox) checkboxList.get(i);
        if(check.isSelected()){
          checkboxState[i] = true;
        }
      }
      //сериализуем булев массив и сообщение для отправки через исходящий поток сокета на сервер
      String messageToSend = null;
      try{
        out.writeObject(userName + nextNum++ + ": " + userMessage.getText());
        out.writeObject(checkboxState);
      }catch(Exception ex){
        System.out.println("Sorry, can`t send it to the server.");
        ex.printStackTrace();
      }
      userMessage.setText("");
    }
  }

/*
 * внутренний класс ListSelectionListener срабатывает, когда пользователь выбирает 
 * сообщения из списка
 * При этом мы сразу загружаем соответствующий музыкальный шаблон(хранящийся в переменной 
 * otherSeqMap типа HashMap) и указываем проиграть его
 */
  public class MyListSelectionListener implements ActionListener{
    public void actionPerformed(ActionEvent le){
      if(!le.getValueIsAdjusting()){
        String selected = (String) incomingList.getSelectedValue();
        if(selected != null){
          //Переходим к отображению и изменяем последовательность
          boolean[] selectedState = (boolean[]) otherSeqMap.get(selected);
          changeSequence(selectedState);
          sequencer.stop();
          buildTrackAndStart();
        }
      }
    }
  }

  public class RemoteReader implements Runnable{
    boolean[] checkboxState = null;
    String nameToShow = null;
    Object obj = null;
    public void run(){
    
    }
  }
}
