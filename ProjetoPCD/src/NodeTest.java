
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Map;

public class NodeTest {

    public static void main(String[] args) throws Exception {
        // Configurações
        String dir1 = "dl1";      // pasta de trabalho do nó 1
        String dir2 = "dl2";      // pasta de trabalho do nó 2
        int port1 = 9001;
        int port2 = 9002;
        String host = "127.0.0.1";

        // 1. Instanciar os nós
        Node node1 = new Node(dir1, port1);
        Node node2 = new Node(dir2, port2);

        // 2. Mostrar ficheiros lidos em cada nó
        System.out.println("=== Node1 ficheiros carregados ===");
        for (Map.Entry<BigInteger, File> e : node1.getFiles().entrySet()) {
            System.out.println(e.getValue().getName());
        }
        System.out.println("=== Node2 ficheiros carregados ===");
        for (Map.Entry<BigInteger, File> e : node2.getFiles().entrySet()) {
            System.out.println(e.getValue().getName());
        }

        // 3. Ligar node1 ao node2 (handshake)
        System.out.printf("Node1 a ligar a %s:%d…%n", host, port2);
        node1.connectToNode(host, port2);

        // Dar tempo para o handshake acontecer
        Thread.sleep(2000);

        // 4. Obter nº de peers via reflection (não é preciso alterar Node)
        Field peersField = Node.class.getDeclaredField("peers");
        peersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<?, ?> peers1 = (Map<?, ?>) peersField.get(node1);
        @SuppressWarnings("unchecked")
        Map<?, ?> peers2 = (Map<?, ?>) peersField.get(node2);

        System.out.println("Node1 ligações estabelecidas: " + peers1.size());
        System.out.println("Node2 ligações estabelecidas: " + peers2.size());

        // Aguardar um bocadinho para veres o PING no console do node2
        Thread.sleep(1000);

        System.out.println("Teste concluído.");
        System.exit(0);
    }
}
