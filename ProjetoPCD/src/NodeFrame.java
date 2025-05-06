
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class NodeFrame {

    private JFrame frame = new JFrame();
    private JLabel labelEndereco;
    private JLabel labelPorta;
    private JTextField textFieldEndereco;
    private JTextField textFieldPorta;
    private JButton buttonCancel;
    private JButton buttonOK;

    private Node node;

    public NodeFrame(Node node) {
        this.node = node;
        frame = new JFrame("Ligar a nó");
        frame.setSize(550, 70);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addFrameContent();
    }

    public void open() {
        frame.setVisible(true);
    }

    public void addFrameContent() {
        frame.setLayout(new GridLayout(1, 6, 5, 5));
        labelEndereco = new JLabel("Endereço:");
        labelPorta = new JLabel("Porta:");
        textFieldEndereco = new JTextField();
        textFieldEndereco.setText("localhost");
        textFieldPorta = new JTextField();
        buttonCancel = new JButton("Cancelar");
        buttonOK = new JButton("OK");

        frame.add(labelEndereco);
        frame.add(textFieldEndereco);
        frame.add(labelPorta);
        frame.add(textFieldPorta);
        frame.add(buttonCancel);
        frame.add(buttonOK);

        buttonCancel.addActionListener(e -> frame.dispose());

        buttonOK.addActionListener(e -> {
            String host = textFieldEndereco.getText();
            int port = Integer.parseInt(textFieldPorta.getText());
            node.connectToNode(host, port); // Liga ao nó especificado
            frame.dispose();
        });
    }
}
