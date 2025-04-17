
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Node {

    private String path;
    private Map<BigInteger, File> files = new ConcurrentHashMap<>();

    public Node(String path) {
        this.path = path;
        readFiles();
    }

    public void readFiles() {
        File folder = new File(path);
        File[] filesList = folder.listFiles(File::isFile);

        if (filesList == null) {
            System.err.println("Diretoria inválida: " + folder);
            return;
        }

        for (File file : filesList) {
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(content);
                BigInteger hash = new BigInteger(1, hashBytes);
                files.put(hash, file);
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Erro ao processar ficheiro: " + file);
            }
        }
    }

    public Map<BigInteger, File> getFiles() {
        return files;
    }

    // Testes
    public static void main(String[] args) {
        String path = "C:\\Users\\eduar\\OneDrive\\Documentos\\GitHub\\Projeto-PCD\\ProjetoPCD\\dl1";

        Node node = new Node(path);

        System.out.println("Ficheiros na diretoria:");
        Map<BigInteger, File> files = node.getFiles();
        for (File file : files.values()) {
            System.out.println("Ficheiro: " + file.getName());
        }
    }
}
