# IscTorrent (Peer‑to‑Peer File Search & Download)

A simple peer‑to‑peer (P2P) file search and download application with a Swing GUI. Each node indexes files in a working directory, listens on a TCP port, connects to peers, searches by keyword, and downloads files in blocks from multiple sources concurrently.

## Overview

- Graphical client (`IscTorrent`) that starts a local `Node` server and provides UI to search and download files.
- Manual peer connection UI (`NodeFrame`) to add peers by host and port.
- File search propagates to all connected peers; results aggregate and display as `filename<count>` where `count` is number of peers with that file.
- Downloads are block‑based and concurrent across multiple peers (`DownloadTaskManager`).
- After downloading, files are saved to the node's working directory and the local index is refreshed. A statistics window (`DownloadStatsFrame`) shows per‑peer block counts, total size, and elapsed time.

## Project Structure

- [src/IscTorrent.java](src/IscTorrent.java): Swing GUI entry point and orchestration.
- [src/Node.java](src/Node.java): Local node server; file indexing, message handling, peer management.
- [src/NodeFrame.java](src/NodeFrame.java): UI to connect to another node ("Ligar a nó").
- [src/DownloadTaskManager.java](src/DownloadTaskManager.java): Concurrent, block‑based downloader.
- [src/DownloadStatsFrame.java](src/DownloadStatsFrame.java): Simple stats display.
- [src/FileSearchResult.java](src/FileSearchResult.java): Serializable search result payload.
- [src/WordSearchMessage.java](src/WordSearchMessage.java), [src/NewConnectionRequest.java](src/NewConnectionRequest.java): Serializable request messages.
- [src/FileBlockRequestMessage.java](src/FileBlockRequestMessage.java), [src/FileBlockAnswerMessage.java](src/FileBlockAnswerMessage.java): Block transfer messages.

Support folders:
- `dl1/`, `dl2/`: Sample working directories to run two local nodes.

## Prerequisites

- Java JDK 17+ installed and available on PATH (older versions may work; tested with modern JDKs).
- Windows, macOS, or Linux. The examples below use Windows PowerShell/CMD syntax.
- Local firewall should allow inbound connections to chosen ports if connecting across machines.

## Build & Run (no Maven/Gradle)

This project uses plain Java sources without a build tool. Compile to an `out` folder and run the GUI entry point.

```powershell
# From the repository root (the folder containing ProjetoPCD)
mkdir out
javac -d out ProjetoPCD/src/*.java
```

Start two nodes locally using the sample folders and different ports:

```powershell
# Node A
java -cp out IscTorrent 5001 dl1

# Node B (in a second terminal)
java -cp out IscTorrent 5002 dl2
```

Notes:
- The app accepts two args: `<port> <folder>`. Example: `5001 dl1`.
- If `<folder>` is a relative path, the app resolves it relative to the project root and prints the absolute path ("[INFO] Diretório definido como: ...").
- You can use absolute paths for `<folder>` as well, e.g. `C:\Data\NodeA`.

## Using the App

1. Launch two nodes as shown above.
2. In either GUI, click "Ligar a nó" and connect to the other node:
   - `Endereço`: `localhost` (or the peer's IP)
   - `Porta`: the other node's port (e.g., `5002`)
3. Type a keyword in "Texto a procurar" and click "Procurar".
   - Results exclude files already present locally.
   - Each result shows `filename<count>`, where `count` is how many peers have the file.
4. Select one or more results and click "Descarregar".
   - The downloader requests blocks concurrently from available peers.
   - When complete, a stats window appears and the file is saved under the node's working directory.

## How It Works (brief)

- The node indexes local files by computing SHA‑256 and keeps a map from hash → file.
- It listens on the given port and handles messages:
  - `NewConnectionRequest`: peer handshake and registration.
  - `WordSearchMessage`: returns a list of `FileSearchResult` for files whose names contain the keyword.
  - `FileBlockRequestMessage`: serves a specific file block (offset/length) and returns `FileBlockAnswerMessage` with data.
- The downloader splits the target file into 10KB blocks and distributes requests across peers; results are reassembled and written to disk.

## Troubleshooting

- "command not found" for `javac`/`java`: Ensure JDK is installed and `JAVA_HOME`/PATH are configured.
- Cannot connect to peer: Verify host/IP and port, and that the peer is running. On Windows, allow Java through the firewall or use `localhost` for local testing.
- Empty search results: Make sure peers are connected and have files in their working directory matching your keyword.
- Duplicate/local files: The UI filters out files already present locally.

## Extending

- Add automatic peer discovery or a simple registry service.
- Persist known peers and reconnect on startup.
- Add integrity checks per block and final hash verification.
- Support pause/resume and retry strategies per block.

---
