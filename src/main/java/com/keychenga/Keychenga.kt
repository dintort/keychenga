package com.keychenga

import java.awt.*
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.*
import kotlin.math.min

const val QUESTION_LENGTH_LIMIT = 75
private val MAC_TO_PC_KEYS: Map<String, String> = mapOf(
//        "⌘" to "Windows",
    "⌘" to "Alt",
//        "⌥" to "Alt",
    "⌥" to "Windows",
    "⌃" to "Ctrl",
    "⇧" to "Shift",
    "⎋" to "Escape",
)

fun main() {
    Keychenga().isVisible = true
}

class Keychenga : JFrame("Keychenga") {
    private val questionLabel: JLabel
    private val answerLabel: JLabel
    private val aimLabel: JLabel

    private val inputQueue: BlockingQueue<KeyEvent> = LinkedBlockingQueue()
    private val globalPenalties = object : LinkedHashSet<String?>(8192) {
        override fun add(element: String?): Boolean {
            if (size >= 8192 && !contains(element)) {
                remove(first())
            }
            return super.add(element)
        }
    }


    init {
        defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        val font = Font(Font.MONOSPACED, Font.BOLD, 20)

        val mainPanel = JPanel()
        mainPanel.setLayout(BorderLayout())
        this.contentPane.add(mainPanel)

//        val pictureLabel = JLabel(ImageIcon(javaClass.getResource("/touch-type.png")))
//        mainPanel.add(pictureLabel, BorderLayout.NORTH)

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
        setLocation(screenSize.width / 2 - size.width / 2 - size.width / 3, screenSize.height / 2 - size.height / 2)
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

            Executors.newSingleThreadExecutor().execute {
                while (!Thread.currentThread().isInterrupted) {
                    play()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun play() {
        try {
            val lines: MutableList<String?> = ArrayList()
            lines.addAll(loadLines("/functions.txt"))
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                // F10 triggers window menu on Windows :(
                lines.addAll(loadLines("/f9.txt"))
            } else {
                lines.addAll(loadLines("/f9f10.txt"))
            }
//            lines.addAll(loadLines("/functions-modifiers.txt"))
//            lines.addAll(loadLines("/symbols.txt"))
//            lines.addAll(loadLines("/words.txt").subList(0, 30))
            println("-")
            val subGlobalPenalties = globalPenalties.shuffled()
                .subList(0, min(256, globalPenalties.size))
            println("subGlobalPenalties=$subGlobalPenalties")
            repeat(5) { lines.addAll(subGlobalPenalties) }
            lines.shuffle()
            println("lines=$lines")
            question(lines)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("SameParameterValue")
    private fun loadLines(resource: String): List<String?> {
        val lines: MutableList<String?> = ArrayList()
        val resourceStream = Objects.requireNonNull(this.javaClass.getResourceAsStream(resource))
        val linesReader = BufferedReader(InputStreamReader(resourceStream))
        linesReader.forEachLine { lines.add(it) }
        lines.shuffle()
        return lines
    }

    private fun question(lines: MutableList<String?>) {
        val expectedLines: MutableList<String?> = LinkedList()
        val questionBuilder = StringBuilder(" ")
        for (line in lines) {
            if (questionBuilder.length + line!!.length >= QUESTION_LENGTH_LIMIT) {
                println("expectedLines=$expectedLines")
                answer(expectedLines, questionBuilder.toString())
                questionBuilder.clear().append(" ")
                expectedLines.clear()
                println("-")
            }
            questionBuilder.append(line).append(" ")
            expectedLines.add(line)
        }
        answer(expectedLines, questionBuilder.toString())
    }

    private fun answer(expectedLines: List<String?>, question: String) {
        val color = Color.BLACK
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
        val penalties: MutableList<String?> = ArrayList()
        for (expectedLine in expectedLines) {
            var expectedLineWithLeadingSpace = " $expectedLine"

            while (expectedLineWithLeadingSpace.isNotEmpty()) {
                val key = inputQueue.poll(5, TimeUnit.MINUTES)
                if (key == null) {
                    println("Waiting for input...")
                    continue
                }
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
                answer = MAC_TO_PC_KEYS[answer] ?: answer
                if (key.keyChar == '\n' || key.keyChar == ' ') {
                    answer = " "
                }
                if (answer.startsWith("Unknown")
                    || answer.startsWith("Undefined")
                ) {
                    continue
                }

                println("e=[$expectedLineWithLeadingSpace]")
                expectedLineWithLeadingSpace = checkAnswer(
                    expectedLineWithLeadingSpace,
                    answer,
                    aimBuilder,
                    answerBuilder,
                    key,
                    penalties,
                    expectedLine
                )
                println("p=$penalties")
            }
        }
        if (penalties.isNotEmpty()) {
            penalties.addAll(expectedLines)
            penalties.shuffle()
            question(penalties) // This is an unbound recursion so  theoretically it might cause a stack overflow if the user is persistent enough.
        }
    }

    private fun checkAnswer(
        expectedLineWithLeadingSpace: String,
        answer: String,
        aimBuilder: StringBuilder,
        answerBuilder: StringBuilder,
        key: KeyEvent,
        penalties: MutableList<String?>,
        expectedLine: String?
    ): String {
        var varExpectedLineWithLeadingSpace = expectedLineWithLeadingSpace
        var varAnswer = answer
        if (varExpectedLineWithLeadingSpace.startsWith(" ")
            && !varAnswer.equals(" ")
        ) {
            varAnswer = " $varAnswer"
        }
        if (matches(varExpectedLineWithLeadingSpace, varAnswer)) {
            varExpectedLineWithLeadingSpace = varExpectedLineWithLeadingSpace.substring(varAnswer.length)
            aimBuilder.append(" ".repeat(varAnswer.length))
            answerBuilder.append(varAnswer)
            SwingUtilities.invokeLater {
                answerLabel.setForeground(Color.BLACK)
                answerLabel.setText(answerBuilder.toString())
                aimLabel.setText("$aimBuilder^")
            }
        } else {
            if (key.keyChar.isDefined() || key.isActionKey) {
                if (varAnswer != " ") {
                    if (!penalties.contains(expectedLine)) {
                        repeat(3) {
                            penalties.add(expectedLine)
                        }
                    }
                    globalPenalties.add(expectedLine)
                }
                SwingUtilities.invokeLater {
                    answerLabel.setForeground(Color.RED)
                    answerLabel.setText(answerBuilder.toString() + varAnswer)
                }
            }
        }
        return varExpectedLineWithLeadingSpace
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
