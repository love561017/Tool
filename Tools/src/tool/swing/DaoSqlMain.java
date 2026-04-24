package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import tool.logic.DaoSqlConverter;

public class DaoSqlMain {

    private JTextArea txtInput;
    private JTextArea txtOutput;

    public JPanel initialize(Font f) {
        JPanel jp = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setFont(f);
                g.setColor(Color.BLUE);
                g.drawString("輸入 (舊 DAO SQL — StringBuffer 風格):", 10, 25);
                g.setColor(Color.RED);
                g.drawString("輸出 (SQLQueryBuilder 風格):", 10, 435);
            }
        };
        jp.setLayout(null);

        Font mono = new Font("Monospaced", Font.PLAIN, 12);

        txtInput = new JTextArea();
        txtInput.setFont(mono);
        txtInput.setLineWrap(false);
        JScrollPane spInput = new JScrollPane(txtInput);
        spInput.setBounds(10, 35, 760, 375);
        jp.add(spInput);

        JButton btnConvert = new JButton("轉換 →");
        btnConvert.setBounds(290, 420, 200, 35);
        btnConvert.setFont(f);
        btnConvert.addActionListener(new ConvertListener());
        jp.add(btnConvert);

        JButton btnClear = new JButton("清除");
        btnClear.setBounds(510, 420, 100, 35);
        btnClear.setFont(f);
        btnClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtInput.setText("");
                txtOutput.setText("");
            }
        });
        jp.add(btnClear);

        txtOutput = new JTextArea();
        txtOutput.setFont(mono);
        txtOutput.setLineWrap(false);
        JScrollPane spOutput = new JScrollPane(txtOutput);
        spOutput.setBounds(10, 465, 760, 305);
        jp.add(spOutput);

        return jp;
    }

    class ConvertListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            try {
                DaoSqlConverter converter = new DaoSqlConverter();
                String result = converter.convert(DaoSqlMain.this.txtInput.getText());
                DaoSqlMain.this.txtOutput.setText(result);
            } catch (Exception ex) {
                DaoSqlMain.this.txtOutput.setText("ERROR: " + ex.getMessage());
            }
        }
    }
}
