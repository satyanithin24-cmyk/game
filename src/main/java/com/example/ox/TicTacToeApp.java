package com.example.ox;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;

public final class TicTacToeApp extends JFrame {
    private static final int BOARD_SIZE = 9;
    private static final int DEFAULT_PORT = 5050;
    private static final int[] MOVE_ORDER = {4, 0, 2, 6, 8, 1, 3, 5, 7};
    private static final int[][] WINS = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    private final JButton[] cells = new JButton[BOARD_SIZE];
    private final char[] board = new char[BOARD_SIZE];
    private final JLabel statusLabel = new JLabel("Choose Offline or Online to start.", SwingConstants.CENTER);
    private final JLabel roleLabel = new JLabel("Mode: not connected");
    private final JLabel ipLabel = new JLabel("LAN IP: " + findLanAddress());
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField(String.valueOf(DEFAULT_PORT), 5);
    private final JButton offlineButton = new JButton("Offline");
    private final JButton localTwoPlayerButton = new JButton("2 Players");
    private final JButton onlineButton = new JButton("Online");
    private final JButton restartButton = new JButton("Restart");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JLabel resultImageLabel = new JLabel("", SwingConstants.CENTER);
    private final BufferedImage oImage = loadImage("Images/O.png");
    private final BufferedImage xImage = loadImage("Images/X.png");
    private final BufferedImage oWinsImage = loadImage("Images/O wins.png");
    private final BufferedImage xWinsImage = loadImage("Images/X wins.png");

    private volatile LanSession session;
    private char currentTurn = 'X';
    private char localMark = 'X';
    private char remoteMark = 'O';
    private boolean gameOver;
    private boolean onlineMode;
    private boolean computerMode;
    private boolean localTwoPlayerMode;
    private boolean computerThinking;
    private boolean connected;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TicTacToeApp app = new TicTacToeApp();
            app.setVisible(true);
        });
    }

    private TicTacToeApp() {
        super("O & X LAN Game");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(760, 650));
        setSize(860, 720);
        setLocationRelativeTo(null);
        setResizable(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeSession();
                dispose();
                System.exit(0);
            }
        });

        setJMenuBar(createMenuBar());
        setContentPane(createContent());
        resetBoard("Choose a game mode to start.");
        updateControls();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu gameMenu = new JMenu("Game");
        JMenuItem localItem = new JMenuItem("New Offline Game");
        JMenuItem localTwoPlayerItem = new JMenuItem("New Local 2-Player Game");
        JMenuItem hostItem = new JMenuItem("Host LAN Game");
        JMenuItem joinItem = new JMenuItem("Join LAN Game");
        JMenuItem disconnectItem = new JMenuItem("Disconnect");
        JMenuItem exitItem = new JMenuItem("Exit");

        localItem.addActionListener(event -> startOfflineGame());
        localTwoPlayerItem.addActionListener(event -> startLocalTwoPlayerGame());
        hostItem.addActionListener(event -> startHosting());
        joinItem.addActionListener(event -> joinGame());
        disconnectItem.addActionListener(event -> disconnectFromMenu());
        exitItem.addActionListener(event -> {
            closeSession();
            dispose();
            System.exit(0);
        });

        gameMenu.add(localItem);
        gameMenu.add(localTwoPlayerItem);
        gameMenu.add(hostItem);
        gameMenu.add(joinItem);
        gameMenu.add(new JSeparator());
        gameMenu.add(disconnectItem);
        gameMenu.add(new JSeparator());
        gameMenu.add(exitItem);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem lanHelp = new JMenuItem("LAN Connection Help");
        lanHelp.addActionListener(event -> showConnectionHelp());
        helpMenu.add(lanHelp);

        menuBar.add(gameMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        root.setBackground(new Color(244, 247, 251));

        root.add(createTopPanel(), BorderLayout.NORTH);
        root.add(createBoardPanel(), BorderLayout.CENTER);
        root.add(createBottomPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createTopPanel() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("O & X");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        title.setForeground(new Color(24, 39, 64));

        JLabel subtitle = new JLabel("Offline vs computer or same-network online play");
        subtitle.setForeground(new Color(84, 96, 115));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        top.add(titlePanel, gbc);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.insets = new Insets(3, 4, 3, 4);
        fgbc.anchor = GridBagConstraints.WEST;

        addLabeledField(fields, fgbc, 0, "Host IP", hostField);
        addLabeledField(fields, fgbc, 1, "Port", portField);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 8, 0));
        buttons.setOpaque(false);
        buttons.add(offlineButton);
        buttons.add(localTwoPlayerButton);
        buttons.add(onlineButton);

        offlineButton.addActionListener(event -> startOfflineGame());
        localTwoPlayerButton.addActionListener(event -> startLocalTwoPlayerGame());
        onlineButton.addActionListener(event -> showOnlineOptions());

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        top.add(fields, gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        top.add(buttons, gbc);

        return top;
    }

    private void addLabeledField(JPanel panel, GridBagConstraints gbc, int row, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(63, 74, 91));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 31));

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    private JPanel createBoardPanel() {
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 10, 10));
        boardPanel.setOpaque(false);
        boardPanel.setBorder(BorderFactory.createEmptyBorder(18, 80, 18, 80));

        Font cellFont = new Font(Font.SANS_SERIF, Font.BOLD, 64);
        for (int i = 0; i < cells.length; i++) {
            JButton button = new JButton();
            button.setFont(cellFont);
            button.setHorizontalAlignment(SwingConstants.CENTER);
            button.setVerticalAlignment(SwingConstants.CENTER);
            button.setFocusPainted(false);
            button.setBackground(Color.WHITE);
            button.setForeground(new Color(31, 88, 156));
            button.setBorder(BorderFactory.createLineBorder(new Color(205, 214, 226), 2));
            button.setPreferredSize(new Dimension(130, 130));
            final int index = i;
            button.addActionListener(event -> playCell(index));
            cells[i] = button;
            boardPanel.add(button);
        }
        boardPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                SwingUtilities.invokeLater(() -> refreshBoardIcons());
            }
        });
        return boardPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(12, 12));
        bottom.setOpaque(false);

        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        statusLabel.setForeground(new Color(34, 48, 76));
        resultImageLabel.setVisible(false);

        JPanel statusStack = new JPanel(new BorderLayout(8, 8));
        statusStack.setOpaque(false);
        statusStack.add(resultImageLabel, BorderLayout.NORTH);
        statusStack.add(statusLabel, BorderLayout.SOUTH);

        JPanel details = new JPanel(new GridLayout(2, 1, 4, 4));
        details.setOpaque(false);
        roleLabel.setForeground(new Color(76, 88, 108));
        ipLabel.setForeground(new Color(76, 88, 108));
        details.add(roleLabel);
        details.add(ipLabel);

        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        restartButton.addActionListener(event -> restartGame());
        disconnectButton.addActionListener(event -> disconnectFromMenu());
        actions.add(restartButton);
        actions.add(disconnectButton);

        bottom.add(statusStack, BorderLayout.NORTH);
        bottom.add(details, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.EAST);
        return bottom;
    }

    private void startOfflineGame() {
        Character chosenMark = askForOfflineMark();
        if (chosenMark == null) {
            return;
        }

        closeSession();
        onlineMode = false;
        computerMode = true;
        localTwoPlayerMode = false;
        computerThinking = false;
        connected = false;
        localMark = chosenMark;
        remoteMark = otherMark(chosenMark);
        resetBoard(localMark == 'X' ? "Offline game started. Your turn (X)." : "Offline game started. Computer starts as X.");
        roleLabel.setText("Mode: offline vs computer. You are " + localMark + ", computer is " + remoteMark);
        updateControls();
        if (currentTurn == remoteMark) {
            scheduleComputerMove();
        }
    }

    private void startLocalTwoPlayerGame() {
        closeSession();
        onlineMode = false;
        computerMode = false;
        localTwoPlayerMode = true;
        computerThinking = false;
        connected = false;
        localMark = 'X';
        remoteMark = 'O';
        resetBoard("Local 2-player game started. Player X goes first.");
        roleLabel.setText("Mode: local 2-player");
        updateControls();
    }

    private void showOnlineOptions() {
        Object[] options = {"Host", "Join"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Choose how to start the online LAN game.",
                "Online Game",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 0) {
            startHosting();
        } else if (choice == 1) {
            joinGame();
        }
    }

    private void startHosting() {
        int port = readPort();
        if (port <= 0) {
            return;
        }

        closeSession();
        onlineMode = true;
        computerMode = false;
        localTwoPlayerMode = false;
        computerThinking = false;
        connected = false;
        localMark = 'X';
        remoteMark = 'O';
        resetBoard("Waiting for another player to join...");
        roleLabel.setText("Mode: hosting as X on port " + port);
        updateControls();

        LanSession hostSession = LanSession.host(
                port,
                message -> SwingUtilities.invokeLater(() -> handleNetworkMessage(message)),
                message -> SwingUtilities.invokeLater(() -> setStatus(message)),
                error -> SwingUtilities.invokeLater(() -> handleDisconnect(error))
        );
        session = hostSession;
        hostSession.start();
    }

    private void joinGame() {
        int port = readPort();
        if (port <= 0) {
            return;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the host computer's LAN IP address.", "Host IP needed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        closeSession();
        onlineMode = true;
        computerMode = false;
        localTwoPlayerMode = false;
        computerThinking = false;
        connected = false;
        localMark = 'O';
        remoteMark = 'X';
        resetBoard("Connecting to " + host + ":" + port + "...");
        roleLabel.setText("Mode: joining as O");
        updateControls();

        LanSession clientSession = LanSession.join(
                host,
                port,
                message -> SwingUtilities.invokeLater(() -> handleNetworkMessage(message)),
                message -> SwingUtilities.invokeLater(() -> setStatus(message)),
                error -> SwingUtilities.invokeLater(() -> handleDisconnect(error))
        );
        session = clientSession;
        clientSession.start();
    }

    private int readPort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) {
                JOptionPane.showMessageDialog(this, "Use a port from 1024 to 65535.", "Invalid port", JOptionPane.WARNING_MESSAGE);
                return -1;
            }
            return port;
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Port must be a number.", "Invalid port", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
    }

    private void playCell(int index) {
        if (gameOver) {
            setStatus("The game is over. Press Restart to play again.");
            return;
        }
        if (board[index] != '\0') {
            setStatus("That square is already taken.");
            return;
        }
        if (onlineMode) {
            if (!connected) {
                setStatus("Wait until the second player is connected.");
                return;
            }
            if (currentTurn != localMark) {
                setStatus("Wait for the other player's move.");
                return;
            }
        } else if (computerMode) {
            if (computerThinking || currentTurn != localMark) {
                setStatus("Wait for the computer's move.");
                return;
            }
        } else if (!localTwoPlayerMode) {
            setStatus("Choose Offline or Online to start.");
            return;
        }

        char move = currentTurn;
        if (applyMove(index, move)) {
            LanSession active = session;
            if (onlineMode && active != null) {
                active.send("MOVE " + index);
            } else if (computerMode) {
                scheduleComputerMove();
            }
        }
    }

    private boolean applyMove(int index, char mark) {
        if (index < 0 || index >= board.length || board[index] != '\0' || gameOver || mark != currentTurn) {
            return false;
        }

        board[index] = mark;
        setCellMark(index, mark);

        int[] winningLine = winningLine(mark);
        if (winningLine != null) {
            gameOver = true;
            highlightWinningLine(winningLine);
            if (onlineMode) {
                setStatus(mark == localMark ? "You win!" : "Player " + mark + " wins.");
            } else if (computerMode) {
                setStatus(mark == localMark ? "You win!" : "Computer wins.");
            } else {
                setStatus("Player " + mark + " wins.");
            }
            updateControls();
            displayWinnerImage(mark);
            return true;
        }

        if (isBoardFull()) {
            gameOver = true;
            setStatus("Draw. Nobody wins this round.");
            updateControls();
            return true;
        }

        currentTurn = otherMark(currentTurn);
        updateTurnStatus();
        updateControls();
        return true;
    }

    private int[] winningLine(char mark) {
        for (int[] line : WINS) {
            if (board[line[0]] == mark && board[line[1]] == mark && board[line[2]] == mark) {
                return line;
            }
        }
        return null;
    }

    private boolean isBoardFull() {
        for (char cell : board) {
            if (cell == '\0') {
                return false;
            }
        }
        return true;
    }

    private void highlightWinningLine(int[] line) {
        for (int index : line) {
            cells[index].setBackground(new Color(218, 241, 225));
            cells[index].setBorder(BorderFactory.createLineBorder(new Color(48, 142, 89), 3));
        }
    }

    private void restartGame() {
        if (onlineMode && !connected) {
            setStatus("Connect another player before restarting an online game.");
            return;
        }
        if (onlineMode && session != null) {
            session.send("RESET");
        }
        if (computerMode) {
            computerThinking = false;
            resetBoard(localMark == 'X' ? "Game restarted. Your turn (X)." : "Game restarted. Computer starts as X.");
            if (currentTurn == remoteMark) {
                scheduleComputerMove();
            }
            return;
        }
        resetBoard(onlineMode
                ? "Game restarted. X goes first."
                : localTwoPlayerMode ? "Game restarted. Player X goes first." : "Choose Offline or Online to start.");
    }

    private void resetBoard(String status) {
        Arrays.fill(board, '\0');
        currentTurn = 'X';
        gameOver = false;
        computerThinking = false;
        for (JButton cell : cells) {
            if (cell != null) {
                cell.setText("");
                cell.setIcon(null);
                cell.setDisabledIcon(null);
                cell.setBackground(Color.WHITE);
                cell.setBorder(BorderFactory.createLineBorder(new Color(205, 214, 226), 2));
            }
        }
        resultImageLabel.setIcon(null);
        resultImageLabel.setText("");
        resultImageLabel.setVisible(false);
        setStatus(status);
        updateControls();
    }

    private void setCellMark(int index, char mark) {
        JButton cell = cells[index];
        BufferedImage image = mark == 'X' ? xImage : oImage;
        int maxWidth = Math.max(64, cell.getWidth() - 34);
        int maxHeight = Math.max(64, cell.getHeight() - 34);
        ImageIcon icon = scaledIcon(image, maxWidth, maxHeight);

        if (icon == null) {
            cell.setIcon(null);
            cell.setDisabledIcon(null);
            cell.setText(String.valueOf(mark));
            cell.setForeground(mark == 'X' ? new Color(31, 88, 156) : new Color(190, 76, 65));
            return;
        }

        cell.setText("");
        cell.setIcon(icon);
        cell.setDisabledIcon(icon);
    }

    private void refreshBoardIcons() {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 'X' || board[i] == 'O') {
                setCellMark(i, board[i]);
            }
        }
    }

    private void displayWinnerImage(char mark) {
        BufferedImage image = mark == 'X' ? xWinsImage : oWinsImage;
        ImageIcon bannerIcon = scaledIcon(image, 300, 132);
        resultImageLabel.setIcon(bannerIcon);
        resultImageLabel.setText(bannerIcon == null ? "Player " + mark + " wins!" : "");
        resultImageLabel.setVisible(true);
        resultImageLabel.revalidate();
        resultImageLabel.repaint();

        ImageIcon dialogIcon = scaledIcon(image, 380, 380);
        JLabel message = new JLabel(dialogIcon, SwingConstants.CENTER);
        if (dialogIcon == null) {
            message.setText("Player " + mark + " wins!");
            message.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        }
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                message,
                "Game Over",
                JOptionPane.PLAIN_MESSAGE
        ));
    }

    private static BufferedImage loadImage(String assetPath) {
        String resourcePath = "/" + assetPath.replace('\\', '/');
        try (InputStream stream = TicTacToeApp.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) {
                    return image;
                }
            }
        } catch (IOException ignored) {
            // Fall back to loading from the project folder while developing.
        }

        Path filePath = Path.of(assetPath);
        if (Files.exists(filePath)) {
            try {
                return ImageIO.read(filePath.toFile());
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }

    private static ImageIcon scaledIcon(BufferedImage image, int maxWidth, int maxHeight) {
        if (image == null || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }

        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private Character askForOfflineMark() {
        Object[] options = {"X", "O"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "Choose your side for offline play.",
                "Offline Game",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == JOptionPane.CLOSED_OPTION) {
            return null;
        }
        return choice == 1 ? 'O' : 'X';
    }

    private void scheduleComputerMove() {
        if (onlineMode || !computerMode || gameOver || currentTurn != remoteMark) {
            return;
        }

        computerThinking = true;
        setStatus("Computer (" + remoteMark + ") is thinking...");
        updateControls();

        Timer timer = new Timer(350, event -> {
            ((Timer) event.getSource()).stop();
            if (onlineMode || !computerMode || gameOver || currentTurn != remoteMark) {
                computerThinking = false;
                updateControls();
                return;
            }

            int move = findBestComputerMove();
            computerThinking = false;
            if (move >= 0) {
                applyMove(move, remoteMark);
            } else {
                updateControls();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private int findBestComputerMove() {
        int bestMove = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int index : MOVE_ORDER) {
            if (board[index] == '\0') {
                board[index] = remoteMark;
                int score = minimax(false, 0);
                board[index] = '\0';
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = index;
                }
            }
        }
        return bestMove;
    }

    private int minimax(boolean computerTurn, int depth) {
        if (winningLine(remoteMark) != null) {
            return 10 - depth;
        }
        if (winningLine(localMark) != null) {
            return depth - 10;
        }
        if (isBoardFull()) {
            return 0;
        }

        int bestScore = computerTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        char mark = computerTurn ? remoteMark : localMark;
        for (int index : MOVE_ORDER) {
            if (board[index] == '\0') {
                board[index] = mark;
                int score = minimax(!computerTurn, depth + 1);
                board[index] = '\0';
                bestScore = computerTurn ? Math.max(bestScore, score) : Math.min(bestScore, score);
            }
        }
        return bestScore;
    }

    private void updateTurnStatus() {
        if (computerMode) {
            setStatus(currentTurn == localMark ? "Your turn (" + localMark + ")." : "Computer's turn (" + remoteMark + ").");
            return;
        }
        if (localTwoPlayerMode) {
            setStatus("Player " + currentTurn + "'s turn.");
            return;
        }
        if (!connected) {
            setStatus(onlineMode ? "Waiting for another player to join..." : "Choose Offline or Online to start.");
            return;
        }
        setStatus(currentTurn == localMark ? "Your turn (" + localMark + ")." : "Player " + currentTurn + "'s turn.");
    }

    private void handleNetworkMessage(String message) {
        if (message.equals("CONNECTED")) {
            connected = true;
            resetBoard(localMark == 'X' ? "Player O connected. Your turn (X)." : "Connected. Waiting for player X.");
            roleLabel.setText("Mode: online as " + localMark + " against " + remoteMark);
            return;
        }

        if (message.startsWith("ROLE ")) {
            char assigned = message.charAt(message.length() - 1);
            if (assigned == 'X' || assigned == 'O') {
                localMark = assigned;
                remoteMark = otherMark(assigned);
                connected = true;
                resetBoard(localMark == 'X' ? "Connected. Your turn (X)." : "Connected. Waiting for player X.");
                roleLabel.setText("Mode: online as " + localMark + " against " + remoteMark);
            }
            return;
        }

        if (message.startsWith("MOVE ")) {
            try {
                int index = Integer.parseInt(message.substring(5).trim());
                boolean accepted = applyMove(index, remoteMark);
                if (!accepted) {
                    setStatus("Received an invalid move. Disconnecting.");
                    closeSession();
                    connected = false;
                    updateControls();
                }
            } catch (NumberFormatException exception) {
                setStatus("Received a broken move. Disconnecting.");
                closeSession();
                connected = false;
                updateControls();
            }
            return;
        }

        if (message.equals("RESET")) {
            resetBoard("The other player restarted the game. X goes first.");
            return;
        }

        if (message.equals("QUIT")) {
            handleDisconnect("The other player left the game.");
        }
    }

    private void disconnectFromMenu() {
        closeSession();
        onlineMode = false;
        computerMode = false;
        localTwoPlayerMode = false;
        computerThinking = false;
        connected = false;
        roleLabel.setText("Mode: disconnected");
        resetBoard("Disconnected. Choose Offline or Online to play.");
    }

    private void handleDisconnect(String reason) {
        closeSession();
        connected = false;
        onlineMode = false;
        computerMode = false;
        localTwoPlayerMode = false;
        computerThinking = false;
        roleLabel.setText("Mode: disconnected");
        setStatus(reason == null || reason.isBlank() ? "Disconnected." : reason);
        updateControls();
    }

    private void closeSession() {
        LanSession active = session;
        session = null;
        if (active != null) {
            active.close();
        }
    }

    private void updateControls() {
        boolean canMove = false;
        if (onlineMode) {
            canMove = connected && currentTurn == localMark;
        } else if (computerMode) {
            canMove = currentTurn == localMark && !computerThinking;
        } else if (localTwoPlayerMode) {
            canMove = true;
        }

        for (int i = 0; i < cells.length; i++) {
            JButton cell = cells[i];
            if (cell != null) {
                cell.setEnabled(canMove && !gameOver && board[i] == '\0');
            }
        }
        hostField.setEnabled(!onlineMode || !connected);
        portField.setEnabled(!onlineMode || !connected);
        offlineButton.setEnabled(true);
        onlineButton.setEnabled(!onlineMode || !connected);
        restartButton.setEnabled(computerMode || localTwoPlayerMode || (onlineMode && connected));
        disconnectButton.setEnabled(onlineMode || connected);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void showConnectionHelp() {
        JOptionPane.showMessageDialog(
                this,
                "To play over the same network:\n\n"
                        + "1. Player X clicks Host.\n"
                        + "2. Player O enters Player X's LAN IP and clicks Join.\n"
                        + "3. Keep both computers on the same Wi-Fi or LAN.\n"
                        + "4. If Windows Firewall asks, allow Java on private networks.\n\n"
                        + "For Offline, choose X or O and the computer plays the other side.\n\n"
                        + "This window uses normal Windows controls for minimize, maximize, and close.",
                "LAN Connection Help",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static char otherMark(char mark) {
        return mark == 'X' ? 'O' : 'X';
    }

    private static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {
            return "unavailable";
        }
        return "unavailable";
    }

    @FunctionalInterface
    private interface MessageHandler {
        void accept(String message);
    }

    private static final class LanSession implements Closeable {
        private final boolean host;
        private final String hostAddress;
        private final int port;
        private final MessageHandler inboundHandler;
        private final MessageHandler statusHandler;
        private final MessageHandler disconnectHandler;
        private volatile boolean closed;
        private volatile Socket socket;
        private volatile ServerSocket serverSocket;
        private volatile PrintWriter writer;
        private Thread worker;

        private LanSession(
                boolean host,
                String hostAddress,
                int port,
                MessageHandler inboundHandler,
                MessageHandler statusHandler,
                MessageHandler disconnectHandler
        ) {
            this.host = host;
            this.hostAddress = hostAddress;
            this.port = port;
            this.inboundHandler = inboundHandler;
            this.statusHandler = statusHandler;
            this.disconnectHandler = disconnectHandler;
        }

        static LanSession host(
                int port,
                MessageHandler inboundHandler,
                MessageHandler statusHandler,
                MessageHandler disconnectHandler
        ) {
            return new LanSession(true, null, port, inboundHandler, statusHandler, disconnectHandler);
        }

        static LanSession join(
                String hostAddress,
                int port,
                MessageHandler inboundHandler,
                MessageHandler statusHandler,
                MessageHandler disconnectHandler
        ) {
            return new LanSession(false, hostAddress, port, inboundHandler, statusHandler, disconnectHandler);
        }

        void start() {
            worker = new Thread(host ? this::runHost : this::runClient, host ? "ox-host-session" : "ox-client-session");
            worker.setDaemon(true);
            worker.start();
        }

        void send(String message) {
            PrintWriter activeWriter = writer;
            if (activeWriter != null && !closed) {
                activeWriter.println(message);
            }
        }

        @Override
        public void close() {
            send("QUIT");
            closed = true;
            closeQuietly(socket);
            closeQuietly(serverSocket);
        }

        private void runHost() {
            try (ServerSocket listener = new ServerSocket(port)) {
                serverSocket = listener;
                statusHandler.accept("Hosting on port " + port + ". Waiting for Player O...");
                Socket accepted = listener.accept();
                if (closed) {
                    closeQuietly(accepted);
                    return;
                }
                socket = accepted;
                setupWriter(accepted);
                inboundHandler.accept("CONNECTED");
                send("ROLE O");
                readLoop(accepted);
            } catch (IOException exception) {
                if (!closed) {
                    disconnectHandler.accept("Could not host game: " + exception.getMessage());
                }
            } finally {
                close();
            }
        }

        private void runClient() {
            try {
                Socket clientSocket = new Socket();
                clientSocket.connect(new InetSocketAddress(hostAddress, port), 8000);
                if (closed) {
                    closeQuietly(clientSocket);
                    return;
                }
                socket = clientSocket;
                setupWriter(clientSocket);
                statusHandler.accept("Connected to host. Waiting for game setup...");
                readLoop(clientSocket);
            } catch (IOException exception) {
                if (!closed) {
                    disconnectHandler.accept("Could not join game: " + exception.getMessage());
                }
            } finally {
                close();
            }
        }

        private void setupWriter(Socket activeSocket) throws IOException {
            writer = new PrintWriter(activeSocket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        private void readLoop(Socket activeSocket) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    inboundHandler.accept(line);
                }
                if (!closed) {
                    disconnectHandler.accept("The connection was closed.");
                }
            }
        }

        private static void closeQuietly(Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Already closing.
            }
        }
    }
}
