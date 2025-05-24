
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DownloadTaskManager {

    private static final int BLOCK_SIZE = 10240;
    private List<FileBlockRequestMessage> pendingBlocks;
    private Map<Integer, byte[]> downloadedBlocks;
    private String fileName;
    private long fileSize;
    private String workDir;
    private long startTime;
    private Map<String, Integer> peersBlockCount;
    private boolean downloading = false;

    // Cadeado e variáveis condicionais para coordenação
    private final Lock lock = new ReentrantLock();
    private final Condition downloadCompleteCondition = lock.newCondition();
    private int totalBlocks = 0;
    private int completedBlocks = 0;

    private Node node;

    public DownloadTaskManager(String workDir, Node node) {
        this.pendingBlocks = Collections.synchronizedList(new ArrayList<>());
        this.downloadedBlocks = new ConcurrentHashMap<>();
        this.peersBlockCount = new ConcurrentHashMap<>();
        this.workDir = workDir;
        this.node = node;
    }

    public void startDownload(String fileName, long fileSize, List<FileSearchResult> sources) {
        lock.lock();
        try {
            if (downloading) {
                System.err.println("[ERRO] Já existe um download a decorrer");
                return;
            }

            this.fileName = fileName;
            this.fileSize = fileSize;
            this.startTime = System.currentTimeMillis();
            this.downloading = true;

            // Inicializa a contagem de blocos por peer
            for (FileSearchResult source : sources) {
                String peerKey = source.getHostName() + ":" + source.getOriginPort();
                peersBlockCount.put(peerKey, 0);
            }

            // Cria a lista dos blocos
            createBlockRequests(fileName, fileSize);

            // Define o total de blocos a serem baixados
            totalBlocks = pendingBlocks.size();
            completedBlocks = 0;

            // Inicia as threads para download de cada peer
            for (FileSearchResult source : sources) {
                new Thread(() -> downloadFromPeer(source)).start();
            }

            // Cria thread para escrever o arquivo quando completo
            new Thread(this::waitAndWrite).start();
        } finally {
            lock.unlock();
        }
    }

    private void waitAndWrite() {
        lock.lock();
        try {
            // Aguarda até que todos os blocos sejam transferidos
            while (completedBlocks < totalBlocks) {
                try {
                    downloadCompleteCondition.await();
                } catch (InterruptedException e) {
                    System.err.println("[ERRO] Thread de escrita interrompida: " + e.getMessage());
                    return;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // Escreve o ficheiro no disco
            writeFileToDisk();

            // Exibe as estatísticas
            showDownloadStatistics(elapsed);

            // Reset do estado
            downloading = false;

        } finally {
            lock.unlock();
        }
    }

    private void writeFileToDisk() {
        File file = new File(workDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Escreve os blocos na ordem correta
            for (int i = 0; i < totalBlocks; i++) {
                byte[] data = downloadedBlocks.get(i);
                if (data != null) {
                    fos.write(data);
                }
            }
            System.out.println("[INFO] Ficheiro guardado em : " + file.getAbsolutePath());
            node.refreshFiles();
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao guardar o ficheiro: " + e.getMessage());
        }
    }

    private void showDownloadStatistics(long elapsed) {
        // Apenas cria e chama o frame, sem lógica de formatação
        javax.swing.SwingUtilities.invokeLater(() -> {
            DownloadStatsFrame statsFrame = new DownloadStatsFrame(fileName, fileSize, elapsed, peersBlockCount);
            statsFrame.show();
        });
    }

    private void downloadFromPeer(FileSearchResult source) {
        String peerKey = source.getHostName() + ":" + source.getOriginPort();

        try {
            while (true) {
                // Pega no proximo bloco para download, de forma sincronizada
                FileBlockRequestMessage block;

                lock.lock();
                try {
                    if (pendingBlocks.isEmpty()) {
                        break; // Nao ha mais blocos
                    }
                    block = pendingBlocks.remove(0);
                } finally {
                    lock.unlock();
                }

                try {
                    // Pede o bloco ao peer
                    byte[] data = requestBlockFromPeer(source, block);

                    // Armazena o bloco e atualiza contadores
                    lock.lock();
                    try {
                        int blockIndex = (int) (block.getOffset() / BLOCK_SIZE);
                        downloadedBlocks.put(blockIndex, data);

                        // Atualiza a contagem deste peer
                        peersBlockCount.put(peerKey, peersBlockCount.get(peerKey) + 1);

                        // Marca um bloco como concluído
                        completedBlocks++;

                        // Se todos os blocos foram transferidos, sinaliza a condição
                        if (completedBlocks >= totalBlocks) {
                            downloadCompleteCondition.signal();
                        }
                    } finally {
                        lock.unlock();
                    }

                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[ERRO] Falha ao baixar bloco de " + peerKey + ": " + e.getMessage());

                    // Devolve o bloco à lista para ser tentado por outro peer
                    lock.lock();
                    try {
                        pendingBlocks.add(block);
                    } finally {
                        lock.unlock();
                    }

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERRO] Falha na thread de download: " + e.getMessage());
        }
    }

    private byte[] requestBlockFromPeer(FileSearchResult source, FileBlockRequestMessage block) throws IOException, ClassNotFoundException {
        // Estabelece conexão com o peer
        try (Socket socket = new Socket(source.getHostName(), source.getOriginPort()); ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Envia pedido do bloco
            out.writeObject(block);
            out.flush();

            Object response = in.readObject();
            if (response instanceof FileBlockAnswerMessage answer) {
                return answer.getData();
            } else {
                throw new IOException("Resposta inesperada ao pedido de bloco");
            }
        }
    }

    public void createBlockRequests(String fileName, long fileSize) {
        lock.lock();
        try {
            pendingBlocks.clear();
            long offset = 0;

            while (offset < fileSize) {
                int length = (int) Math.min(BLOCK_SIZE, fileSize - offset);
                pendingBlocks.add(new FileBlockRequestMessage(fileName, offset, length));
                offset += length;
            }
        } finally {
            lock.unlock();
        }
    }

    public List<FileBlockRequestMessage> getBlockRequests() {
        lock.lock();
        try {
            return new ArrayList<>(pendingBlocks);
        } finally {
            lock.unlock();
        }
    }

    public boolean isDownloading() {
        lock.lock();
        try {
            return downloading;
        } finally {
            lock.unlock();
        }
    }
}
