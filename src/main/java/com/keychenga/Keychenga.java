package com.keychenga;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class Keychenga extends JFrame {

    public static final int QUESTION_LENGTH_LIMIT = 100;
    private final BlockingQueue<Character> inputQueue = new LinkedBlockingQueue<>();
    private final JLabel questionLabel;
    private final JLabel answerLabel;
    private final JLabel aimLabel;

    public static void main(String[] args) {
        new Keychenga().setVisible(true);
    }

    public Keychenga() {
        super("Keychenga");
        Font font = new Font(Font.MONOSPACED, Font.BOLD, 20);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        this.getContentPane().add(panel);
        char[] chars = new char[QUESTION_LENGTH_LIMIT];
        Arrays.fill(chars, ' ');
        String text = new String(chars);
        questionLabel = new JLabel(text);
        questionLabel.setFont(font);
        panel.add(questionLabel, BorderLayout.NORTH);
        answerLabel = new JLabel(text);
        answerLabel.setFont(font);
        panel.add(answerLabel, BorderLayout.CENTER);
        aimLabel = new JLabel(text);
        aimLabel.setFont(font);
        aimLabel.setForeground(Color.BLUE);
        panel.add(aimLabel, BorderLayout.SOUTH);
        pack();
        Rectangle screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setLocation(screenSize.width / 2 - getSize().width / 2, screenSize.height / 2 - getSize().height / 2);

        try {
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventPostProcessor(event -> {
                if (event.getID() == KeyEvent.KEY_TYPED) {
                    char keyChar = event.getKeyChar();
                    if (keyChar == '\n') {
                        keyChar = ' ';
                    }
                    inputQueue.clear();
                    inputQueue.add(keyChar);
                }
                return false;
            });

//            List<String> d = loadLines("/d.txt");
//            List<String> e = loadLines("/e.txt");
//            BufferedWriter writer = new BufferedWriter(new FileWriter("words.txt"));
//            for (int i = 0; i < d.size(); i++) {
//                String dd = d.get(i);
//                String ee = e.get(i);
//                writer.write(dd + "=" + ee + "\n");
//            }

            List<String> symbols = loadLines("/symbols.txt");
            List<String> words = loadLines("/words.txt");

            Executors.newSingleThreadExecutor().execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    question(symbols, words);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> loadLines(String resource) throws IOException {
        List<String> symbols = new ArrayList<>();
        BufferedReader linesReader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                this.getClass().getResourceAsStream(resource))));
        String questionLine;
        while (!((questionLine = linesReader.readLine()) == null)) {
            symbols.add(questionLine);
        }
        return symbols;
    }

    private void question(List<String> symbols, List<String> words) {
        try {
            List<String> lines = new ArrayList<>(symbols);
            Collections.shuffle(words);
            lines.addAll(words.subList(0, symbols.size() / 3));
            Collections.shuffle(lines);

            List<String> expectedLines = new LinkedList<>();
            StringBuilder questionBuilder = new StringBuilder();
            System.out.println("-");
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                System.out.println("i=" + i);
                if (questionBuilder.length() + line.length() >= QUESTION_LENGTH_LIMIT || i >= lines.size() - 1) {
                    List<String> penalties = answerAndGetPenalties(expectedLines, questionBuilder.toString());
                    questionBuilder = new StringBuilder();
                    System.out.println("expectedLines=" + expectedLines);
                    expectedLines.clear();
                    System.out.println("-");
                    Collections.shuffle(penalties);
                    questionLabel.setForeground(Color.RED);
                    StringBuilder penaltyQuestionBuilder = new StringBuilder();
                    List<String> penaltyBatch = new LinkedList<>();
                    for (int j = 0; j < penalties.size(); j++) {
                        System.out.println("j=" + j);
                        System.out.println("penalties=" + penalties);
                        System.out.println("penaltyQuestionBuilder=" + penaltyQuestionBuilder);
                        String penalty = penalties.get(j);
                        boolean appended = false;
                        if (penaltyQuestionBuilder.length() + penalty.length() >= QUESTION_LENGTH_LIMIT || j >= penalties.size() - 1) {
                            if (j >= penalties.size() - 1) {
                                appended = true;
                                penaltyQuestionBuilder.append(penalty).append(" ");
                                penaltyBatch.add(penalty);
                            }
                            List<String> morePenalties = answerAndGetPenalties(penaltyBatch, penaltyQuestionBuilder.toString());
                            Collections.shuffle(morePenalties);
                            penalties.addAll(morePenalties);
                            penaltyQuestionBuilder = new StringBuilder();
                            penaltyBatch.clear();
                        }
                        if (!appended) {
                            penaltyQuestionBuilder.append(penalty).append(" ");
                            penaltyBatch.add(penalty);
                        }
                    }

                    questionLabel.setForeground(Color.BLACK);
                    System.out.println("words=" + lines);
                }
                questionBuilder.append(line).append(" ");
                expectedLines.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> answerAndGetPenalties(List<String> expectedLines, String question) throws InterruptedException, InvocationTargetException {
        StringBuilder answerBuilder = new StringBuilder();
        StringBuilder aimBuilder = new StringBuilder();
        System.out.println("question=" + question);
        System.out.println("questionLength=" + question.length());
        SwingUtilities.invokeAndWait(() -> {
            questionLabel.setText(question);
            answerLabel.setText("");
            aimLabel.setText("^");
        });
        System.out.println();
        List<String> penalties = new LinkedList<>();
        for (String expectedLine : expectedLines) {
            String expectedLineWithSpace = expectedLine + " ";
            for (char expectedChar : expectedLineWithSpace.toCharArray()) {
                boolean correct = false;
                while (!correct) {
                    Character answerChar = inputQueue.poll(5, TimeUnit.MINUTES);
                    System.out.println("c=" + answerChar);
                    if (answerChar == null) {
                        System.out.println("Timed out, quitting");
                        System.exit(0);
                    } else {
                        if (expectedChar == answerChar) {
                            aimBuilder.append(" ");
                            correct = true;
                            answerBuilder.append(answerChar);
                            SwingUtilities.invokeLater(() -> {
                                answerLabel.setForeground(Color.BLACK);
                                answerLabel.setText(answerBuilder.toString());
                                aimLabel.setText(aimBuilder + "^");
                            });
                        } else {
                            if (expectedChar != ' ' && answerChar != ' ') {
                                if (!penalties.contains(expectedLine)) {
                                    penalties.add(expectedLine);
                                    if (expectedLine.length() <= 2) {
                                        penalties.add(expectedLine);
                                    }
                                }
                            }
                            System.out.println("p=" + penalties);
                            SwingUtilities.invokeLater(() -> {
                                answerLabel.setForeground(Color.RED);
                                answerLabel.setText(answerBuilder.toString() + answerChar);
                            });

                        }
                    }
                }
            }
        }
        return penalties;
    }
}
