
import java.awt.*;
import java.util.Map;
import javax.swing.*;

public class DownloadStatsFrame {

    private JFrame frame;

    private String fileName;
    private long fileSize;
    private long elapsedTime;
    private Map<String, Integer> peersBlockCounts;

    public DownloadStatsFrame(String fileName, long fileSize, long elapsedTime, Map<String, Integer> peersBlockCounts) {

        this.fileName = fileName;
        this.fileSize = fileSize;
        this.elapsedTime = elapsedTime;
        this.peersBlockCounts = peersBlockCounts;

        frame = new JFrame("Download Concluído");
        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Painel principal
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Informações básicas
        panel.add(new JLabel("Download completo!"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(new JLabel("Arquivo: " + fileName));
        panel.add(new JLabel("Tamanho: " + fileSize + " bytes"));
        panel.add(new JLabel("Tempo: " + (elapsedTime / 1000.0) + " segundos"));
        panel.add(Box.createVerticalStrut(10));

        // Título para lista de peers
        panel.add(new JLabel("Blocos por peer:"));
        panel.add(Box.createVerticalStrut(5));

        // Lista de peers e blocos
        JPanel peersPanel = new JPanel();
        peersPanel.setLayout(new GridLayout(0, 2, 5, 2));

        for (Map.Entry<String, Integer> entry : peersBlockCounts.entrySet()) {
            peersPanel.add(new JLabel(entry.getKey() + ":"));
            peersPanel.add(new JLabel(entry.getValue() + " blocos"));
        }

        // Adiciona o painel de peers a um ScrollPane
        JScrollPane scrollPane = new JScrollPane(peersPanel);
        scrollPane.setPreferredSize(new Dimension(350, 150));
        panel.add(scrollPane);

        // Botão OK
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> frame.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);

        panel.add(Box.createVerticalStrut(10));
        panel.add(buttonPanel);

        frame.add(panel);
    }

    public void show() {
        frame.setVisible(true);
    }

    // Adicionar método para obter string de estatísticas
    public String getStatsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Download completo!\n");
        sb.append("Arquivo: ").append(fileName).append("\n");
        sb.append("Tamanho: ").append(fileSize).append(" bytes\n");
        sb.append("Tempo: ").append(elapsedTime / 1000.0).append(" segundos\n\n");
        sb.append("Blocos por peer:\n");

        for (Map.Entry<String, Integer> entry : peersBlockCounts.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(" blocos\n");
        }

        return sb.toString();
    }
}
