package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import tool.logic.BeanToSqlColumns;

public class BeanToSqlColumnsMain {

    private JTextField txtAlias;
    private JTextArea  txtInput;
    private JTextArea  txtSqlSelect;
    private JTextArea  txtOutput;

    public JPanel initialize(Font f) {
        JPanel jp = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(f);
                // 藍字基準線 y=50，頂端 ~38，別名欄底部 38，剛好貼齊
                g.setColor(Color.BLUE);
                g.drawString("Bean 欄位輸入 (private 宣告 + Javadoc):", 10, 50);
                // 綠字基準線 y=276，頂端 ~264，spInput 底部 258，留 6px 間距
                g.setColor(new Color(0, 128, 0));
                g.drawString("已存在 SQL SELECT 欄位 (選填，有相同別名的 Bean 欄位將跳過):", 10, 276);
                // 紅字基準線 y=450，頂端 ~438，按鈕底部 433，留 5px 間距
                g.setColor(Color.RED);
                g.drawString("輸出:", 10, 450);
            }
        };
        jp.setLayout(null);

        Font mono = new Font("Monospaced", Font.PLAIN, 12);

        // 別名輸入列  y=10, h=28, 底部 38
        JLabel lblAlias = new JLabel("別名 (alias):");
        lblAlias.setFont(f);
        lblAlias.setBounds(10, 10, 110, 28);
        jp.add(lblAlias);

        txtAlias = new JTextField();
        txtAlias.setFont(f);
        txtAlias.setBounds(125, 10, 100, 28);
        jp.add(txtAlias);

        // Bean 輸入區  y=55, h=198, 底部 253
        txtInput = new JTextArea();
        txtInput.setFont(mono);
        txtInput.setLineWrap(false);
        JScrollPane spInput = new JScrollPane(txtInput);
        spInput.setBounds(10, 55, 760, 198);
        jp.add(spInput);

        // SQL SELECT 輸入區  y=282, h=110, 底部 392
        txtSqlSelect = new JTextArea();
        txtSqlSelect.setFont(mono);
        txtSqlSelect.setLineWrap(false);
        JScrollPane spSqlSelect = new JScrollPane(txtSqlSelect);
        spSqlSelect.setBounds(10, 282, 760, 110);
        jp.add(spSqlSelect);

        // 按鈕列  y=398, h=35, 底部 433
        JButton btnGenerate = new JButton("產生 →");
        btnGenerate.setBounds(10, 398, 200, 35);
        btnGenerate.setFont(f);
        btnGenerate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    BeanToSqlColumns converter = new BeanToSqlColumns();
                    String result = converter.convert(
                            txtAlias.getText(),
                            txtInput.getText(),
                            txtSqlSelect.getText());
                    txtOutput.setText(result);
                } catch (Exception ex) {
                    txtOutput.setText("ERROR: " + ex.getMessage());
                }
            }
        });
        jp.add(btnGenerate);

        JButton btnClear = new JButton("清除");
        btnClear.setBounds(220, 398, 100, 35);
        btnClear.setFont(f);
        btnClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtAlias.setText("");
                txtInput.setText("");
                txtSqlSelect.setText("");
                txtOutput.setText("");
            }
        });
        jp.add(btnClear);

        // 輸出區  y=456, h=268, 底部 724
        txtOutput = new JTextArea();
        txtOutput.setFont(mono);
        txtOutput.setLineWrap(false);
        JScrollPane spOutput = new JScrollPane(txtOutput);
        spOutput.setBounds(10, 456, 760, 268);
        jp.add(spOutput);

        return jp;
    }
}
