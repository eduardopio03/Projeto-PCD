
import java.awt.*;
import javax.swing.*;

public class IscTorrent {

    // Componentes principais da interface
    private final JFrame frame;
    private JPanel topPanel;
    private JPanel bottomPanel;
    private JLabel label;
    private JTextField textField;
    private JButton searchButton;
    private JPanel rightPanel;
    private JButton downloadButton;
    private JButton connectButton;

    public IscTorrent() {
        frame = new JFrame("Aplicação de Pesquisa ");
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

        // Painel superior -> Contem a label, campo de texto e botao de pesquisa
        topPanel = new JPanel(new GridLayout());
        label = new JLabel("Texto a procurar: ");
        textField = new JTextField();
        searchButton = new JButton("Procurar");

        topPanel.add(label);
        topPanel.add(textField);
        topPanel.add(searchButton);

        // Lista de resultados -> Apresenta os resultados da pesquisa
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> resultList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(resultList);

        // Painel lateral direito -> Contem os botoes "Descarregar" e "Ligar no"
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

    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IscTorrent iscTorrent = new IscTorrent();
            iscTorrent.open();
        });
    }
}
