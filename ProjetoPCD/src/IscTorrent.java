
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class IscTorrent {

    // Componentes da interface
    private final JFrame frame;
    private JPanel topPanel;
    private JPanel bottomPanel;
    private JLabel label;
    private JTextField textField;
    private JButton searchButton;
    private JPanel rightPanel;
    private JButton downloadButton;
    private JButton connectButton;
    private DefaultListModel<String> listModel;

    private Node node;

    // Armazenar resultados da última pesquisa
    private List<FileSearchResult> lastSearchResults = new ArrayList<>();

    public IscTorrent(int port, String workDir) throws IOException {
        File workDirFile = new File(workDir);

        // Se for um caminho relativo, tenta ajustá-lo à raiz do projeto
        if (!workDirFile.isAbsolute()) {
            // Sobe da pasta onde o Java está a correr para a raiz do projeto
            File projectRoot = new File(System.getProperty("user.dir")).getParentFile();
            workDirFile = new File(projectRoot, workDir);
        }

        String absoluteWorkDir = workDirFile.getCanonicalPath();

        if (!workDirFile.exists() || !workDirFile.isDirectory()) {
            System.err.println("[ERRO] Diretório não encontrado: " + absoluteWorkDir);
            System.exit(1);
        }
        System.out.println("[INFO] Diretório definido como: " + absoluteWorkDir);

        this.node = new Node(absoluteWorkDir, port);
        frame = new JFrame("Aplicação de Pesquisa: " + node.getListenPort());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 300);
        frame.setLocationRelativeTo(null);
        addFrameContent();
    }

    public void open() {
        frame.setVisible(true);

    }

    public void addFrameContent() {
        frame.setLayout(new BorderLayout());

        // Painel superior -> Contém a label, campo de texto e botao de pesquisa
        topPanel = new JPanel(new GridLayout());
        label = new JLabel("Texto a procurar: ");
        textField = new JTextField();
        searchButton = new JButton("Procurar");

        topPanel.add(label);
        topPanel.add(textField);
        topPanel.add(searchButton);

        // Lista de resultados -> Apresenta os resultados da pesquisa
        listModel = new DefaultListModel<>();
        JList<String> resultList = new JList<>(listModel);
        // Seleção multipla
        resultList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(resultList);

        // Painel lateral direito -> Contem os botões "Descarregar" e "Ligar nó"
        rightPanel = new JPanel();
        rightPanel.setLayout(new GridLayout(2, 1, 5, 5));
        downloadButton = new JButton("Descarregar");
        connectButton = new JButton("Ligar a nó");

        rightPanel.add(downloadButton);
        rightPanel.add(connectButton);

        // Painel inferior -> Combina a lista de resultados e o painel lateral direito
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        // Adicionar tudo na frame principal
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(bottomPanel);

        // Ações dos botões
        connectButton.addActionListener(e -> openNodeConnection());
        searchButton.addActionListener(e -> searchFiles());
        downloadButton.addActionListener(e -> downloadFiles());
    }

    public void openNodeConnection() {
        NodeFrame nodeFrame = new NodeFrame(node);
        nodeFrame.open();
    }

    public void searchFiles() {
        String keyword = textField.getText().trim();
        if (keyword.isEmpty()) {
            return;
        }

        // Executar pesquisa em background
        new Thread(() -> {
            try {
                // Obtém todos os resultados da pesquisa
                List<FileSearchResult> allResults = node.searchFiles(keyword);

                // Filtra os resultados, excluindo arquivos que já existem localmente
                List<FileSearchResult> filteredResults = new ArrayList<>();
                for (FileSearchResult result : allResults) {
                    // Adiciona apenas arquivos que NÃO existem localmente
                    if (!node.hasLocalFile(result.getFileName())) {
                        filteredResults.add(result);
                    }
                }

                // Guarda apenas os resultados filtrados
                lastSearchResults = filteredResults;

                // Agrupar por nome de ficheiro e contar quantos peers têm cada ficheiro
                java.util.Map<String, Integer> fileCounts = new java.util.HashMap<>();
                for (FileSearchResult res : filteredResults) {
                    fileCounts.put(res.getFileName(), fileCounts.getOrDefault(res.getFileName(), 0) + 1);
                }

                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (String fileName : fileCounts.keySet()) {
                        int count = fileCounts.get(fileName);
                        listModel.addElement(fileName + "<" + count + ">");
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void downloadFiles() {
        JList<String> resultList = (JList<String>) ((JScrollPane) bottomPanel.getComponent(0)).getViewport().getView();
        int[] selectedIndices = resultList.getSelectedIndices();

        if (selectedIndices.length == 0) {
            JOptionPane.showMessageDialog(frame, "Selecione pelo menos um arquivo para download",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Para cada índice selecionado
        for (int selectedIndex : selectedIndices) {
            String selectedItem = listModel.getElementAt(selectedIndex);
            // Remove o <numero> do final
            String fileName = selectedItem.substring(0, selectedItem.lastIndexOf('<')).trim();

            // Procura por todos os peers que têm o arquivo
            List<FileSearchResult> sources = new ArrayList<>();
            for (FileSearchResult result : lastSearchResults) {
                if (result.getFileName().equals(fileName)) {
                    sources.add(result);
                }
            }

            if (sources.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                        "Nenhuma fonte disponível para o arquivo: " + fileName,
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                continue; // Pula para o próximo arquivo
            }

            // Usa o tamanho do primeiro resultado encontrado 
            long fileSize = sources.get(0).getFileSize();

            // Inicia o download em thread separada
            final String finalFileName = fileName;
            new Thread(() -> {
                System.out.println("[INFO] Iniciando download de " + finalFileName);
                DownloadTaskManager downloadManager = new DownloadTaskManager(node.getWorkDir(), node);
                downloadManager.startDownload(finalFileName, fileSize, sources);
            }).start();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Uso: java IscTorrent <porto> <pasta>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        String folder = args[1];

        SwingUtilities.invokeLater(() -> {
            try {
                IscTorrent iscTorrent = new IscTorrent(port, folder);
                iscTorrent.open();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
