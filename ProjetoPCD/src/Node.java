
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
    private final Map<Socket, ObjectOutputStream> peers = new ConcurrentHashMap<>();

    public Node(String workDir, int listenPort) throws IOException {
        this.workDir = workDir;
        this.listenPort = listenPort;
        System.out.println("[INFO] Incializar nó com diretoria: " + workDir + " e porta: " + listenPort);
        readFiles();         // Leitura inicial dos ficheiros
        startServer();       // Inicia o servidor para novas conexões
    }

    // Lê os ficheiros da pasta de trabalho e guarda no mapa
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
                System.out.println("[INFO] Ficheiro registado: " + f.getName() + " com chave " + key.toString(16));
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
                    Socket sock = server.accept();
                    System.out.println("[INFO] Nova ligação recebida de " + sock.getRemoteSocketAddress());
                    negotiateConnection(sock);
                } catch (IOException e) {
                    System.err.println("[ERRO] Falha ao aceitar ligação: " + e.getMessage());
                }
            }
        }).start();
    }

    // 3. Handshake e criação de canal de objectos
    private void negotiateConnection(Socket sock) {
        new Thread(() -> {
            try {
                ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(sock.getInputStream());

                // Lê pedido do peer
                Object obj = in.readObject();
                if (!(obj instanceof NewConnectionRequest)) {
                    System.err.println("[ERRO] Pedido de ligação inválido de: " + sock.getRemoteSocketAddress());
                    sock.close();
                    return;
                }
                NewConnectionRequest req = (NewConnectionRequest) obj;
                System.out.printf("[INFO] Pedido de ligação recebido de %s:%d%n", req.getHost(), req.getPort());

                // Guarda output stream para futuros envios
                peers.put(sock, out);

                // Envia o nosso pedido de volta
                String localHost = InetAddress.getLocalHost().getHostAddress();
                NewConnectionRequest reply = new NewConnectionRequest(localHost, listenPort);
                out.writeObject(reply);
                out.flush();

                System.out.println("[INFO] Ligação estabelecida com sucesso com " + req.getHost() + ":" + req.getPort());
                // ler outras mensagens do peer
                listenPeer(in);

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Falha no handshake com " + sock.getRemoteSocketAddress() + ": " + e.getMessage());
            }
        }).start();
    }

    // Conectar ativamente a outro nó
    public void connectToNode(String host, int port) {
        new Thread(() -> {
            try {
                System.out.printf("[INFO] A tentar ligar a %s:%d...%n", host, port);
                Socket sock = new Socket(host, port);
                ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(sock.getInputStream());

                // Envia pedido
                String localHost = InetAddress.getLocalHost().getHostAddress();
                out.writeObject(new NewConnectionRequest(localHost, listenPort));
                out.flush();

                // Lê resposta
                Object obj = in.readObject();
                if (!(obj instanceof NewConnectionRequest)) {
                    System.err.println("[ERRO] Resposta inválida recebida de " + host + ":" + port);
                    sock.close();
                    return;
                }
                NewConnectionRequest reply = (NewConnectionRequest) obj;
                System.out.printf("[INFO] Ligação aceite por %s:%d%n", reply.getHost(), reply.getPort());

                peers.put(sock, out);
                listenPeer(in);

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Falha ao ligar a " + host + ":" + port + " - " + e.getMessage());
            }
        }).start();
    }

    // Loop de receção de mensagens do peer
    private void listenPeer(ObjectInputStream in) {
        try {
            Object msg;
            while ((msg = in.readObject()) != null) {
                // Aqui tratarias WordSearchMessage, FileBlockRequestMessage, etc.
                System.out.println("[RECEBIDO] Mensagem do peer: " + msg.toString());
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[ERRO] Ligação perdida ou mensagem inválida: " + e.getMessage()); // MODIFICADO
        }
    }

    public Map<BigInteger, File> getFiles() {
        return files;
    }
}
