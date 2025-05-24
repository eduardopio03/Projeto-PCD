
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class Node {

    private final String workDir;
    private final int listenPort;
    private final Map<BigInteger, File> files = new ConcurrentHashMap<>();
    private final List<PeerInfo> peers = new CopyOnWriteArrayList<>();

    public Node(String workDir, int listenPort) throws IOException {
        this.workDir = workDir;
        this.listenPort = listenPort;
        System.out.println("[INFO] Incializar nó com diretório: " + workDir + " e porta: " + listenPort);
        readFiles();         // Leitura inicial dos ficheiros
        startServer();       // Inicia o servidor deste nó
    }

    public void addPeer(String host, int port) {
        peers.add(new PeerInfo(host, port));
    }

    public String getWorkDir() {
        return workDir;
    }

    public int getListenPort() {
        return listenPort;
    }

    public boolean hasLocalFile(String fileName) {
        // Verifica em todos os arquivos do mapa se existe algum com o nome especificado
        for (File file : files.values()) {
            if (file.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    // Lê os ficheiros da pasta e guarda no mapa
    private void readFiles() {
        File folder = new File(workDir);
        File[] list = folder.listFiles(File::isFile);
        if (list == null) {
            System.err.println("[ERRO] Pasta inválida ou inacessível: " + workDir
            );
            return;
        }
        System.out.println("[INFO] A ler ficheiros do diretório: " + workDir);
        for (File f : list) {
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
                BigInteger key = new BigInteger(1, hash);
                files.put(key, f);
                System.out.println("[INFO] Ficheiro registado: " + f.getName());
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("[ERRO] Falha ao processar ficheiro: " + f.getName() + " - " + e.getMessage());
            }
        }
    }

    // ServerSocket e handshake de NewConnectionRequest
    private void startServer() throws IOException {
        ServerSocket server = new ServerSocket(listenPort);
        System.out.println("[INFO] Servidor iniciado na porta " + listenPort);

        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = server.accept();
                    System.out.println("[INFO] Nova ligação recebida de " + socket.getRemoteSocketAddress());
                    handleConnection(socket);
                } catch (IOException e) {
                    System.err.println("[ERRO] Erro ao aceitar ligação: " + e.getMessage());
                }
            }
        }).start();
    }

    private void handleConnection(Socket socket) {
        new Thread(() -> {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                Object obj = in.readObject();

                // Encaminhar para os métodos específicos conforme o tipo de mensagem
                if (obj instanceof NewConnectionRequest req) {
                    handleNewConnection(req, out);
                } else if (obj instanceof WordSearchMessage wsm) {
                    handleWordSearch(wsm, out);
                } else if (obj instanceof FileBlockRequestMessage blockRequest) {
                    handleBlockRequest(blockRequest, out);
                } else {
                    System.err.println("[ERRO] Mensagem inesperada durante handshake: " + obj);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Erro no handshake: " + e.getMessage());
            }
        }).start();
    }

    // Método específico para lidar com pedido de nova conexão
    private void handleNewConnection(NewConnectionRequest req, ObjectOutputStream out) throws IOException {
        System.out.printf("[INFO] Pedido de ligação de %s:%d%n", req.getHost(), req.getPort());
        // Responde com os mesmos dados para confirmar
        String localHost = InetAddress.getLocalHost().getHostAddress();
        NewConnectionRequest reply = new NewConnectionRequest(localHost, listenPort);
        out.writeObject(reply);
        out.flush();
        addPeer(req.getHost(), req.getPort());
        System.out.println("[INFO] Ligação estabelecida com sucesso.");
    }

    // Método específico para lidar com pedido de pesquisa
    private void handleWordSearch(WordSearchMessage wsm, ObjectOutputStream out) throws IOException {
        // Pesquisa ficheiros locais e responde com a lista de resultados
        List<FileSearchResult> results = new ArrayList<>();
        for (Map.Entry<BigInteger, File> entry : files.entrySet()) {
            File f = entry.getValue();
            if (f.getName().contains(wsm.getSearchWord())) {
                results.add(new FileSearchResult(
                        wsm,
                        (int) f.length(),
                        f.getName(),
                        listenPort,
                        InetAddress.getLocalHost().getHostAddress()));
            }
        }
        out.writeObject(results);
        out.flush();
        System.out.println("[INFO] Pesquisa recebida e respondida.");
    }

    private void handleBlockRequest(FileBlockRequestMessage request, ObjectOutputStream out) throws IOException {
        String fileName = request.getFileName();
        long offset = request.getOffset();
        int length = request.getLength();

        // Procura o ficheiro localmente
        File requestedFile = null;
        for (File file : files.values()) {
            if (file.getName().equals(fileName)) {
                requestedFile = file;
                break;
            }
        }

        if (requestedFile == null) {
            System.err.println("[ERRO] Ficheiro solicitado não encontrado: " + fileName);
            // Envia resposta vazia
            out.writeObject(new FileBlockAnswerMessage(fileName, offset, new byte[0]));
            out.flush();
            return;
        }

        try {
            // Le o bloco solicitado
            byte[] data = new byte[length];
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(requestedFile, "r")) {
                raf.seek(offset);
                int bytesRead = raf.read(data, 0, length);

                // Se leu menos bytes que o solicitadom ajusta o array
                if (bytesRead < length) {
                    byte[] trimmedData = new byte[bytesRead];
                    System.arraycopy(data, 0, trimmedData, 0, bytesRead);
                    data = trimmedData;
                }
            }
            // Envia resposta
            FileBlockAnswerMessage response = new FileBlockAnswerMessage(fileName, offset, data);
            out.writeObject(response);
            out.flush();

            System.out.println("[INFO] Bloco enviado: " + fileName + " (offset=" + offset + ", length=" + data.length + ")");
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao ler bloco: " + e.getMessage());
            // Envia resposta vazia para indicar erro
            out.writeObject(new FileBlockAnswerMessage(fileName, offset, new byte[0]));
            out.flush();
        }
    }

    // Ligação a outro nó manualmente
    public void connectToNode(String host, int port) {
        new Thread(() -> {
            try {
                // Verificar se o nó não se liga a si mesmo
                String localHost = InetAddress.getLocalHost().getHostAddress();
                if ((host.equals(localHost) || host.equals("localhost")) && port == listenPort) {
                    System.err.println("[ERRO] Não é possível ligar-se a si mesmo.");
                    return;
                }
                System.out.printf("[INFO] A tentar ligar a %s:%d...%n", host, port);
                try (Socket sock = new Socket(host, port); ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream()); ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {
                    // Envia pedido de conexão
                    out.writeObject(new NewConnectionRequest(localHost, listenPort));
                    out.flush();
                    // Lê resposta
                    Object obj = in.readObject();
                    if (obj instanceof NewConnectionRequest reply) {
                        System.out.printf("[INFO] Ligação aceite por %s:%d%n", reply.getHost(), reply.getPort());
                        addPeer(host, port);
                    } else {
                        System.err.println("[ERRO] Resposta inesperada: " + obj);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Não foi possível ligar a " + host + ":" + port + " - " + e.getMessage());
            }
        }
        ).start();
    }

    public Map<BigInteger, File> getFiles() {
        return files;
    }

    // Método de pesquisa remota
    public List<FileSearchResult> searchFiles(String keyword) throws InterruptedException {
        WordSearchMessage msg = new WordSearchMessage(keyword);
        int n = peers.size();
        CountDownLatch latch = new CountDownLatch(n);
        List<FileSearchResult> aggregated = Collections.synchronizedList(new ArrayList<>());

        for (PeerInfo peer : peers) {
            new Thread(() -> {
                try (Socket sock = new Socket(peer.getHost(), peer.getPort()); ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream()); ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {

                    // Envia pesquisa
                    out.writeObject(msg);
                    out.flush();

                    // Espera lista de resultados
                    Object resp = in.readObject();
                    if (resp instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof FileSearchResult fsr) {
                                aggregated.add(fsr);
                            }
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Erro a contactar peer " + peer + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        // Aguarda respostas
        latch.await();
        return aggregated;
    }

    // Método público para recarregar arquivos do diretório
    public synchronized void refreshFiles() {
        // Limpar o mapa atual
        files.clear();

        // Recarregar os arquivos
        readFiles();

        System.out.println("[INFO] Lista de arquivos atualizada após download");
    }
}
