# OX LAN Tic-Tac-Toe

A Java Swing O and X game with:

- Offline mode where you choose X or O and play against the computer.
- Local 2-player mode where players take turns on the same computer.
- Online LAN mode for two computers on the same Wi-Fi/LAN.
- Standard Windows window controls for minimize, maximize, and close.
- Maven build and a Windows `.exe` launcher generated through `jpackage`.

## Run From Maven

```powershell
mvn package
java -jar target\ox-lan-tictactoe-1.0.0.jar
```

## Build The `.exe` Launcher

This uses the JDK `jpackage` tool. Run:

```powershell
mvn clean package -Pexe
```

The runnable launcher will be created here:

```text
target\dist\OX-LAN-TicTacToe\OX-LAN-TicTacToe.exe
```

## LAN Play

1. Connect both computers to the same Wi-Fi or LAN.
2. Player X clicks `Online`, then chooses `Host`.
3. Player O enters Player X's displayed LAN IP address, clicks `Online`, then chooses `Join`.
4. If Windows Firewall asks, allow Java on private networks.

Default port: `5050`.

## Offline Play

1. Click `Offline`.
2. Choose `X` or `O`.
3. If you choose `X`, the computer plays `O`.
4. If you choose `O`, the computer plays `X` and makes the first move.

## Local 2-Player Play

1. Click `2 Players` or choose `Game` > `New Local 2-Player Game`.
2. Players take turns placing X and O on the same board. X goes first.
