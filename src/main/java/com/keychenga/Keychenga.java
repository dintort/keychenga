package com.keychenga;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class Keychenga extends JFrame {

    private final BlockingQueue<Character> inputQueue = new LinkedBlockingQueue<>();


    public static void main(String[] args) {
        new Keychenga().setVisible(true);
    }

    public Keychenga() {
        pack();
        setSize(600, 100);
        setLocation(1600, 800);


        Font font = new Font(Font.MONOSPACED, Font.BOLD, 20);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        this.getContentPane().add(panel);
        JLabel questionLabel = new JLabel();
        questionLabel.setFont(font);
        panel.add(questionLabel, BorderLayout.NORTH);
        JLabel answerLabel = new JLabel();
        answerLabel.setFont(font);
        panel.add(answerLabel, BorderLayout.CENTER);
        JLabel aimLabel = new JLabel();
        aimLabel.setFont(font);
        panel.add(aimLabel, BorderLayout.SOUTH);

        //        JTextArea text = new JTextArea();
//        panel.add(text);

        try {
            List<String> lines = new ArrayList<>();
            BufferedReader linesReader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                    this.getClass().getResourceAsStream("/symbols.txt"))));


//            Robot robot = new Robot();
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventPostProcessor(event -> {
//                if (event.isMetaDown()) {
//                    return false;
//                }
//                boolean pressed;
//                if (event.getID() == KeyEvent.KEY_PRESSED) {
//                    pressed = true;
//                } else if (event.getID() == KeyEvent.KEY_RELEASED) {
//                    pressed = false;
//                } else {
//                    return false;
//                }
                if (event.getID() == KeyEvent.KEY_TYPED) {
                    char keyChar = event.getKeyChar();
//                    System.out.println("E: keyChar");
                    inputQueue.clear();
                    inputQueue.add(keyChar);
//                    robot.keyPress(KeyEvent.VK_ENTER);
                }

//                if (!pressed && event.getKeyCode() != KeyEvent.VK_ENTER) {
//                }
                return false;
            });

            String questionLine;
            while (!((questionLine = linesReader.readLine()) == null)) {
                lines.add(questionLine);
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        List<String> words = new ArrayList<>();
                        for (String line : lines) {
                            String[] lineWords = line.split(" ");
                            words.addAll(Arrays.asList(lineWords));
                        }
                        Collections.shuffle(words);

                        List<String> punishments = new LinkedList<>();
                        StringBuilder questionBuilder = new StringBuilder();
                        System.out.println("-");
//                        System.out.print("Q: ");
                        for (int i = 0; i < words.size(); i++) {
                            String word = words.get(i);
                            questionBuilder.append(word).append(" ");
//                            System.out.print(word);
//                            System.out.print(" ");

                            System.out.println("i=" + i);
                            if (questionBuilder.length() >= 50 || i >= words.size() - 1) {
//                            if (questionBuilder.length() >= 10 || i >= words.size() - 1) {
                                StringBuilder answerBuilder = new StringBuilder();
                                StringBuilder aimBuilder = new StringBuilder();
                                String question = questionBuilder.toString();
                                System.out.println("Q: " + question);
                                System.out.println("question=" + question);
                                SwingUtilities.invokeAndWait(() -> {
                                    questionLabel.setText(question);
                                    answerLabel.setText("");
                                    aimLabel.setText("^");
                                });
                                questionBuilder = new StringBuilder();
                                System.out.println();
                                char[] expected = question.toCharArray();
                                for (char expectedChar : expected) {
                                    boolean correct = false;
                                    while (!correct) {
                                        Character readChar = inputQueue.poll(5, TimeUnit.MINUTES);
                                        System.out.println("c=" + readChar);
                                        if (readChar == null) {
                                            System.out.println("Timed out, quitting");
                                            System.exit(0);
                                        } else {
                                            if (expectedChar == readChar) {
                                                aimBuilder.append(" ");
                                                correct = true;
                                                answerBuilder.append(readChar);
                                                SwingUtilities.invokeLater(() -> {
                                                    answerLabel.setForeground(Color.BLACK);
                                                    answerLabel.setText(answerBuilder.toString());
                                                    aimLabel.setText(aimBuilder + "^");
                                                });
                                            } else {
                                                if (expectedChar != ' ') {
                                                    punishments.add(String.valueOf(expectedChar));
                                                }
                                                System.out.println("p=" + punishments);
                                                System.out.println("w=" + words);
                                                SwingUtilities.invokeLater(() -> {
                                                    answerLabel.setForeground(Color.RED);
                                                    answerLabel.setText(answerBuilder.toString() + readChar);
                                                });

                                            }
                                        }
                                    }
                                }
                                System.out.println("punishments=" + punishments);
                                Collections.shuffle(punishments);
                                words.addAll(i + 1, punishments);
                                System.out.println("words=" + words);
                                punishments.clear();
                                System.out.println("-");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
