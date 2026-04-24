package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.Main;
import tool.logic.BeanMaker;

public class BeanMakerMain {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private JTextPane txtSql;
    private JTextPane txtResult;
    private JTextField txtBeanName;
    private JComboBox<String> comboBox;

    public JPanel initialize(Font f) {
        JPanel jp = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(f);
                g.setColor(Color.RED);
                g.drawString("Bean 名稱:", 10, 30);
                g.drawString("DB 選擇:", 450, 30);
                g.drawString("SQL 輸入 (多個SQL以空白行分隔):", 10, 65);
                g.drawString("產生結果:", 10, 385);
            }
        };

        jp.setLayout(null);

        // Bean name input
        txtBeanName = new JTextField();
        txtBeanName.setBounds(110, 10, 310, 30);
        txtBeanName.setFont(f);
        jp.add(txtBeanName);

        // DB selection
        String[] items = Main.dataSourceMap.keySet().toArray(new String[0]);
        comboBox = new JComboBox<>(items);
        comboBox.setBounds(530, 10, 140, 30);
        comboBox.setFont(f);
        jp.add(comboBox);

        // SQL input area
        txtSql = new JTextPane();
        JScrollPane spSql = new JScrollPane(txtSql);
        spSql.setBounds(10, 75, 760, 290);
        txtSql.setFont(f);
        jp.add(spSql);

        // Result area
        txtResult = new JTextPane();
        JScrollPane spResult = new JScrollPane(txtResult);
        spResult.setBounds(10, 395, 760, 285);
        txtResult.setFont(f);
        jp.add(spResult);

        // Generate button
        JButton btnGenerate = new JButton("Generate Bean");
        btnGenerate.addActionListener(new ListenerGenerate());
        btnGenerate.setBounds(285, 695, 210, 35);
        btnGenerate.setFont(f);
        jp.add(btnGenerate);

        return jp;
    }

    class ListenerGenerate implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            BeanMaker maker = new BeanMaker();
            try {
                Object selected = comboBox.getSelectedItem();
                if (selected != null) {
                    String jdbc = Main.dataSourceMap.get(selected.toString());
                    if (jdbc != null) {
                        Main.getNames(jdbc);
                    }
                }
                String result = maker.process(
                        txtSql.getText(),
                        txtBeanName.getText().trim(),
                        Main.tableColumnsMap);
                txtResult.setText(result);
            } catch (Exception ex) {
                txtResult.setText(ex.getMessage());
                logger.error(ex.getMessage(), ex);
            }
        }
    }
}
