package com.keychenga

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Arrays
import java.util.LinkedList
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import java.util.zip.ZipInputStream
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.random.Random

const val QUESTION_LENGTH_LIMIT = 75
const val PREFERENCES_KEY_DRILL_SELECTED_PREFIX = "drill_selected_"

private val MAC_TO_PC_KEYS: Map<String, String> = mapOf(
//    "⌘" to "Windows",
    "⌘" to "Ctrl",
//    "⌘" to "Alt",
    "⌥" to "Alt",
//    "⌥" to "Windows",
//    "⌃" to "Ctrl",
    "⌃" to "Windows",
    "⇧" to "Shift",
    "⎋" to "Escape",
)
private val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("windows")

fun main() {
    SwingUtilities.invokeLater {
        Keychenga().isVisible = true
    }
}

class Keychenga : JFrame("Keychenga") {
    private val preferences = Preferences.userNodeForPackage(Keychenga::class.java)

    private val questionLabel: JLabel
    private val answerLabel: JLabel
    private val aimLabel: JLabel

    private val inputQueue: BlockingQueue<KeyEvent> = LinkedBlockingQueue()
    private val penalties = LimitedLinkedList<String>(1024)

    private val drillCheckboxByFullPath = mutableMapOf<String, JCheckBox>()
    private var availableDrillFiles = listOf<String>()
    private var gameExecutor = Executors.newSingleThreadExecutor()

    private fun startGame() {
        if (!gameExecutor.isShutdown) {
            gameExecutor.shutdownNow()
            try {
                if (!gameExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("Game thread did not terminate in time.")
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        penalties.clear()
        gameExecutor = Executors.newSingleThreadExecutor()
        gameExecutor.execute { play() }
    }

    private fun play() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val selectedLines = loadSelectedDrillLines()
                if (selectedLines.isEmpty()) {
                    SwingUtilities.invokeLater {
                        questionLabel.text = " Please select at least one drill file."
                        answerLabel.text = ""
                        aimLabel.text = ""
                    }
                    return
                }
                println("-")
                selectedLines.shuffle()
                println("lines=$selectedLines")
                question(selectedLines)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interruption status
                println("Game interrupted.")
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) {
                    println("Game loop interrupted during exception handling.")
                    return
                }
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    questionLabel.text = "Error occurred. Restarting..."
                }
            }
        }
    }

    private fun loadSelectedDrillLines(): MutableList<String> {
        val selectedLines = mutableListOf<String>()
        drillCheckboxByFullPath.forEach { (drillName, checkBox) ->
            if (checkBox.isSelected) {
                println("Loading selected drill file: /$drillName")
                selectedLines.addAll(loadLines("/$drillName"))
            }
        }
        return selectedLines
    }

    private fun discoverDrillFiles(): List<String> {
        val discoveredFiles = mutableListOf<String>()
        val drillsPath = "/drills/"
        val folderUrl = this.javaClass.getResource(drillsPath)

        if (folderUrl == null) {
            System.err.println("Drills folder not found: $drillsPath")
            return discoveredFiles
        }

        // When running from a JAR, the resources are accessed differently
        if (folderUrl.protocol == "jar") {
            val jarPath = folderUrl.path.substring(5, folderUrl.path.indexOf("!"))
            try {
                ZipInputStream(FileInputStream(jarPath)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.startsWith(drillsPath.drop(1)) && entry.name.endsWith(".txt")) {
                            discoveredFiles.add(entry.name) // Store full path from JAR root
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("Error discovering drills from JAR, error=$e")
            }
        } else {
            try {
                val folder = java.io.File(folderUrl.toURI())
                folder.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
                    ?.forEach { file ->
                        discoveredFiles.add(drillsPath.drop(1) + file.name) // Store path relative to resources
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("Error discovering drills from file system, error=${e}")
            }
        }
        println("Discovered drills=$discoveredFiles")
        return discoveredFiles.sorted()
    }

    private fun question(lines: List<String>) {
        if (lines.isEmpty()) {
            SwingUtilities.invokeLater {
                questionLabel.text = "No lines to practice. Select drills and start."
                answerLabel.text = ""
                aimLabel.text = ""
            }
            return
        }

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
        // Fill in the rest of the question line so it is not short.
        if (questionLines.isNotEmpty()) {
            while (questionBuilder.length + line.length < QUESTION_LENGTH_LIMIT) {
                remainingLines.addAll(lines) // Use the initially passed lines for refilling
                remainingLines.shuffle()
                while (nextLine(remainingLines, lines, questionBuilder).also { line = it }.isNotEmpty()
                    && questionBuilder.length + line.length < QUESTION_LENGTH_LIMIT
                ) {
                    questionBuilder.append(line).append(" ")
                    questionLines.add(line)
                }
                if (lines.isEmpty() && remainingLines.isEmpty()) break // Avoid infinite loop if no lines at all
            }
            if (questionLines.isNotEmpty()) {
                answer(questionLines, questionBuilder.toString())
            }
        }
    }

    private fun nextLine(
        remainingLines: MutableList<String>,
        originalLines: List<String>,
        questionBuilder: StringBuilder,
    ): String {
        println("penalties=$penalties")
        println("remainingLines=$remainingLines")
        println("questionBuilder=$questionBuilder")
        return if (remainingLines.isEmpty()) {
            ""
        } else if (penalties.isNotEmpty()
            && Random.nextDouble() < 0.5
        ) {
            nextNotClashing(penalties, originalLines, questionBuilder)
        } else {
            nextNotClashing(remainingLines, originalLines, questionBuilder)
        }
    }

    private fun nextNotClashing(
        lines: MutableList<String>,
        originalLines: List<String>,
        questionBuilder: StringBuilder,
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
        questionBuilder: StringBuilder,
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
        if (questionBuilder.trim().endsWith(candidateLine.trim())) {
            return true
        }
        return false
    }

    private fun answer(
        questionLines: List<String>,
        question: String,
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
            questionLabel.text = question
            answerLabel.text = ""
            aimLabel.text = " ^"
        }

        println()
        for (questionLine in questionLines) {
            var questionLineWithLeadingSpace = " $questionLine"

            while (questionLineWithLeadingSpace.isNotEmpty()) {
                val key = inputQueue.poll(60, TimeUnit.SECONDS) ?: continue
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
        questionLine: String,
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
                answerLabel.foreground = Color.BLACK
                answerLabel.text = answerBuilder.toString()
                if (varQuestionLineWithLeadingSpace.isEmpty()
                    || varQuestionLineWithLeadingSpace.startsWith(" ")
                ) {
                    aimLabel.text = "$aimBuilder ^"
                } else {
                    aimLabel.text = "$aimBuilder^"
                }
            }
        } else {
            if (key.keyChar.isDefined() || key.isActionKey) {
                if (varAnswer != " ") {
                    val times = if (questionLine.length <= 3) 32 else 8
                    repeat(times) { penalties.add(questionLine) }
                }
                SwingUtilities.invokeLater {
                    answerLabel.foreground = Color.RED
                    answerLabel.text = answerBuilder.toString() + varAnswer
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
        val mainPanel = JPanel(BorderLayout())
        contentPane.add(mainPanel)

        val drillSelectionPanel = JPanel()
        drillSelectionPanel.layout = BoxLayout(drillSelectionPanel, BoxLayout.Y_AXIS)
        drillSelectionPanel.border = BorderFactory.createTitledBorder("Drills")

        availableDrillFiles = discoverDrillFiles()
        availableDrillFiles.forEach { filePath ->
            // Extract a display name (e.g., "f-keys" from "drills/f-keys.txt")
            val displayName = filePath.substring(
                filePath.lastIndexOf('/') + 1,
                filePath.lastIndexOf('.')
            )
            val preferenceKey = PREFERENCES_KEY_DRILL_SELECTED_PREFIX +
                    filePath.replace("/", "_")

            val isSelected = preferences.getBoolean(preferenceKey, false)
            val checkBox = JCheckBox(displayName, isSelected)
            checkBox.isFocusable = false

            checkBox.addItemListener { event ->
                val currentCheckBox = event.source as JCheckBox
                val selected = currentCheckBox.isSelected
                preferences.putBoolean(preferenceKey, selected)
                try {
                    preferences.flush()
                } catch (e: BackingStoreException) {
                    System.err.println("Error saving preferences, error=$e")
                    e.printStackTrace()
                }

                if (event.stateChange == ItemEvent.SELECTED || event.stateChange == ItemEvent.DESELECTED) {
                    startGame()
                }
            }
            drillCheckboxByFullPath[filePath] = checkBox
            drillSelectionPanel.add(checkBox)
        }

        val drillScrollPane = JScrollPane(drillSelectionPanel)
        drillScrollPane.border = null
        mainPanel.add(drillScrollPane, BorderLayout.EAST)

        // --- Center Panel (Image and Typing Area) ---
        val centerPanel = JPanel(BorderLayout())
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        val pictureLabel = JLabel(ImageIcon(javaClass.getResource("/touch-type.png")))
        centerPanel.add(pictureLabel, BorderLayout.NORTH)

        val typePanel = JPanel(BorderLayout())
        centerPanel.add(typePanel, BorderLayout.CENTER)

        val chars = CharArray(QUESTION_LENGTH_LIMIT + 1)
        Arrays.fill(chars, ' ')
        val text = String(chars)

        questionLabel = JLabel(text)
        questionLabel.font = font
        typePanel.add(questionLabel, BorderLayout.NORTH)

        answerLabel = JLabel(text)
        answerLabel.font = font
        typePanel.add(answerLabel, BorderLayout.CENTER)

        aimLabel = JLabel(text)
        aimLabel.font = font
        aimLabel.setForeground(Color.BLUE)
        typePanel.add(aimLabel, BorderLayout.SOUTH)

        pack()
        val screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        setLocation(screenSize.width / 2 - size.width / 2, screenSize.height / 2 - size.height / 2)

        initKeyboard()
        SwingUtilities.invokeLater {
            startGame()
        }
    }

    private fun initKeyboard() {
        try {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventPostProcessor { event: KeyEvent ->
                if (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_TYPED) {
                    if (IS_WINDOWS
                        && event.keyCode == KeyEvent.VK_F4
                        && event.modifiersEx and KeyEvent.ALT_DOWN_MASK != 0
                    ) {
                        return@addKeyEventPostProcessor false
                    }
                    event.consume()
                    // Offer to queue, don't block indefinitely if queue is full and game is stuck
                    if (!inputQueue.offer(event, 100, TimeUnit.MILLISECONDS)) {
                        println("Could not add key event to queue (timeout).")
                    }
                }
                true
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLines(resource: String): List<String> {
        val lines: MutableList<String> = ArrayList()
        try {
            // Check if the resource string already has a leading slash for getResourceAsStream
            val correctedResource = if (resource.startsWith("/")) resource else "/$resource"
            val resourceStream = this.javaClass.getResourceAsStream(correctedResource)
            if (resourceStream == null) {
                System.err.println("Cannot find resource: $correctedResource")
                return emptyList()
            }
            BufferedReader(InputStreamReader(resourceStream)).use { linesReader ->
                linesReader.forEachLine { lines.add(it) }
            }
        } catch (e: Exception) {
            System.err.println("Error loading lines from resource '$resource', error=$e}")
            e.printStackTrace()
        }
        return lines
    }
}