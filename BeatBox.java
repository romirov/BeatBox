package BeatBox;
import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

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
 */

public class BeatBox{
  /**
   * объявляем переменныу класса
   */
  JFrame theFrame;
  JPanel mainPanel;
  /**
   * в ArrayList будем хранить флажки
   */
  ArrayList<JCheckBox> checkboxList;
  Sequencer sequencer;
  Sequence sequence;
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
    new BeatBox().buildGUI();
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

    setUpMidi();
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
    int[] trackList = null;
    /*избавляемся от старой дорожки и создаем новую*/
    sequence.deleteTrack(track);
    track = sequence.createTrack();
    /*делаем это для каждого из 16 рядов(для Bass, Congo и т.д.)*/
    for(int i = 0; i < 16; i++){
      trackList = new int[16];

      /*задаем клавишу представляющую инструмент(Bass, Hi-Hat, ..).
       * Массив содержит MIDI-числа для каждого инструмента
       */
      int key = instruments[i];

      /*делаем для каждого такта текущего ряда*/
      for(int j = 0; j < 16; j++){
        JCheckBox jc = (JCheckBox) checkboxList.get(j + (16 * i));

        /*проверка установки флажка
         * если установлен, то помещаем значение клавиши в текущую ячейку массива, представляющую такт
         * если нет, то инструмент не должен играть в этом такте, поэтому ему присваивается 0
         */
        if(jc.isSelected()){
          trackList[j] = key;
        }else{
          trackList[j] = 0;
        }
      }
      /* для этого инструмента и для всех 16 тактов создаем события и добавляем их на дорожку
       */
      makeTracks(trackList);
      track.add(makeEvent(176, 1, 127, 0, 16));
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
}
