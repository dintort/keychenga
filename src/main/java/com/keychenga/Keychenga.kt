package com.keychenga

import java.awt.*
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.*
import kotlin.system.exitProcess

const val QUESTION_LENGTH_LIMIT = 75

fun main() {
    Keychenga().isVisible = true
}

class Keychenga : JFrame("Keychenga") {

    private val inputQueue: BlockingQueue<KeyEvent> = LinkedBlockingQueue()
    private val questionLabel: JLabel
    private val answerLabel: JLabel
    private val aimLabel: JLabel
    private val macToPcKeys: Map<String, String> = mapOf(
//        "⌘" to "Windows",
        "⌘" to "Alt",
//        "⌥" to "Alt",
        "⌥" to "Windows",
        "⌃" to "Ctrl",
        "⇧" to "Shift",
        "⎋" to "Escape",
    )

    init {
        val font = Font(Font.MONOSPACED, Font.BOLD, 20)

        val mainPanel = JPanel()
        mainPanel.setLayout(BorderLayout())
        this.contentPane.add(mainPanel)

        val pictureLabel = JLabel(ImageIcon(javaClass.getResource("/touch-type.png")))
        mainPanel.add(pictureLabel, BorderLayout.NORTH)

        val typePanel = JPanel()
        typePanel.setLayout(BorderLayout())
        mainPanel.add(typePanel, BorderLayout.CENTER)

        val chars = CharArray(QUESTION_LENGTH_LIMIT)
        Arrays.fill(chars, ' ')
        val text = String(chars)
        questionLabel = JLabel(text)
        questionLabel.setFont(font)
        typePanel.add(questionLabel, BorderLayout.NORTH)

        answerLabel = JLabel(text)
        answerLabel.setFont(font)
        typePanel.add(answerLabel, BorderLayout.CENTER)

        aimLabel = JLabel(text)
        aimLabel.setFont(font)
        aimLabel.setForeground(Color.BLUE)
        typePanel.add(aimLabel, BorderLayout.SOUTH)

        pack()
        val screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
//        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 2 - size.height / 2)
        setLocation(screenSize.width / 2 - size.width / 2 - size.width / 4, screenSize.height / 2 - size.height / 2)
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
        symbols: MutableList<String?>,
        words: MutableList<String?>,
        functions: MutableList<String?>
    ) {
        try {
            words.shuffle()
            symbols.shuffle()
            functions.shuffle()
            val lines: MutableList<String?> = ArrayList()
            lines.addAll(functions)
//            lines.addAll(symbols)
//            lines.addAll(words.subList(0, symbols.size / 3))
            lines.shuffle()
            doQuestion(lines, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun doQuestion(lines: MutableList<String?>, isPenalty: Boolean) {
        println("-")
        println("lines=$lines")
        val expectedLines: MutableList<String?> = LinkedList()
        var questionBuilder = StringBuilder()
        for (line in lines) {
            if (questionBuilder.length + line!!.length >= QUESTION_LENGTH_LIMIT) {
                println("expectedLines=$expectedLines")
                answer(expectedLines, questionBuilder.toString(), isPenalty)
                questionBuilder = StringBuilder()
                expectedLines.clear()
                println("-")
            }
            questionBuilder.append(line).append(" ")
            expectedLines.add(line)
        }
        answer(expectedLines, questionBuilder.toString(), isPenalty)
    }

    private fun answer(expectedLines: List<String?>, question: String, isPenalty: Boolean) {
        val color = if (isPenalty) Color.RED else Color.BLACK
        questionLabel.setForeground(color)
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
                        || key.keyCode == KeyEvent.VK_ESCAPE //Somehow Escape is not an action key :O
                    ) {
                        if (key.id != KeyEvent.KEY_PRESSED) {
                            continue
                        }
                        answer = KeyEvent.getKeyText(key.keyCode)
                    } else {
                        if (key.id != KeyEvent.KEY_TYPED
                            || key.keyChar == '\u001B' //Escape
                        ) {
                            continue
                        }
                    }
                    println("k=[$key]")
                    println("a=[$answer]")
                    answer = macToPcKeys[answer] ?: answer
                    if (key.keyChar == '\n' || key.keyChar == ' ') {
                        answer = " "
                    }
                    if (answer.startsWith("Unknown")
                        || answer.startsWith("Undefined")
                    ) {
                        continue
                    }

                    println("e=[$expectedLineWithSpace]")
                    expectedLineWithSpace = checkAnswer(expectedLineWithSpace, answer, aimBuilder, answerBuilder, key,
                        penalties, expectedLine)
                    println("p=$penalties")
                }
            }
        }
        if (penalties.isNotEmpty()) {
            penalties.shuffle()
            doQuestion(penalties, true)
        }
    }

    private fun checkAnswer(
        expectedLineWithSpace: String,
        answer: String,
        aimBuilder: StringBuilder,
        answerBuilder: StringBuilder,
        key: KeyEvent,
        penalties: MutableList<String?>,
        expectedLine: String?
    ): String {
        var resultExpectedLineWithSpace = expectedLineWithSpace
        if (matches(resultExpectedLineWithSpace, answer)) {
            resultExpectedLineWithSpace = resultExpectedLineWithSpace.substring(answer.length)
            aimBuilder.append(" ".repeat(answer.length))
            answerBuilder.append(answer)
            SwingUtilities.invokeLater {
                answerLabel.setForeground(Color.BLACK)
                answerLabel.setText(answerBuilder.toString())
                aimLabel.setText("$aimBuilder^")
            }
        } else {
            if (key.keyChar.isDefined() || key.isActionKey) {
                if (!resultExpectedLineWithSpace.startsWith(" ")
                    && answer != " "
                ) {
                    if (!penalties.contains(expectedLine)) {
                        repeat(3) { penalties.add(expectedLine) }
                    }
                }
                SwingUtilities.invokeLater {
                    answerLabel.setForeground(Color.RED)
                    answerLabel.setText(answerBuilder.toString() + answer)
                }
            }
        }
        return resultExpectedLineWithSpace
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
