
import java.io.File;

public class Main {

    public static void main(String[] args) {
        // Dirtetoria onde os ficheiros estão armazenados
        String path = "C:\\Users\\eduar\\OneDrive\\Documentos\\GitHub\\Projeto-PCD\\ProjetoPCD\\dl1";

        // Inicializar o node para ler ficheiros da pasta
        Node node = new Node(path);

        // Obter os ficheiros disponíveis no node
        System.out.println("Ficheiros disponíveis no node:");
        node.getFiles().forEach((hash, file) -> System.out.println("Ficheiro: " + file.getName()));

        // Selecionar um ficheiro para criar os blocos
        File selectedFile = node.getFiles().values().stream().findAny().orElse(null);

        if (selectedFile == null) {
            System.out.println("Nenhum ficheiro encontrado na diretoria");
        }

        // Obter o tamanho do ficheiro
        long fileSize = selectedFile.length();
        String fileName = selectedFile.getName();

        // Inicializar o DownloadoTaskManager e criar os blocos
        DownloadTaskManager manager = new DownloadTaskManager();
        manager.createBlockRequests(fileName, fileSize);

        // Print na consola dos blocos criados
        System.out.println("\nBlocos criados para o ficheiro: " + fileName);
        for (FileBlockRequestMessage block : manager.getBlockRequests()) {
            System.out.println("Bloco: " + block.getFileName()
                    + ", Offset: " + block.getOffset()
                    + ", Length: " + block.getLength());
        }
    }
}
