
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Node {

    private final String workDir;
    private final int listenPort;
    private final Map<BigInteger, File> files = new ConcurrentHashMap<>();

    public Node(String workDir, int listenPort) throws IOException {
        this.workDir = workDir;
        this.listenPort = listenPort;
        System.out.println("[INFO] Incializar nó com diretório: " + workDir + " e porta: " + listenPort);
        readFiles();         // Leitura inicial dos ficheiros
        startServer();       // Inicia o servidor deste nó
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

    // Handshake --> recebe NewConnectionRequest e responde
    private void handleConnection(Socket socket) {
        new Thread(() -> {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                Object obj = in.readObject();
                if (obj instanceof NewConnectionRequest req) {
                    System.out.printf("[INFO] Pedido de ligação de %s:%d%n", req.getHost(), req.getPort());
                    // Responde com os mesmos dados para confirmar
                    String localHost = InetAddress.getLocalHost().getHostAddress();
                    NewConnectionRequest reply = new NewConnectionRequest(localHost, listenPort);
                    out.writeObject(reply);
                    out.flush();
                    System.out.println("[INFO] Ligação estabelecida com sucesso.");
                } else {
                    System.err.println("[ERRO] Mensagem inesperada durante handshake: " + obj);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Erro no handshake: " + e.getMessage());
            }
        }).start();
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
                    } else {
                        System.err.println("[ERRO] Resposta inesperada: " + obj);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Não foi possível ligar a " + host + ":" + port + " - " + e.getMessage());
            }
        }).start();
    }

    public Map<BigInteger, File> getFiles() {
        return files;
    }
}
