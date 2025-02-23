package com.keychenga

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Arrays
import java.util.LinkedList
import java.util.Objects
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.system.exitProcess

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
    private val penalties = LimitedLinkedList<String>(1024)

    private fun play() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val lines: MutableList<String> = ArrayList()
                lines.addAll(loadLines("/f-keys.txt"))
                lines.addAll(loadLines("/numbers.txt"))
//                lines.addAll(loadLines("/symbols.txt"))
//                lines.addAll(loadLines("/danish-symbols.txt"))
//                lines.addAll(loadLines("/danish-words.txt").subList(0, 30))
//                lines.addAll(loadLines("/f-keys-modifiers.txt"))
                if (!System.getProperty("os.name").lowercase().contains("windows")) {
                    // F10 triggers window menu on Windows :(
                    lines.removeAll { it.contains("F10)") }
                }
                println("-")
                lines.shuffle()
                println("lines=$lines")
                question(lines)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(1)
            }
        }
    }

    private fun question(lines: List<String>) {
        val remainingLines = LinkedList(lines)
        val questionLines = ArrayList<String>()
        val questionBuilder = StringBuilder(" ")
        var line: String
        while (nextLine(remainingLines, lines, questionBuilder).also { line = it }.isNotEmpty()) {
            if (questionBuilder.length + line.length >= QUESTION_LENGTH_LIMIT) {
                answer(questionLines, questionBuilder.toString())
                questionBuilder.clear().append(" ")
                questionLines.clear()
                println("-")
            }
            questionBuilder.append(line).append(" ")
            questionLines.add(line)
        }
        // Fill in the rest of the remaining question line so it is not short.
        if (questionLines.isNotEmpty()) {
            val fillerLines = LinkedList(lines)
            while (nextLine(fillerLines, lines, questionBuilder).also { line = it }.isNotEmpty()
                && questionBuilder.length + line.length < QUESTION_LENGTH_LIMIT
            ) {
                questionBuilder.append(line).append(" ")
                questionLines.add(line)
            }
            answer(questionLines, questionBuilder.toString())
        }
    }

    private fun nextLine(
        remainingLines: MutableList<String>,
        originalLines: List<String>,
        questionBuilder: StringBuilder
    ): String {
        println("penalties=$penalties")
        println("remainingLines=$remainingLines")
        println("questionBuilder=$questionBuilder")
        return if (remainingLines.isEmpty()) {
            ""
        } else if (penalties.isNotEmpty() && Random.nextDouble() < 0.7) {
            nextNotClashing(penalties, originalLines, questionBuilder)
        } else {
            nextNotClashing(remainingLines, originalLines, questionBuilder)
        }
    }

    private fun nextNotClashing(
        lines: MutableList<String>,
        originalLines: List<String>,
        questionBuilder: StringBuilder
    ): String {
        var i = 0
        var candidateLine = lines.getOrEmpty(i)
        while (candidateLine.isNotEmpty() && clashes(candidateLine, questionBuilder)) {
            i++
            candidateLine = lines.getOrEmpty(i)
        }
        if (candidateLine.isNotEmpty()) {
            candidateLine = lines.removeAt(i)
        } else {
            candidateLine = originalLines.random()
            i = 0
            while (clashes(candidateLine, questionBuilder) && i++ < 1024) {
                candidateLine = originalLines.random()
            }
        }
        println("nextNotClashing=$candidateLine")
        return candidateLine
    }

    private fun clashes(
        candidateLine: String,
        questionBuilder: StringBuilder
    ): Boolean {
        val candidateLineSplit = candidateLine.trim().split(" ")
        if (candidateLineSplit.isEmpty()) {
            return false
        }

        val questionSplit = questionBuilder.trim().toString().split(" ")
        val prevWord = if (questionSplit.isNotEmpty()) questionSplit.last() else ""
        val prevPrevWord = if (questionSplit.size > 1) questionSplit[questionSplit.size - 2] else ""

        println("prevPrevWord=$prevPrevWord")
        println("prevWord=$prevWord")
        println("candidateLine=$candidateLine")

        if (candidateLineSplit.first() == prevWord) {
            return true
        }
        if (candidateLineSplit.first() == prevPrevWord) {
            return true
        }
        if (candidateLineSplit.size > 1 && candidateLineSplit[1] == prevWord) {
            return true
        }
        return false
    }

    private fun answer(
        questionLines: List<String>,
        question: String
    ) {
        val color = Color.BLACK
        questionLabel.setForeground(color)
        val answerBuilder = StringBuilder()
        val aimBuilder = StringBuilder()
        println("penalties=$penalties")
        println("question=[$question]")
        println("questionLines=[$questionLines]")
        println("questionLength=" + question.length)
        SwingUtilities.invokeAndWait {
            questionLabel.setText(question)
            answerLabel.setText("")
            aimLabel.setText(" ^")
        }
        println()
        for (questionLine in questionLines) {
            var questionLineWithLeadingSpace = " $questionLine"

            while (questionLineWithLeadingSpace.isNotEmpty()) {
                if (questionLineWithLeadingSpace.startsWith(" ")) {
                    SwingUtilities.invokeLater {
                        aimLabel.setText("$aimBuilder ^")
                    }
                }
                val key = inputQueue.poll(2, TimeUnit.SECONDS)
                if (key == null) {
                    if (answerBuilder.isNotEmpty() && !penalties.contains(questionLine)) {
                        repeat(8) { penalties.add(questionLine) }
                    }
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

                println("q=[$questionLineWithLeadingSpace]")
                questionLineWithLeadingSpace = checkAnswer(
                    questionLineWithLeadingSpace,
                    answer,
                    aimBuilder,
                    answerBuilder,
                    key,
                    questionLine
                )
                println("p=$penalties")
            }
        }
    }

    private fun checkAnswer(
        questionLineWithLeadingSpace: String,
        answer: String,
        aimBuilder: StringBuilder,
        answerBuilder: StringBuilder,
        key: KeyEvent,
        questionLine: String
    ): String {
        var varQuestionLineWithLeadingSpace = questionLineWithLeadingSpace
        var varAnswer = answer
        if (varQuestionLineWithLeadingSpace.startsWith(" ")
            && varAnswer != " "
        ) {
            varAnswer = " $varAnswer"
        }
        if (matches(varQuestionLineWithLeadingSpace, varAnswer)) {
            varQuestionLineWithLeadingSpace = varQuestionLineWithLeadingSpace.substring(varAnswer.length)
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
                    repeat(16) { penalties.add(questionLine) }
                }
                SwingUtilities.invokeLater {
                    answerLabel.setForeground(Color.RED)
                    answerLabel.setText(answerBuilder.toString() + varAnswer)
                }
            }
        }
        return varQuestionLineWithLeadingSpace
    }

    private fun matches(questionLineWithSpace: String, answer: String): Boolean {
        val trim = answer.trim()
        if (trim == "F1") { // To avoid F1 passing for F10-F12
            val split = questionLineWithSpace.trim().split(" ")
            if (split.isEmpty()) {
                return false
            }
            return split[0] == trim
        }
        return questionLineWithSpace.startsWith(answer)
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        val font = Font(Font.MONOSPACED, Font.BOLD, 20)
        val mainPanel = JPanel()
        mainPanel.setLayout(BorderLayout())
        contentPane.add(mainPanel)
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
        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 2 - size.height / 2)
//        setLocation(screenSize.width / 2 - size.width / 2 - size.width / 3, screenSize.height / 2 - size.height / 2)
//        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 6 - size.height / 2)
//        setLocation(screenSize.width / 2 + screenSize.width / -size.width / 2, screenSize.height / 2 - size.height / 2)

        initKeyboard()
        Executors.newSingleThreadExecutor().execute { play() }
    }

    private fun initKeyboard() {
        try {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventPostProcessor { event: KeyEvent ->
                if (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_TYPED) {
                    inputQueue.add(event)
                }
                false
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("SameParameterValue")
    private fun loadLines(resource: String): List<String> {
        val lines: MutableList<String> = ArrayList()
        val resourceStream = Objects.requireNonNull(this.javaClass.getResourceAsStream(resource))
        val linesReader = BufferedReader(InputStreamReader(resourceStream))
        linesReader.forEachLine { lines.add(it) }
        return lines
    }

}