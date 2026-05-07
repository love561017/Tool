package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import tool.logic.BeanFieldParser;
import tool.logic.DaoSqlConverter;

public class DaoSqlMain {

    private static final Preferences PREFS = Preferences.userNodeForPackage(DaoSqlMain.class);
    private static final String PREF_BEAN_PATH = "daoSqlBeanPath";

    private JTextArea txtInput;
    private JTextArea txtOutput;
    private JCheckBox chkCamel;
    private JTextField txtBeanPath;
    private JTextField txtBeanName;

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
        spInput.setBounds(10, 35, 760, 315);
        jp.add(spInput);

        // Bean 路徑 + Bean 名稱 row
        JLabel lblBeanPath = new JLabel("Bean路徑:");
        lblBeanPath.setBounds(10, 360, 65, 22);
        lblBeanPath.setFont(f);
        jp.add(lblBeanPath);

        txtBeanPath = new JTextField();
        txtBeanPath.setBounds(78, 357, 420, 26);
        txtBeanPath.setFont(mono);
        txtBeanPath.setText(PREFS.get(PREF_BEAN_PATH, ""));
        jp.add(txtBeanPath);

        JLabel lblBeanName = new JLabel("Bean名稱:");
        lblBeanName.setBounds(505, 360, 65, 22);
        lblBeanName.setFont(f);
        jp.add(lblBeanName);

        txtBeanName = new JTextField();
        txtBeanName.setBounds(577, 357, 193, 26);
        txtBeanName.setFont(mono);
        jp.add(txtBeanName);

        chkCamel = new JCheckBox("別名轉駝峰", true);
        chkCamel.setBounds(10, 392, 130, 30);
        chkCamel.setFont(f);
        jp.add(chkCamel);

        JButton btnConvert = new JButton("轉換 →");
        btnConvert.setBounds(150, 390, 200, 35);
        btnConvert.setFont(f);
        btnConvert.addActionListener(new ConvertListener());
        jp.add(btnConvert);

        JButton btnClear = new JButton("清除");
        btnClear.setBounds(360, 390, 100, 35);
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
        spOutput.setBounds(10, 444, 760, 300);
        jp.add(spOutput);

        return jp;
    }

    class ConvertListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            try {
                String beanPath = DaoSqlMain.this.txtBeanPath.getText().trim();
                String beanName = DaoSqlMain.this.txtBeanName.getText().trim();

                Map<String, String> beanFields = Collections.emptyMap();
                String beanWarning = null;
                if (!beanPath.isEmpty() && !beanName.isEmpty()) {
                    PREFS.put(PREF_BEAN_PATH, beanPath);
                    beanFields = BeanFieldParser.parseFields(beanPath, beanName);
                    if (beanFields.isEmpty()) {
                        beanWarning = "// [警告] 未找到 bean: " + beanName + ".java 或無 private 欄位\n";
                    } else {
                        StringBuilder sb = new StringBuilder("// [Bean: ").append(beanName)
                                .append("] 讀取 ").append(beanFields.size()).append(" 個欄位: ");
                        int n = 0;
                        for (Map.Entry<String, String> entry : beanFields.entrySet()) {
                            if (n++ > 0) sb.append(", ");
                            sb.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
                            if (n >= 8) { sb.append(", ..."); break; }
                        }
                        beanWarning = sb.append("\n").toString();
                    }
                }

                DaoSqlConverter converter = new DaoSqlConverter();
                boolean camel = DaoSqlMain.this.chkCamel.isSelected();
                String result = converter.setBeanFieldTypes(beanFields)
                                         .convert(DaoSqlMain.this.txtInput.getText(), camel);
                if (beanWarning != null) result = beanWarning + result;
                DaoSqlMain.this.txtOutput.setText(result);
            } catch (Exception ex) {
                DaoSqlMain.this.txtOutput.setText("ERROR: " + ex.getMessage());
            }
        }
    }
}
