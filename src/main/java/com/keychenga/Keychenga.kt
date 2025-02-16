package com.keychenga

import java.awt.*
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

const val QUESTION_LENGTH_LIMIT = 100

fun main() {
    Keychenga().isVisible = true
}

class Keychenga : JFrame("Keychenga") {

    private val inputQueue: BlockingQueue<KeyEvent> = LinkedBlockingQueue()
    private val questionLabel: JLabel
    private val answerLabel: JLabel
    private val aimLabel: JLabel

    init {
        val font = Font(Font.MONOSPACED, Font.BOLD, 20)
        val panel = JPanel()
        panel.setLayout(BorderLayout())
        this.contentPane.add(panel)
        val chars = CharArray(QUESTION_LENGTH_LIMIT)
        Arrays.fill(chars, ' ')
        val text = String(chars)
        questionLabel = JLabel(text)
        questionLabel.setFont(font)
        panel.add(questionLabel, BorderLayout.NORTH)
        answerLabel = JLabel(text)
        answerLabel.setFont(font)
        panel.add(answerLabel, BorderLayout.CENTER)
        aimLabel = JLabel(text)
        aimLabel.setFont(font)
        aimLabel.setForeground(Color.BLUE)
        panel.add(aimLabel, BorderLayout.SOUTH)
        pack()
        val screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 2 - size.height / 2)
//        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 6 - size.height / 2)
//        setLocation(screenSize.width / 2 + screenSize.width / -size.width / 2, screenSize.height / 2 - size.height / 2)
        try {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventPostProcessor { event: KeyEvent ->
                if (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_TYPED) {
                    inputQueue.add(event)
                }
                false
            }

            val symbols = loadLines("/symbols.txt")
            val words = loadLines("/words.txt")
            val functions = loadLines("/functions.txt")
            Executors.newSingleThreadExecutor().execute {
                while (!Thread.currentThread().isInterrupted) {
                    question(symbols, words, functions)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun loadLines(resource: String): MutableList<String?> {
        val symbols: MutableList<String?> = ArrayList()
        val resourceStream = Objects.requireNonNull(this.javaClass.getResourceAsStream(resource))
        val linesReader = BufferedReader(InputStreamReader(resourceStream))
        var questionLine: String?
        while (linesReader.readLine().also { questionLine = it } != null) {
            symbols.add(questionLine)
        }
        return symbols
    }

    private fun question(
        symbols: List<String?>,
        words: MutableList<String?>,
        functions: MutableList<String?>
    ) {
        try {
            words.shuffle()
            val lines: MutableList<String?> = ArrayList()
            lines.addAll(functions)
            lines.addAll(symbols)
            lines.addAll(words.subList(0, symbols.size / 3))
            lines.shuffle()
            val expectedLines: MutableList<String?> = LinkedList()
            var questionBuilder = StringBuilder()
            println("-")
            for (i in lines.indices) {
                val line = lines[i]
                println("i=$i")
                if (questionBuilder.length + line!!.length >= QUESTION_LENGTH_LIMIT || i >= lines.size - 1) {
                    val penalties = answerAndGetPenalties(expectedLines, questionBuilder.toString())
                    questionBuilder = StringBuilder()
                    println("expectedLines=$expectedLines")
                    expectedLines.clear()
                    println("-")
                    penalties.shuffle()
                    questionLabel.setForeground(Color.RED)
                    var penaltyQuestionBuilder = StringBuilder()
                    val penaltyBatch: MutableList<String?> = LinkedList()
                    for (j in penalties.indices) {
                        println("j=$j")
                        println("penalties=$penalties")
                        println("penaltyQuestionBuilder=$penaltyQuestionBuilder")
                        val penalty = penalties[j]
                        var appended = false
                        if (penaltyQuestionBuilder.length + penalty!!.length >= QUESTION_LENGTH_LIMIT || j >= penalties.size - 1) {
                            if (j >= penalties.size - 1) {
                                appended = true
                                penaltyQuestionBuilder.append(penalty).append(" ")
                                penaltyBatch.add(penalty)
                            }
                            val morePenalties: MutableList<String?> = answerAndGetPenalties(
                                penaltyBatch,
                                penaltyQuestionBuilder.toString()
                            )
                            morePenalties.shuffle()
                            penalties.addAll(morePenalties)
                            penaltyQuestionBuilder = StringBuilder()
                            penaltyBatch.clear()
                        }
                        if (!appended) {
                            penaltyQuestionBuilder.append(penalty).append(" ")
                            penaltyBatch.add(penalty)
                        }
                    }
                    questionLabel.setForeground(Color.BLACK)
                    println("words=$lines")
                }
                questionBuilder.append(line).append(" ")
                expectedLines.add(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(InterruptedException::class, InvocationTargetException::class)
    private fun answerAndGetPenalties(expectedLines: List<String?>, question: String): MutableList<String?> {
        val answerBuilder = StringBuilder()
        val aimBuilder = StringBuilder()
        println("question=$question")
        println("questionLength=" + question.length)
        SwingUtilities.invokeAndWait {
            questionLabel.setText(question)
            answerLabel.setText("")
            aimLabel.setText("^")
        }
        println()
        val penalties: MutableList<String?> = LinkedList()
        for (expectedLine in expectedLines) {
            var expectedLineWithSpace = "$expectedLine "

            while (expectedLineWithSpace.isNotEmpty()) {
                val key = inputQueue.poll(5, TimeUnit.MINUTES)
                if (key == null) {
                    println("Timed out, quitting")
                    exitProcess(0)
                } else {
                    var answer = key.keyChar + ""
                    if (!key.keyChar.isDefined()
                        || key.isActionKey
                        || key.keyCode == KeyEvent.VK_ESCAPE // Somehow Escape is not an action key :O
                    ) {
                        if (key.id != KeyEvent.KEY_PRESSED) {
                            continue
                        }
                        answer = KeyEvent.getKeyText(key.keyCode)
                    } else {
                        if (key.id != KeyEvent.KEY_TYPED
                            || key.toString().contains("Unknown keyCode")
                        ) {
                            continue
                        }
                    }
                    println("k=[$key]")
                    println("a=[$answer]")
                    if (key.keyChar == '\n' || key.keyChar == ' ') {
                        answer = " "
                    }
                    if (answer.startsWith("Unknown")
                        || answer.startsWith("Undefined")
                    ) {
                        continue
                    }

                    println("e=[$expectedLineWithSpace]")

                    if (matches(expectedLineWithSpace, answer)) {
                        expectedLineWithSpace = expectedLineWithSpace.substring(answer.length)
                        aimBuilder.append(" ".repeat(answer.length))
                        answerBuilder.append(answer)
                        SwingUtilities.invokeLater {
                            answerLabel.setForeground(Color.BLACK)
                            answerLabel.setText(answerBuilder.toString())
                            aimLabel.setText("$aimBuilder^")
                        }
                    } else {
                        if (key.keyChar.isDefined() || key.isActionKey) {
                            if (!expectedLineWithSpace.startsWith(" ")
                                && answer != " "
                            ) {
                                if (!penalties.contains(expectedLine)) {
                                    penalties.add(expectedLine)
                                    if (expectedLine!!.length <= 2) {
                                        penalties.add(expectedLine)
                                    }
                                }
                            }
                            println("p=$penalties")
                            SwingUtilities.invokeLater {
                                answerLabel.setForeground(Color.RED)
                                answerLabel.setText(answerBuilder.toString() + answer)
                            }
                        }
                    }
                }
            }
        }
        return penalties
    }

    private fun matches(expectedLineWithSpace: String, answer: String): Boolean {
        // Hack: there should be a better solution for F1 passing for F10-F12
        if (expectedLineWithSpace.startsWith("F10") && answer != "F10") {
            return false
        }
        if (expectedLineWithSpace.startsWith("F11") && answer != "F11") {
            return false
        }
        if (expectedLineWithSpace.startsWith("F12") && answer != "F12") {
            return false
        }
        return expectedLineWithSpace.startsWith(answer)
    }


}
